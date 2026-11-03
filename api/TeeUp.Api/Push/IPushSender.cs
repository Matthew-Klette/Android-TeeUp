using TeeUp.Api.Models;

namespace TeeUp.Api.Push;

/// <summary>
/// Delivers a stored <see cref="Notification"/> to the recipient's devices as a push message.
/// Implementations may throw; <see cref="PushingNotificationRepository"/> guarantees a push failure never fails a request.
/// </summary>
public interface IPushSender
{
    Task SendAsync(string firebaseUid, Notification notification, CancellationToken cancellationToken = default);
}
