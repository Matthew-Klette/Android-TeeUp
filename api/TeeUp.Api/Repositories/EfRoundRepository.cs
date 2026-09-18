using Microsoft.EntityFrameworkCore;
using TeeUp.Api.Data;
using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public class EfRoundRepository(TeeUpDbContext context) : EfRepository<Round>(context), IRoundRepository
{
    public Task<Round?> GetByTeeTimeIdAsync(Guid teeTimeId)
    {
        return Set.FirstOrDefaultAsync(r => r.TeeTimeId == teeTimeId);
    }
}
