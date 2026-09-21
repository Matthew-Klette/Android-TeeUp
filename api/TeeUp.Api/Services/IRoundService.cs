using TeeUp.Api.Dtos;

namespace TeeUp.Api.Services;

public interface IRoundService
{
    Task<IReadOnlyList<ScheduledRoundDto>> GetScheduleForUserAsync(Guid userId);
    Task<RoundDto> PostScorecardAsync(Guid teeTimeId, PostScorecardRequest request);
    Task<IReadOnlyList<RoundDto>> GetRoundsForUserAsync(Guid userId);
}
