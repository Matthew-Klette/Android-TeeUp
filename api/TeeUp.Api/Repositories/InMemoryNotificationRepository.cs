using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public class InMemoryNotificationRepository : InMemoryRepository<Notification>, INotificationRepository
{
    public InMemoryNotificationRepository() : base(n => n.Id)
    {
    }

    public async Task<IReadOnlyList<Notification>> GetByUserIdAsync(Guid userId)
    {
        var all = await GetAllAsync();
        return all.Where(n => n.UserId == userId).ToList();
    }
}
