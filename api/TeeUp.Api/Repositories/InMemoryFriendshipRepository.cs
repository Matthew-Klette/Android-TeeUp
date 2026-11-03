using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public class InMemoryFriendshipRepository : InMemoryRepository<Friendship>, IFriendshipRepository
{
    public InMemoryFriendshipRepository() : base(f => f.Id)
    {
    }

    public async Task<IReadOnlyList<Friendship>> GetByUserIdAsync(Guid userId)
    {
        var all = await GetAllAsync();
        return all.Where(f => f.UserId == userId).ToList();
    }

    public async Task<Friendship?> GetAsync(Guid userId, Guid friendUserId)
    {
        var all = await GetAllAsync();
        return all.FirstOrDefault(f => f.UserId == userId && f.FriendUserId == friendUserId);
    }
}
