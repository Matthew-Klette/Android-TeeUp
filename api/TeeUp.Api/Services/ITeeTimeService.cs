using TeeUp.Api.Dtos;

namespace TeeUp.Api.Services;

public interface ITeeTimeService
{
    Task<IReadOnlyList<TeeTimeDto>> GetAllAsync();
}
