using TeeUp.Api.Models;

namespace TeeUp.Api.Dtos;

/// <summary>
/// <paramref name="Holes"/> is the tee time's intended round length (9 or 18), null only for a legacy/solo row created before this field existed. RoundsActivity needs it to tell a finished 9-hole round apart from one still in progress.
/// </summary>
public record ScheduledRoundDto(Guid TeeTimeId, Guid CourseId, DateTime DateTime, int? Holes, RoundDto? Round);

public record ScorecardEntryRequest(int HoleNumber, int Strokes, int Putts);

public record PostScorecardRequest(IReadOnlyList<ScorecardEntryRequest> Entries);

public record ScorecardEntryDto(Guid Id, int HoleNumber, int Strokes, int Putts, bool Synced)
{
    public static ScorecardEntryDto From(ScorecardEntry entry) => new(
        entry.Id, entry.HoleNumber, entry.Strokes, entry.Putts, entry.Synced);
}

/// <summary>
/// <paramref name="AveragePutts"/> is putts per hole played (AVG). <paramref name="NetScore"/> is
/// total strokes minus the scoring player's handicap index; null if that player has none set, or
/// if no holes have been scored yet. <paramref name="StablefordScore"/> is null under the same
/// conditions, since Stableford points are computed net of handicap. EME-304.
/// </summary>
public record RoundDto(
    Guid Id,
    Guid TeeTimeId,
    IReadOnlyList<ScorecardEntryDto> Scorecard,
    int TotalStrokes,
    int TotalPutts,
    double AveragePutts,
    decimal? NetScore,
    int? StablefordScore)
{
    public static RoundDto From(
        Round round, IReadOnlyList<ScorecardEntry> entries, int coursePar, decimal? handicapIndex)
    {
        var totalStrokes = entries.Sum(e => e.Strokes);
        var totalPutts = entries.Sum(e => e.Putts);
        var averagePutts = entries.Count == 0 ? 0 : entries.Average(e => e.Putts);
        var netScore = handicapIndex is decimal h && entries.Count > 0 ? totalStrokes - h : (decimal?)null;
        var stableford = handicapIndex is null || entries.Count == 0
            ? (int?)null
            : ComputeStableford(entries, coursePar, handicapIndex.Value);

        return new(
            round.Id, round.TeeTimeId, entries.Select(ScorecardEntryDto.From).ToList(),
            totalStrokes, totalPutts, averagePutts, netScore, stableford);
    }

    /// <summary>
    /// Simplified Stableford: this schema has no per-hole par or stroke-index data, so par is
    /// spread evenly across played holes (<paramref name="coursePar"/> / 18, the course's full
    /// par) and handicap strokes are allocated one per hole in hole-number order, starting from
    /// hole 1, until the rounded handicap index is used up; not the real stroke-index
    /// allocation a scorecard would use, but a reasonable stand-in without that data. Points
    /// per hole follow standard Stableford: 2 at net par, +1/-1 per stroke away, floored at 0.
    /// </summary>
    private static int ComputeStableford(IReadOnlyList<ScorecardEntry> entries, int coursePar, decimal handicapIndex)
    {
        var holePar = (int)Math.Round(coursePar / 18.0, MidpointRounding.AwayFromZero);
        var strokesReceived = (int)Math.Round(handicapIndex, MidpointRounding.AwayFromZero);

        return entries
            .OrderBy(e => e.HoleNumber)
            .Select((e, index) =>
            {
                var received = index < strokesReceived ? 1 : 0;
                var netStrokes = e.Strokes - received;
                return Math.Max(0, 2 - (netStrokes - holePar));
            })
            .Sum();
    }
}
