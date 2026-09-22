using TeeUp.Api.Dtos;

namespace TeeUp.Api.Services;

public interface IRoundService
{
    Task<IReadOnlyList<ScheduledRoundDto>> GetScheduleForUserAsync(Guid userId);
    /// <summary>
    /// <paramref name="callerId"/> is whoever posted this scorecard, used only to pick whose
    /// handicap index scores <see cref="RoundDto.NetScore"/>/<see cref="RoundDto.StablefordScore"/>
    /// in the response (EME-304); this endpoint has no host/accepted-guest authorization check
    /// of its own, unlike <see cref="DeleteScorecardEntryAsync"/>.
    /// </summary>
    Task<RoundDto> PostScorecardAsync(Guid teeTimeId, PostScorecardRequest request, Guid callerId);
    Task<IReadOnlyList<RoundDto>> GetRoundsForUserAsync(Guid userId);

    /// <summary>
    /// Deletes one hole's scorecard entry (EME-322). <paramref name="callerId"/> must be the round's owner, the tee time's host or an accepted guest, otherwise <see cref="TeeUp.Api.Common.ForbiddenException"/>.
    /// No separate edit endpoint exists for this POE; correcting a mis-entered hole is delete-then-repost.
    /// </summary>
    Task DeleteScorecardEntryAsync(Guid roundId, int holeNumber, Guid callerId);

    /// <summary>
    /// Deletes a whole round instead of one hole at a time. <paramref name="callerId"/> must be
    /// the host or an accepted guest, otherwise <see cref="TeeUp.Api.Common.ForbiddenException"/>.
    /// </summary>
    Task DeleteRoundAsync(Guid roundId, Guid callerId);
}
