using Microsoft.EntityFrameworkCore;
using TeeUp.Api.Data;
using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public class EfScorecardEntryRepository(TeeUpDbContext context)
    : EfRepository<ScorecardEntry>(context), IScorecardEntryRepository
{
    public async Task<IReadOnlyList<ScorecardEntry>> GetByRoundIdAsync(Guid roundId)
    {
        return await Set.AsNoTracking().Where(s => s.RoundId == roundId).ToListAsync();
    }
}
