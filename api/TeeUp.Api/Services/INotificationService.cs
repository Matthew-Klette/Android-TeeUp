using TeeUp.Api.Dtos;

namespace TeeUp.Api.Services;

public interface INotificationService
{
    Task<IReadOnlyList<NotificationDto>> GetForUserAsync(Guid userId);
}
