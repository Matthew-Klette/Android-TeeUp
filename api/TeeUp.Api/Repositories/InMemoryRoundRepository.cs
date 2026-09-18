using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public class InMemoryRoundRepository : InMemoryRepository<Round>, IRoundRepository
{
    public InMemoryRoundRepository() : base(r => r.Id)
    {
    }

    public async Task<Round?> GetByTeeTimeIdAsync(Guid teeTimeId)
    {
        var all = await GetAllAsync();
        return all.FirstOrDefault(r => r.TeeTimeId == teeTimeId);
    }
}
