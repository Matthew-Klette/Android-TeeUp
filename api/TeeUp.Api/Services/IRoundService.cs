using TeeUp.Api.Dtos;

namespace TeeUp.Api.Services;

public interface IRoundService
{
    Task<RoundDto> PostScorecardAsync(Guid teeTimeId, PostScorecardRequest request);
    Task<IReadOnlyList<RoundDto>> GetRoundsForUserAsync(Guid userId);
}
