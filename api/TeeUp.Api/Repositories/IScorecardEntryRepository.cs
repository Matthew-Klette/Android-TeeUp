using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public interface IScorecardEntryRepository : IRepository<ScorecardEntry>
{
    Task<IReadOnlyList<ScorecardEntry>> GetByRoundIdAsync(Guid roundId);
}
