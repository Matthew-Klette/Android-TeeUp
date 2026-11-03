using Microsoft.Extensions.Logging;
using TeeUp.Api.Models;

namespace TeeUp.Api.Push;

/// <summary>Used when no Firebase credentials are configured, so the app and tests run without them. Never sends anything.</summary>
public class NoOpPushSender(ILogger<NoOpPushSender> logger) : IPushSender
{
    public Task SendAsync(string firebaseUid, Notification notification, CancellationToken cancellationToken = default)
    {
        logger.LogDebug("Push skipped for notification {NotificationId}: Firebase credentials are not configured.", notification.Id);
        return Task.CompletedTask;
    }
}
