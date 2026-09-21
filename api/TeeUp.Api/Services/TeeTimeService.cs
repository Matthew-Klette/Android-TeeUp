using TeeUp.Api.Dtos;
using TeeUp.Api.Models;
using TeeUp.Api.Repositories;
using TeeUp.Api.Common;

namespace TeeUp.Api.Services;

public class TeeTimeService(
    ITeeTimeRepository teeTimeRepository,
    IUserRepository userRepository,
    ICourseRepository courseRepository,
    IJoinRequestRepository joinRequestRepository) : ITeeTimeService
{
    public async Task<IReadOnlyList<TeeTimeDto>> GetAllAsync(decimal? maxHandicap = null, PaceOfPlay? pace = null)
    {
        if (maxHandicap is decimal handicapFilter &&
            (handicapFilter < 0 || handicapFilter > 54 || decimal.Round(handicapFilter, 1) != handicapFilter))
            throw new DomainValidationException("Handicap must be between 0 and 54, with at most one decimal place.");
        if (pace is { } paceFilter && !Enum.IsDefined(paceFilter))
            throw new DomainValidationException("Select a valid pace of play.");
        var teeTimes = await teeTimeRepository.GetAllAsync();

        var filtered = maxHandicap is null && pace is null
            ? teeTimes
            : await FilterByHostSkillAsync(teeTimes, maxHandicap, pace);

        var usersById = (await userRepository.GetAllAsync()).ToDictionary(u => u.Id);
        var joinRequests = await joinRequestRepository.GetAllAsync();
        var acceptedByTeeTime = joinRequests
            .Where(j => j.Status == JoinRequestStatus.Accepted)
            .ToLookup(j => j.TeeTimeId);

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
            ?? throw new NotFoundException($"Course {request.CourseId} not found.");

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

        var members = new List<GroupMemberDto>
        {
            new(host.Id, host.DisplayName, host.HandicapIndex, host.PaceOfPlay, IsHost: true)
        };

        return TeeTimeDto.From(teeTime, members);
    }
}
