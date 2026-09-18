using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public interface INotificationRepository : IRepository<Notification>
{
    Task<IReadOnlyList<Notification>> GetByUserIdAsync(Guid userId);
}
