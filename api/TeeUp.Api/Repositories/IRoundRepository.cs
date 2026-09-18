using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public interface IRoundRepository : IRepository<Round>
{
    Task<Round?> GetByTeeTimeIdAsync(Guid teeTimeId);
}
