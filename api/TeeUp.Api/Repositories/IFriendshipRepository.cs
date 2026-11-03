using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public interface IFriendshipRepository : IRepository<Friendship>
{
    /// <summary>Every partner saved by <paramref name="userId"/>.</summary>
    Task<IReadOnlyList<Friendship>> GetByUserIdAsync(Guid userId);

    /// <summary>The (owner, partner) row, or null if that partner is not saved.</summary>
    Task<Friendship?> GetAsync(Guid userId, Guid friendUserId);
}
