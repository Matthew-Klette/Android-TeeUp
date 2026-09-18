using Microsoft.EntityFrameworkCore;
using TeeUp.Api.Data;
using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public class EfNotificationRepository(TeeUpDbContext context)
    : EfRepository<Notification>(context), INotificationRepository
{
    public async Task<IReadOnlyList<Notification>> GetByUserIdAsync(Guid userId)
    {
        return await Set.AsNoTracking().Where(n => n.UserId == userId).ToListAsync();
    }
}
