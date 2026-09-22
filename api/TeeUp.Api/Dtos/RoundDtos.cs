using TeeUp.Api.Models;

namespace TeeUp.Api.Dtos;

/// <summary>
/// <paramref name="Holes"/> is the tee time's intended round length (9 or 18) — null only for a
/// legacy/solo row created before this field existed. RoundsActivity needs it to tell "finished
/// a 9-hole round" apart from "9 of 18 holes scored, still going" rather than assuming 18.
/// </summary>
public record ScheduledRoundDto(Guid TeeTimeId, Guid CourseId, DateTime DateTime, int? Holes, RoundDto? Round);

public record ScorecardEntryRequest(int HoleNumber, int Strokes, int Putts);

public record PostScorecardRequest(IReadOnlyList<ScorecardEntryRequest> Entries);

public record ScorecardEntryDto(Guid Id, int HoleNumber, int Strokes, int Putts, bool Synced)
{
    public static ScorecardEntryDto From(ScorecardEntry entry) => new(
        entry.Id, entry.HoleNumber, entry.Strokes, entry.Putts, entry.Synced);
}

public record RoundDto(Guid Id, Guid TeeTimeId, IReadOnlyList<ScorecardEntryDto> Scorecard)
{
    public static RoundDto From(Round round, IReadOnlyList<ScorecardEntry> entries) => new(
        round.Id, round.TeeTimeId, entries.Select(ScorecardEntryDto.From).ToList());
}
