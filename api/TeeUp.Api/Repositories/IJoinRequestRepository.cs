using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public interface IJoinRequestRepository : IRepository<JoinRequest>
{
    Task<IReadOnlyList<JoinRequest>> GetByTeeTimeIdAsync(Guid teeTimeId);
}
