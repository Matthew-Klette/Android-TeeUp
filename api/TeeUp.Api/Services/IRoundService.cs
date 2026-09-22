using TeeUp.Api.Dtos;

namespace TeeUp.Api.Services;

public interface IRoundService
{
    Task<IReadOnlyList<ScheduledRoundDto>> GetScheduleForUserAsync(Guid userId);
    Task<RoundDto> PostScorecardAsync(Guid teeTimeId, PostScorecardRequest request);
    Task<IReadOnlyList<RoundDto>> GetRoundsForUserAsync(Guid userId);

    /// <summary>
    /// Deletes one hole's scorecard entry (EME-322). <paramref name="callerId"/> must be the
    /// round's owner — the tee time's host, or an accepted guest of it (same scoping as
    /// <see cref="GetRoundsForUserAsync"/>) — otherwise <see cref="TeeUp.Api.Common.ForbiddenException"/>.
    /// No separate edit endpoint exists for this POE; correcting a mis-entered hole is delete-then-repost.
    /// </summary>
    Task DeleteScorecardEntryAsync(Guid roundId, int holeNumber, Guid callerId);
}
