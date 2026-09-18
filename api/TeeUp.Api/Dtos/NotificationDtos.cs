using TeeUp.Api.Models;

namespace TeeUp.Api.Dtos;

public record NotificationDto(
    Guid Id,
    NotificationType Type,
    string Message,
    Guid? RelatedEntityId,
    bool IsRead,
    DateTime CreatedAt)
{
    public static NotificationDto From(Notification notification) => new(
        notification.Id, notification.Type, notification.Message,
        notification.RelatedEntityId, notification.IsRead, notification.CreatedAt);
}
