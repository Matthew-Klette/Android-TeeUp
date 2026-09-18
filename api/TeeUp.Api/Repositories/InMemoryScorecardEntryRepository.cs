using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public class InMemoryScorecardEntryRepository : InMemoryRepository<ScorecardEntry>, IScorecardEntryRepository
{
    public InMemoryScorecardEntryRepository() : base(s => s.Id)
    {
    }

    public async Task<IReadOnlyList<ScorecardEntry>> GetByRoundIdAsync(Guid roundId)
    {
        var all = await GetAllAsync();
        return all.Where(s => s.RoundId == roundId).ToList();
    }
}
