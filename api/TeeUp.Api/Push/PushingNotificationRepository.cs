using Microsoft.Extensions.Logging;
using TeeUp.Api.Models;
using TeeUp.Api.Repositories;

namespace TeeUp.Api.Push;

/// <summary>
/// Decorates <see cref="INotificationRepository"/> so every persisted notification is also pushed, without changing any
/// service that creates one. The row is always stored; only the push is gated by the recipient's preferences, and a push
/// problem is logged and swallowed so it can never fail the request that created the notification.
/// With <paramref name="sendInBackground"/> (how the app registers it) the FCM send runs after the request returns: a slow
/// FCM or Realtime Database would otherwise hold the request (and any lock it's in) for up to <see cref="PushTimeout"/> per
/// recipient, long enough for the phone's 10 s timeout to fire and a retry to create a duplicate group. The recipient
/// lookup and preference check still happen in the request, so only the singleton sender is used afterwards.
/// </summary>
public class PushingNotificationRepository(
    INotificationRepository inner,
    IUserRepository userRepository,
    IPushSender pushSender,
    ILogger<PushingNotificationRepository> logger,
    bool sendInBackground = false) : INotificationRepository
{
    public static readonly TimeSpan PushTimeout = TimeSpan.FromSeconds(5);

    public Task<IReadOnlyList<Notification>> GetByUserIdAsync(Guid userId) => inner.GetByUserIdAsync(userId);
    public Task<Notification?> GetByIdAsync(Guid id) => inner.GetByIdAsync(id);
    public Task<Notification?> GetByIdFreshAsync(Guid id) => inner.GetByIdFreshAsync(id);
    public Task<IReadOnlyList<Notification>> GetAllAsync() => inner.GetAllAsync();
    public Task UpdateAsync(Notification entity) => inner.UpdateAsync(entity);
    public Task DeleteAsync(Guid id) => inner.DeleteAsync(id);

    public async Task<Notification> AddAsync(Notification entity)
    {
        var stored = await inner.AddAsync(entity);
        await TryPushAsync(stored);
        return stored;
    }

    private async Task TryPushAsync(Notification notification)
    {
        try
        {
            var user = await userRepository.GetByIdAsync(notification.UserId);
            if (user is null)
            {
                logger.LogDebug("Push skipped for notification {NotificationId}: recipient {UserId} not found.", notification.Id, notification.UserId);
                return;
            }

            if (!IsPushWanted(user, notification.Type))
            {
                logger.LogDebug("Push skipped for notification {NotificationId}: {Type} is switched off in the recipient's preferences.", notification.Id, notification.Type);
                return;
            }

            if (sendInBackground)
            {
                var firebaseUid = user.FirebaseUid;
                _ = Task.Run(() => SendAsync(firebaseUid, notification));
                return;
            }
            await SendAsync(user.FirebaseUid, notification);
        }
        catch (Exception ex)
        {
            logger.LogWarning(ex, "Push failed for notification {NotificationId}; the notification itself was saved.", notification.Id);
        }
    }

    private async Task SendAsync(string firebaseUid, Notification notification)
    {
        try
        {
            using var timeout = new CancellationTokenSource(PushTimeout);
            await pushSender.SendAsync(firebaseUid, notification, timeout.Token);
            logger.LogInformation("Push sent for notification {NotificationId} ({Type}).", notification.Id, notification.Type);
        }
        catch (Exception ex)
        {
            logger.LogWarning(ex, "Push failed for notification {NotificationId}; the notification itself was saved.", notification.Id);
        }
    }

    /// <summary>Maps each notification type to the user preference that gates its push (null gate = always push).</summary>
    public static bool IsPushWanted(User user, NotificationType type) => type switch
    {
        NotificationType.JoinRequestReceived
            or NotificationType.RequestAccepted
            or NotificationType.RequestDeclined
            or NotificationType.PrivateGroupInvite => user.JoinRequestNotifications,
        NotificationType.TeeTimeCancelled
            or NotificationType.TeeTimeReminder => user.TeeTimeReminders,
        _ => true
    };
}
