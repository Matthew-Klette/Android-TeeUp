using TeeUp.Api.Models;

namespace TeeUp.Api.Dtos;

/// <summary>
/// A golfer starting a solo round on their own, with no join-request flow. <paramref name="Holes"/> null defaults to 18, matching ScorecardActivity's own fallback.
/// Persisting it here, rather than only living as a per-screen local variable, is what lets RoundsActivity tell a finished 9-hole round apart from one still in progress.
/// </summary>
public record CreateTeeTimeRequest(Guid CourseId, int? Holes = null);

/// <summary>
/// Creates a real group looking for players. <paramref name="OpenSpots"/> is guests
/// wanted, not counting the host, matching how OpenSpots already works for solo rounds.
/// </summary>
public record CreateGroupRequest(
    Guid CourseId,
    DateTime DateTime,
    int Holes,
    int OpenSpots,
    decimal? WantedHandicapMin,
    decimal? WantedHandicapMax,
    PaceOfPlay? WantedPace);

/// <summary>
/// Edits an existing group's details (EME-321). Course isn't editable, only date/time, holes, open spots and the wanted handicap/pace range, mirroring what CreateGroupRequest validates.
/// </summary>
public record UpdateGroupRequest(
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
    int SpotsRemaining,
    decimal Price,
    TeeTimeType Type,
    int? Holes,
    decimal? WantedHandicapMin,
    decimal? WantedHandicapMax,
    PaceOfPlay? WantedPace,
    TeeTimeStatus Status,
    IReadOnlyList<GroupMemberDto> Members)
{
    /// <summary>No member list available yet, so Members comes back empty.</summary>
    public static TeeTimeDto From(TeeTime teeTime) => From(teeTime, []);

    /// <summary>
    /// <paramref name="teeTime"/>.OpenSpots is the fixed guest capacity set at creation and never
    /// changes; SpotsRemaining is capacity minus accepted guests, so clients (Android's Home list,
    /// filters and the tee time detail screen) show/filter on how many spots are actually still
    /// open rather than the original capacity.
    /// </summary>
    public static TeeTimeDto From(TeeTime teeTime, IReadOnlyList<GroupMemberDto> members)
    {
        var acceptedGuestCount = members.Count(m => !m.IsHost);
        var spotsRemaining = Math.Max(0, teeTime.OpenSpots - acceptedGuestCount);
        return new(
            teeTime.Id, teeTime.HostUserId, teeTime.CourseId, teeTime.DateTime,
            teeTime.OpenSpots, spotsRemaining, teeTime.Price, teeTime.Type,
            teeTime.Holes, teeTime.WantedHandicapMin, teeTime.WantedHandicapMax, teeTime.WantedPace,
            teeTime.Status, members);
    }
}
