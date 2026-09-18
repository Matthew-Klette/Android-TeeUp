using TeeUp.Api.Dtos;
using TeeUp.Api.Repositories;

namespace TeeUp.Api.Services;

public class TeeTimeService(ITeeTimeRepository teeTimeRepository) : ITeeTimeService
{
    public async Task<IReadOnlyList<TeeTimeDto>> GetAllAsync()
    {
        var teeTimes = await teeTimeRepository.GetAllAsync();
        return teeTimes.Select(TeeTimeDto.From).ToList();
    }
}
