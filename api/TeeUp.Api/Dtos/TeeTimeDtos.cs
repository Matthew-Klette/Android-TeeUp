using TeeUp.Api.Models;

namespace TeeUp.Api.Dtos;

/// <summary>A golfer starting a solo round on their own, with no join-request flow — see RoundsActivity's "Start a Round".</summary>
public record CreateTeeTimeRequest(Guid CourseId);

/// <summary>
/// Creates a real group looking for players (EME-311). <paramref name="OpenSpots"/> is guests
/// wanted, not counting the host — matches how OpenSpots already works for solo rounds (0 = just
/// the host) and how JoinRequestService's accept-limit check already reads it.
/// </summary>
public record CreateGroupRequest(
    Guid CourseId,
    DateTime DateTime,
    int Holes,
    int OpenSpots,
    decimal? WantedHandicapMin,
    decimal? WantedHandicapMax,
    PaceOfPlay? WantedPace);

public record GroupMemberDto(Guid UserId, string DisplayName, decimal? HandicapIndex, PaceOfPlay PaceOfPlay, bool IsHost);

public record TeeTimeDto(
    Guid Id,
    Guid? HostUserId,
    Guid CourseId,
    DateTime DateTime,
    int OpenSpots,
    decimal Price,
    TeeTimeType Type,
    int? Holes,
    decimal? WantedHandicapMin,
    decimal? WantedHandicapMax,
    PaceOfPlay? WantedPace,
    TeeTimeStatus Status,
    IReadOnlyList<GroupMemberDto> Members)
{
    /// <summary>No member list available (e.g. right after creating a solo round) — Members comes back empty.</summary>
    public static TeeTimeDto From(TeeTime teeTime) => From(teeTime, []);

    public static TeeTimeDto From(TeeTime teeTime, IReadOnlyList<GroupMemberDto> members) => new(
        teeTime.Id, teeTime.HostUserId, teeTime.CourseId, teeTime.DateTime,
        teeTime.OpenSpots, teeTime.Price, teeTime.Type,
        teeTime.Holes, teeTime.WantedHandicapMin, teeTime.WantedHandicapMax, teeTime.WantedPace,
        teeTime.Status, members);
}
