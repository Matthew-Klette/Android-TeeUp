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
    INotificationRepository notificationRepository) : ITeeTimeService
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

    public async Task<TeeTimeDto> CreateSoloAsync(Guid hostUserId, Guid courseId)
    {
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
            Type = TeeTimeType.Booking
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

        await teeTimeRepository.AddAsync(teeTime);

        // EME-323: hosting your own group makes any pending request you're holding as a guest
        // elsewhere no longer relevant — auto-decline them (status only, preserving history,
        // same as a host's own decline) rather than leaving them pending indefinitely. No
        // separate notification: this is a side effect of the host's own action, not something
        // another party did to them.
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

        // Serialized against a concurrent accept/decline/withdraw on the same tee time (EME-321,
        // reusing EME-313's per-tee-time lock) so an edit can't act on a stale open-spots count.
        using var _ = await TeeTimeJoinLock.AcquireAsync(teeTimeId);

        var teeTime = await teeTimeRepository.GetByIdFreshAsync(teeTimeId)
            ?? throw new NotFoundException($"Tee time {teeTimeId} not found.");

        if (teeTime.HostUserId != hostUserId)
            throw new ForbiddenException("Only the host can edit this group.");
        if (teeTime.Status == TeeTimeStatus.Cancelled)
            throw new DomainValidationException("A cancelled group cannot be edited.");

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

        var siblings = await joinRequestRepository.GetByTeeTimeIdAsync(teeTimeId);
        if (siblings.Count > 0)
        {
            throw new DomainValidationException(
                "This group has join requests against it and cannot be deleted; cancel it instead.");
        }

        await teeTimeRepository.DeleteAsync(teeTimeId);
    }
}
