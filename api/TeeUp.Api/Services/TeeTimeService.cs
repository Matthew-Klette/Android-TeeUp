using TeeUp.Api.Dtos;
using TeeUp.Api.Models;
using TeeUp.Api.Repositories;
using TeeUp.Api.Common;

namespace TeeUp.Api.Services;

public class TeeTimeService(
    ITeeTimeRepository teeTimeRepository,
    IUserRepository userRepository,
    ICourseRepository courseRepository,
    IJoinRequestRepository joinRequestRepository,
    INotificationRepository notificationRepository,
    IRoundRepository roundRepository) : ITeeTimeService
{
    public async Task<IReadOnlyList<TeeTimeDto>> GetAllAsync(decimal? maxHandicap = null, PaceOfPlay? pace = null, bool joinableOnly = false)
    {
        if (maxHandicap is decimal handicapFilter &&
            (handicapFilter < 0 || handicapFilter > 54 || decimal.Round(handicapFilter, 1) != handicapFilter))
            throw new DomainValidationException("Handicap must be between 0 and 54, with at most one decimal place.");
        if (pace is { } paceFilter && !Enum.IsDefined(paceFilter))
            throw new DomainValidationException("Select a valid pace of play.");
        var teeTimes = await teeTimeRepository.GetAllAsync();
        var joinRequests = await joinRequestRepository.GetAllAsync();
        var acceptedByTeeTime = joinRequests
            .Where(j => j.Status == JoinRequestStatus.Accepted)
            .ToLookup(j => j.TeeTimeId);

        // Status alone isn't trusted here: a row that existed before Status was added
        // (or before it was consistently kept in sync) can still read Open while its
        // guest capacity is already filled, so joinableOnly also recomputes remaining
        // spots directly from accepted join requests rather than relying on the flag.
        IReadOnlyList<TeeTime> joinable = joinableOnly
            ? teeTimes
                .Where(t => t.Type == TeeTimeType.OpenRound
                    && t.Status == TeeTimeStatus.Open
                    && t.DateTime > DateTime.UtcNow
                    && acceptedByTeeTime[t.Id].Count() < t.OpenSpots)
                .ToList()
            : teeTimes;

        var filtered = maxHandicap is null && pace is null
            ? joinable
            : await FilterByHostSkillAsync(joinable, maxHandicap, pace);

        var usersById = (await userRepository.GetAllAsync()).ToDictionary(u => u.Id);

        return filtered
            .Select(t => TeeTimeDto.From(t, BuildMemberList(t, usersById, acceptedByTeeTime[t.Id])))
            .ToList();
    }

    private async Task<IEnumerable<TeeTime>> FilterByHostSkillAsync(
        IReadOnlyList<TeeTime> teeTimes, decimal? maxHandicap, PaceOfPlay? pace)
    {
        var usersById = (await userRepository.GetAllAsync()).ToDictionary(u => u.Id);

        return teeTimes.Where(t => t.HostUserId is Guid hostId
            && usersById.TryGetValue(hostId, out var host)
            && (maxHandicap is null || (host.HandicapIndex is decimal handicap && handicap <= maxHandicap))
            && (pace is null || host.PaceOfPlay == pace));
    }

    private static IReadOnlyList<GroupMemberDto> BuildMemberList(
        TeeTime teeTime, IReadOnlyDictionary<Guid, User> usersById, IEnumerable<JoinRequest> acceptedGuests)
    {
        var members = new List<GroupMemberDto>();

        if (teeTime.HostUserId is Guid hostId && usersById.TryGetValue(hostId, out var host))
        {
            members.Add(new GroupMemberDto(host.Id, host.DisplayName, host.HandicapIndex, host.PaceOfPlay, IsHost: true));
        }

        foreach (var guestRequest in acceptedGuests)
        {
            if (usersById.TryGetValue(guestRequest.GuestUserId, out var guest))
            {
                members.Add(new GroupMemberDto(guest.Id, guest.DisplayName, guest.HandicapIndex, guest.PaceOfPlay, IsHost: false));
            }
        }

        return members;
    }

    public async Task<TeeTimeDto> CreateSoloAsync(Guid hostUserId, Guid courseId, int? holes = null)
    {
        if (holes is not null && holes != 9 && holes != 18)
            throw new DomainValidationException("Holes must be 9 or 18.");

        _ = await courseRepository.GetByIdAsync(courseId)
            ?? throw new NotFoundException($"Course {courseId} not found.");

        var teeTime = new TeeTime
        {
            Id = Guid.NewGuid(),
            HostUserId = hostUserId,
            CourseId = courseId,
            DateTime = DateTime.UtcNow,
            OpenSpots = 0,
            Price = 0,
            Type = TeeTimeType.Booking,
            Holes = holes ?? 18
        };

        await teeTimeRepository.AddAsync(teeTime);
        return TeeTimeDto.From(teeTime);
    }

    public async Task<TeeTimeDto> CreateGroupAsync(Guid hostUserId, CreateGroupRequest request)
    {
        _ = await courseRepository.GetByIdAsync(request.CourseId)
            ?? throw new DomainValidationException($"Course {request.CourseId} does not exist.");

        if (request.DateTime <= DateTime.UtcNow)
            throw new DomainValidationException("The tee time must be in the future.");
        if (request.Holes != 9 && request.Holes != 18)
            throw new DomainValidationException("Holes must be 9 or 18.");
        if (request.OpenSpots < 1)
            throw new DomainValidationException("A group needs at least one open spot for a guest.");
        if (request.WantedHandicapMin is decimal min && request.WantedHandicapMax is decimal max && min > max)
            throw new DomainValidationException("The minimum wanted handicap cannot be above the maximum.");
        if (request.WantedPace is { } wantedPace && !Enum.IsDefined(wantedPace))
            throw new DomainValidationException("Select a valid pace of play.");

        var host = await userRepository.GetByIdAsync(hostUserId)
            ?? throw new NotFoundException($"User {hostUserId} not found.");

        var teeTime = new TeeTime
        {
            Id = Guid.NewGuid(),
            HostUserId = hostUserId,
            CourseId = request.CourseId,
            DateTime = request.DateTime,
            OpenSpots = request.OpenSpots,
            Price = 0,
            Type = TeeTimeType.OpenRound,
            Holes = request.Holes,
            WantedHandicapMin = request.WantedHandicapMin,
            WantedHandicapMax = request.WantedHandicapMax,
            WantedPace = request.WantedPace,
            Status = TeeTimeStatus.Open
        };

        // Held across the auto-decline loop below so it can't race a concurrent JoinRequestService call (see AutoDeclineLock).
        using var __ = await AutoDeclineLock.AcquireAsync(joinRequestRepository, teeTime.Id, hostUserId);

        await teeTimeRepository.AddAsync(teeTime);

        // EME-323: hosting your own group makes any pending guest request elsewhere no longer relevant, so auto-decline it instead of leaving it pending.
        var ownPendingAsGuest = (await joinRequestRepository.GetAllAsync())
            .Where(j => j.GuestUserId == hostUserId && j.Status == JoinRequestStatus.Pending);
        foreach (var pending in ownPendingAsGuest)
        {
            pending.Status = JoinRequestStatus.Declined;
            await joinRequestRepository.UpdateAsync(pending);
        }

        var members = new List<GroupMemberDto>
        {
            new(host.Id, host.DisplayName, host.HandicapIndex, host.PaceOfPlay, IsHost: true)
        };

        return TeeTimeDto.From(teeTime, members);
    }

    public async Task<TeeTimeDto> EditAsync(Guid teeTimeId, Guid hostUserId, UpdateGroupRequest request)
    {
        if (request.DateTime <= DateTime.UtcNow)
            throw new DomainValidationException("The tee time must be in the future.");
        if (request.Holes != 9 && request.Holes != 18)
            throw new DomainValidationException("Holes must be 9 or 18.");
        if (request.OpenSpots < 1)
            throw new DomainValidationException("A group needs at least one open spot for a guest.");
        if (request.WantedHandicapMin is decimal min && request.WantedHandicapMax is decimal max && min > max)
            throw new DomainValidationException("The minimum wanted handicap cannot be above the maximum.");
        if (request.WantedPace is { } wantedPace && !Enum.IsDefined(wantedPace))
            throw new DomainValidationException("Select a valid pace of play.");

        // Serialized against a concurrent accept/decline/withdraw on the same tee time (EME-321, reusing EME-313's per-tee-time lock) so an edit can't act on a stale open-spots count.
        // Also covers a race against an auto-decline (EME-323), since AutoDeclineLock takes this same TeeTimeJoinLock.
        using var _ = await TeeTimeJoinLock.AcquireAsync(teeTimeId);

        var teeTime = await teeTimeRepository.GetByIdFreshAsync(teeTimeId)
            ?? throw new NotFoundException($"Tee time {teeTimeId} not found.");

        if (teeTime.HostUserId != hostUserId)
            throw new ForbiddenException("Only the host can edit this group.");
        // Not reachable from the app's current UI (only group listings expose Edit), but the
        // endpoint itself must not silently write guest-capacity/handicap/pace fields onto a
        // solo booking just because its creator is also its own host, same as any group's host.
        if (teeTime.Type != TeeTimeType.OpenRound)
            throw new DomainValidationException("Only a group looking for players can be edited this way.");
        if (teeTime.Status == TeeTimeStatus.Cancelled)
            throw new DomainValidationException("A cancelled group cannot be edited.");
        // A round tracks its hole count from this tee time's live Holes value (see RoundService.PostScorecardAsync), so changing holes or schedule after scoring started could invalidate posted scores. Cancel instead.
        if (await roundRepository.GetByTeeTimeIdAsync(teeTimeId) is not null)
            throw new DomainValidationException(
                "This group already has a round in progress and cannot be edited; cancel it instead.");

        var siblings = await joinRequestRepository.GetByTeeTimeIdAsync(teeTimeId);
        var accepted = siblings.Where(j => j.Status == JoinRequestStatus.Accepted).ToList();
        if (request.OpenSpots < accepted.Count)
        {
            throw new DomainValidationException(
                $"This group already has {accepted.Count} accepted guest(s); open spots cannot be reduced below that.");
        }

        teeTime.DateTime = request.DateTime;
        teeTime.Holes = request.Holes;
        teeTime.OpenSpots = request.OpenSpots;
        teeTime.WantedHandicapMin = request.WantedHandicapMin;
        teeTime.WantedHandicapMax = request.WantedHandicapMax;
        teeTime.WantedPace = request.WantedPace;
        // A prior accept may have flipped this to Full; if the edit reopened capacity, reflect it.
        if (teeTime.Status == TeeTimeStatus.Full && accepted.Count < request.OpenSpots)
            teeTime.Status = TeeTimeStatus.Open;

        await teeTimeRepository.UpdateAsync(teeTime);

        var usersById = (await userRepository.GetAllAsync()).ToDictionary(u => u.Id);
        var members = BuildMemberList(teeTime, usersById, accepted);
        return TeeTimeDto.From(teeTime, members);
    }

    public async Task<TeeTimeDto> CancelAsync(Guid teeTimeId, Guid hostUserId)
    {
        // Same TeeTimeJoinLock EditAsync takes, which also serializes this against a same-tee-time auto-decline (EME-323).
        using var _ = await TeeTimeJoinLock.AcquireAsync(teeTimeId);

        var teeTime = await teeTimeRepository.GetByIdFreshAsync(teeTimeId)
            ?? throw new NotFoundException($"Tee time {teeTimeId} not found.");

        if (teeTime.HostUserId != hostUserId)
            throw new ForbiddenException("Only the host can cancel this group.");
        if (teeTime.Status == TeeTimeStatus.Cancelled)
            throw new DomainValidationException($"Tee time {teeTimeId} is already cancelled.");

        teeTime.Status = TeeTimeStatus.Cancelled;
        await teeTimeRepository.UpdateAsync(teeTime);

        var siblings = await joinRequestRepository.GetByTeeTimeIdAsync(teeTimeId);
        var affected = siblings.Where(j => j.Status is JoinRequestStatus.Pending or JoinRequestStatus.Accepted).ToList();
        foreach (var request in affected)
        {
            await notificationRepository.AddAsync(new Notification
            {
                Id = Guid.NewGuid(),
                UserId = request.GuestUserId,
                Type = NotificationType.TeeTimeCancelled,
                Message = "A tee time group you were part of was cancelled by the host.",
                RelatedEntityId = teeTime.Id
            });
        }

        var usersById = (await userRepository.GetAllAsync()).ToDictionary(u => u.Id);
        var acceptedMembers = siblings.Where(j => j.Status == JoinRequestStatus.Accepted).ToList();
        var members = BuildMemberList(teeTime, usersById, acceptedMembers);
        return TeeTimeDto.From(teeTime, members);
    }

    public async Task DeleteAsync(Guid teeTimeId, Guid hostUserId)
    {
        using var _ = await TeeTimeJoinLock.AcquireAsync(teeTimeId);

        var teeTime = await teeTimeRepository.GetByIdFreshAsync(teeTimeId)
            ?? throw new NotFoundException($"Tee time {teeTimeId} not found.");

        if (teeTime.HostUserId != hostUserId)
            throw new ForbiddenException("Only the host can delete this group.");

        // The host can delete their group unconditionally. Join requests, the round and its
        // scorecard entries all cascade-delete with the tee time itself (see TeeUpDbContext).
        var siblings = await joinRequestRepository.GetByTeeTimeIdAsync(teeTimeId);
        var affected = siblings.Where(j => j.Status is JoinRequestStatus.Pending or JoinRequestStatus.Accepted).ToList();

        await teeTimeRepository.DeleteAsync(teeTimeId);

        // Notify only after the delete actually succeeds, so a failure here can't leave a
        // guest believing the group is gone when it isn't.
        foreach (var guest in affected)
        {
            await notificationRepository.AddAsync(new Notification
            {
                Id = Guid.NewGuid(),
                UserId = guest.GuestUserId,
                Type = NotificationType.TeeTimeCancelled,
                Message = "A tee time group you were part of was deleted by the host.",
                RelatedEntityId = teeTime.Id
            });
        }
    }

    public async Task<IReadOnlyList<TeeTimeDto>> GetMineAsync(Guid userId)
    {
        var teeTimes = await teeTimeRepository.GetAllAsync();
        var joinRequests = await joinRequestRepository.GetAllAsync();
        var acceptedByTeeTime = joinRequests
            .Where(j => j.Status == JoinRequestStatus.Accepted)
            .ToLookup(j => j.TeeTimeId);
        var myAcceptedTeeTimeIds = joinRequests
            .Where(j => j.GuestUserId == userId && j.Status == JoinRequestStatus.Accepted)
            .Select(j => j.TeeTimeId)
            .ToHashSet();

        var mine = teeTimes.Where(t => t.HostUserId == userId || myAcceptedTeeTimeIds.Contains(t.Id));

        var usersById = (await userRepository.GetAllAsync()).ToDictionary(u => u.Id);
        return mine
            .Select(t => TeeTimeDto.From(t, BuildMemberList(t, usersById, acceptedByTeeTime[t.Id])))
            .ToList();
    }
}
