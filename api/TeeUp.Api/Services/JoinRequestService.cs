using TeeUp.Api.Common;
using TeeUp.Api.Dtos;
using TeeUp.Api.Models;
using TeeUp.Api.Repositories;

namespace TeeUp.Api.Services;

/// <summary>
/// Enforces the join-request rules: a guest can't request to join their own tee time or
/// double-request one they already have a request against. A request can only leave
/// "Pending" once, only by the host, and only into a tee time that's still open. A host
/// can't accept more guests than there are open spots. Create, accept and decline for a
/// given tee time are all serialized through <see cref="TeeTimeJoinLock"/> and re-read
/// their state with <see cref="IRepository{T}.GetByIdFreshAsync"/> once inside it. An accept
/// also auto-declines that guest's other pending requests elsewhere (EME-323), so it locks
/// every tee time that touches via <see cref="AutoDeclineLock"/> instead of just its own.
/// </summary>
public class JoinRequestService(
    IJoinRequestRepository joinRequestRepository,
    ITeeTimeRepository teeTimeRepository,
    INotificationRepository notificationRepository) : IJoinRequestService
{
    public async Task<JoinRequestDto> CreateAsync(Guid teeTimeId, Guid guestUserId)
    {
        var teeTime = await teeTimeRepository.GetByIdAsync(teeTimeId)
            ?? throw new NotFoundException($"Tee time {teeTimeId} not found.");

        if (teeTime.HostUserId == guestUserId)
        {
            throw new DomainValidationException("You can't request to join your own tee time.");
        }

        if (teeTime.Status == TeeTimeStatus.Cancelled || teeTime.DateTime <= DateTime.UtcNow)
        {
            throw new DomainValidationException("This tee time is no longer accepting join requests.");
        }

        // Held across the duplicate-check-then-insert below: two near-simultaneous requests
        // from the same guest must not both see "no existing request" before either commits.
        // Also blocks a create from running while an accept/decline for the same tee time
        // is mid-flight, since they share the same per-tee-time lock.
        using var _ = await TeeTimeJoinLock.AcquireAsync(teeTimeId);

        var existingRequests = await joinRequestRepository.GetByTeeTimeIdAsync(teeTimeId);
        // A Declined request doesn't block re-requesting. Matches Android's canRequestToJoin.
        if (existingRequests.Any(j => j.GuestUserId == guestUserId && j.Status != JoinRequestStatus.Declined))
        {
            throw new ConflictException("You've already requested to join this tee time.");
        }

        var joinRequest = new JoinRequest
        {
            Id = Guid.NewGuid(),
            TeeTimeId = teeTime.Id,
            GuestUserId = guestUserId,
            Status = JoinRequestStatus.Pending
        };

        await joinRequestRepository.AddAsync(joinRequest);
        return JoinRequestDto.From(joinRequest);
    }

    public async Task<JoinRequestDto> UpdateStatusAsync(Guid joinRequestId, JoinRequestStatus status, Guid callerId)
    {
        // Only needed to find which tee time this request belongs to, so the right lock
        // can be taken below. Every decision is made from a fresh re-read once inside it,
        // so a stale value here can't affect the outcome. Must use GetByIdFreshAsync here,
        // not GetByIdAsync, or EF tracks this instance and the later GetByIdFreshAsync +
        // UpdateAsync on a second instance with the same key fails.
        var lookup = await joinRequestRepository.GetByIdFreshAsync(joinRequestId)
            ?? throw new NotFoundException($"Join request {joinRequestId} not found.");

        // Held for the whole read-check-write below. Accept/decline calls racing on the
        // same request, or two accepts racing for the last open spot, must not both pass
        // their checks before either commits. See TeeTimeJoinLock's doc comment. An accept
        // may also auto-decline this guest's pending requests at other tee times (EME-323),
        // so it locks all of those too, via AutoDeclineLock — a decline only ever touches
        // this one tee time, so it just takes this tee time's own TeeTimeJoinLock.
        using var _ = status == JoinRequestStatus.Accepted
            ? await AutoDeclineLock.AcquireAsync(joinRequestRepository, lookup.TeeTimeId, lookup.GuestUserId)
            : await TeeTimeJoinLock.AcquireAsync(lookup.TeeTimeId);

        var joinRequest = await joinRequestRepository.GetByIdFreshAsync(joinRequestId)
            ?? throw new NotFoundException($"Join request {joinRequestId} not found.");
        var teeTime = await teeTimeRepository.GetByIdFreshAsync(joinRequest.TeeTimeId)
            ?? throw new NotFoundException($"Tee time {joinRequest.TeeTimeId} not found.");

        if (teeTime.HostUserId != callerId)
        {
            throw new ForbiddenException("Only the host can accept or decline join requests for this tee time.");
        }

        if (joinRequest.Status != JoinRequestStatus.Pending)
        {
            throw new DomainValidationException(
                $"Join request {joinRequestId} is already {joinRequest.Status} and cannot be changed.");
        }

        if (status == JoinRequestStatus.Accepted)
        {
            // Explicitly reject anything but Open, not just Cancelled. A tee time can be
            // Full without every join request against it being Accepted yet, and the
            // accepted-count check below alone wouldn't catch that.
            if (teeTime.Status != TeeTimeStatus.Open || teeTime.DateTime <= DateTime.UtcNow)
            {
                throw new DomainValidationException("This tee time can no longer accept new players.");
            }

            var siblings = await joinRequestRepository.GetByTeeTimeIdAsync(teeTime.Id);
            var acceptedCount = siblings.Count(j => j.Status == JoinRequestStatus.Accepted);

            if (acceptedCount >= teeTime.OpenSpots)
            {
                throw new DomainValidationException(
                    $"Tee time {teeTime.Id} already has its {teeTime.OpenSpots} open spot(s) filled.");
            }

            joinRequest.Status = status;
            await joinRequestRepository.UpdateAsync(joinRequest);

            // This acceptance is the last open spot, so flip Open to Full so clients
            // stop advertising or allowing further join requests here.
            if (acceptedCount + 1 >= teeTime.OpenSpots)
            {
                teeTime.Status = TeeTimeStatus.Full;
                await teeTimeRepository.UpdateAsync(teeTime);
            }

            // EME-323: once this guest is placed in a group, any other pending requests they're
            // holding elsewhere no longer make sense — auto-decline them (status only, preserving
            // history, matching how a host's own decline already works) rather than leaving them
            // pending indefinitely. No separate notification: this is a side effect of the
            // acceptance above, not a decision the other hosts made. AutoDeclineLock above already
            // holds each of these other tee times' TeeTimeJoinLock too, so this is safe.
            var guestOtherPending = (await joinRequestRepository.GetAllAsync())
                .Where(j => j.GuestUserId == joinRequest.GuestUserId
                    && j.Status == JoinRequestStatus.Pending
                    && j.TeeTimeId != teeTime.Id);
            foreach (var other in guestOtherPending)
            {
                other.Status = JoinRequestStatus.Declined;
                await joinRequestRepository.UpdateAsync(other);
            }
        }
        else
        {
            joinRequest.Status = status;
            await joinRequestRepository.UpdateAsync(joinRequest);
        }

        await notificationRepository.AddAsync(new Notification
        {
            Id = Guid.NewGuid(),
            UserId = joinRequest.GuestUserId,
            Type = status == JoinRequestStatus.Accepted
                ? NotificationType.RequestAccepted
                : NotificationType.RequestDeclined,
            Message = status == JoinRequestStatus.Accepted
                ? "Your request to join a tee time was accepted."
                : "Your request to join a tee time was declined.",
            RelatedEntityId = joinRequest.TeeTimeId
        });

        return JoinRequestDto.From(joinRequest);
    }

    public async Task<IReadOnlyList<JoinRequestDto>> GetForTeeTimeAsync(Guid teeTimeId)
    {
        var joinRequests = await joinRequestRepository.GetByTeeTimeIdAsync(teeTimeId);
        return joinRequests.Select(JoinRequestDto.From).ToList();
    }

    public async Task WithdrawAsync(Guid joinRequestId, Guid guestUserId)
    {
        var lookup = await joinRequestRepository.GetByIdFreshAsync(joinRequestId)
            ?? throw new NotFoundException($"Join request {joinRequestId} not found.");

        // Held for the whole read-check-delete below, same rationale as UpdateStatusAsync: a
        // withdraw racing a host's concurrent accept/decline on the same request must not act on
        // a stale "still Pending" read. Also covers a race against an accept elsewhere that's
        // mid-way through auto-declining this same request, since AutoDeclineLock takes this
        // same tee time's TeeTimeJoinLock as part of its set.
        using var _ = await TeeTimeJoinLock.AcquireAsync(lookup.TeeTimeId);

        var joinRequest = await joinRequestRepository.GetByIdFreshAsync(joinRequestId)
            ?? throw new NotFoundException($"Join request {joinRequestId} not found.");

        if (joinRequest.GuestUserId != guestUserId)
        {
            throw new ForbiddenException("Only the requesting guest can withdraw this join request.");
        }

        if (joinRequest.Status != JoinRequestStatus.Pending)
        {
            throw new DomainValidationException(
                $"Join request {joinRequestId} is already {joinRequest.Status} and cannot be withdrawn.");
        }

        await joinRequestRepository.DeleteAsync(joinRequestId);
    }
}
