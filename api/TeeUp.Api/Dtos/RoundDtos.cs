using TeeUp.Api.Models;

namespace TeeUp.Api.Dtos;

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
