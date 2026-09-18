using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public class InMemoryJoinRequestRepository : InMemoryRepository<JoinRequest>, IJoinRequestRepository
{
    public InMemoryJoinRequestRepository() : base(j => j.Id)
    {
    }

    public async Task<IReadOnlyList<JoinRequest>> GetByTeeTimeIdAsync(Guid teeTimeId)
    {
        var all = await GetAllAsync();
        return all.Where(j => j.TeeTimeId == teeTimeId).ToList();
    }
}
