using Microsoft.EntityFrameworkCore;
using TeeUp.Api.Data;
using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public class EfJoinRequestRepository(TeeUpDbContext context)
    : EfRepository<JoinRequest>(context), IJoinRequestRepository
{
    public async Task<IReadOnlyList<JoinRequest>> GetByTeeTimeIdAsync(Guid teeTimeId)
    {
        return await Set.AsNoTracking().Where(j => j.TeeTimeId == teeTimeId).ToListAsync();
    }
}
