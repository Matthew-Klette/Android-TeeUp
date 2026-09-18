using TeeUp.Api.Dtos;
using TeeUp.Api.Repositories;

namespace TeeUp.Api.Services;

public class NotificationService(INotificationRepository notificationRepository) : INotificationService
{
    public async Task<IReadOnlyList<NotificationDto>> GetForUserAsync(Guid userId)
    {
        var notifications = await notificationRepository.GetByUserIdAsync(userId);
        return notifications
            .OrderByDescending(n => n.CreatedAt)
            .Select(NotificationDto.From)
            .ToList();
    }
}
