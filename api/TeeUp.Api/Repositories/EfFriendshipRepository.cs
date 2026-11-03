using Microsoft.EntityFrameworkCore;
using TeeUp.Api.Data;
using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public class EfFriendshipRepository(TeeUpDbContext context)
    : EfRepository<Friendship>(context), IFriendshipRepository
{
    public async Task<IReadOnlyList<Friendship>> GetByUserIdAsync(Guid userId)
    {
        return await Set.AsNoTracking().Where(f => f.UserId == userId).ToListAsync();
    }

    public Task<Friendship?> GetAsync(Guid userId, Guid friendUserId)
    {
        return Set.AsNoTracking().FirstOrDefaultAsync(f => f.UserId == userId && f.FriendUserId == friendUserId);
    }
}
