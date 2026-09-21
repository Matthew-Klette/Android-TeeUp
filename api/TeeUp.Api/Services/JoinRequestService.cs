using TeeUp.Api.Common;
using TeeUp.Api.Dtos;
using TeeUp.Api.Models;
using TeeUp.Api.Repositories;

namespace TeeUp.Api.Services;

/// <summary>
/// Enforces the join-request rules (EME-299/EME-313): a guest can't request to join their own
/// tee time or double-request one they already have a pending/accepted request against; a
/// request can only leave "Pending" once, only by the tee time's host, and only into a tee time
/// that's still open, uncancelled and in the future; and a host cannot accept more guests than a
/// tee time has open spots. All three mutations (create, accept, decline) for a given tee time
/// are serialized through <see cref="TeeTimeJoinLock"/> and re-read their state with
/// <see cref="IRepository{T}.GetByIdFreshAsync"/> once inside it, so a caller can't act on a
/// stale pre-lock read of state a concurrent caller already changed.
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
        // from the same guest must not both see "no existing request" before either commits
        // (EME-313 review). This also means a request can't be created while an accept/decline
        // for the same tee time is mid-flight, since that shares the same per-tee-time lock.
        using var _ = await TeeTimeJoinLock.AcquireAsync(teeTimeId);

        var existingRequests = await joinRequestRepository.GetByTeeTimeIdAsync(teeTimeId);
        // A Declined request doesn't block re-requesting — matches Android's canRequestToJoin.
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
        // Only needed to learn which tee time this request belongs to, so the right lock can
        // be taken below — every decision is made from a fresh re-read once inside it, so a
        // stale value here can't affect the outcome. Must stay untracked (GetByIdFreshAsync,
        // not GetByIdAsync): EF would otherwise track this instance, and the later
        // GetByIdFreshAsync + UpdateAsync on a second, detached instance with the same key
        // fails ("another instance with the same key is already being tracked").
        var lookup = await joinRequestRepository.GetByIdFreshAsync(joinRequestId)
            ?? throw new NotFoundException($"Join request {joinRequestId} not found.");

        // Held for the whole read-check-write below: accept/decline calls racing on the same
        // request, or two accepts racing for the last open spot, must not both pass their
        // checks before either commits (EME-313 review) — see TeeTimeJoinLock's doc comment.
        using var _ = await TeeTimeJoinLock.AcquireAsync(lookup.TeeTimeId);

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
            // Explicitly reject anything but Open, not just Cancelled — a tee time can be Full
            // without every join request against it being Accepted yet (e.g. another request was
            // withdrawn after it filled), and the accepted-count check below alone wouldn't catch
            // that (EME-313 review).
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

            // This acceptance is the last open spot — flip Open -> Full so clients stop
            // advertising/allowing further join requests against this tee time.
            if (acceptedCount + 1 >= teeTime.OpenSpots)
            {
                teeTime.Status = TeeTimeStatus.Full;
                await teeTimeRepository.UpdateAsync(teeTime);
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
}
