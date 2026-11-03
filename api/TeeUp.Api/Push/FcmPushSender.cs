using FirebaseAdmin.Messaging;
using Microsoft.Extensions.Logging;
using TeeUp.Api.Models;
using Notification = TeeUp.Api.Models.Notification;

namespace TeeUp.Api.Push;

/// <summary>Sends notifications through Firebase Cloud Messaging to every device token registered for the recipient.</summary>
public class FcmPushSender(
    FirebaseMessaging messaging,
    IDeviceTokenStore tokenStore,
    ILogger<FcmPushSender> logger) : IPushSender
{
    public const string AndroidChannelId = "teeup_alerts";
    private const int MaxTokensPerMulticast = 500; // FCM batch limit.

    public async Task SendAsync(string firebaseUid, Notification notification, CancellationToken cancellationToken = default)
    {
        var tokens = await tokenStore.GetTokensAsync(firebaseUid, cancellationToken);
        if (tokens.Count == 0)
        {
            logger.LogDebug("Push skipped for notification {NotificationId}: user {FirebaseUid} has no device tokens.", notification.Id, firebaseUid);
            return;
        }

        foreach (var batch in tokens.Chunk(MaxTokensPerMulticast))
        {
            var response = await messaging.SendEachForMulticastAsync(BuildMessage(notification, batch), cancellationToken);

            for (var i = 0; i < response.Responses.Count; i++)
            {
                var result = response.Responses[i];
                if (result.IsSuccess)
                    continue;

                var code = result.Exception?.MessagingErrorCode;
                if (IsDeadToken(code))
                {
                    await tokenStore.RemoveTokenAsync(firebaseUid, batch[i], cancellationToken);
                }
                else
                {
                    logger.LogWarning(result.Exception, "Push to one device of user {FirebaseUid} failed ({Code}).", firebaseUid, code);
                }
            }

            logger.LogInformation(
                "Push for notification {NotificationId}: {Success} sent, {Failure} failed.",
                notification.Id, response.SuccessCount, response.FailureCount);
        }
    }

    /// <summary>FCM codes meaning the token will never work again and should be deleted from the database.</summary>
    public static bool IsDeadToken(MessagingErrorCode? code) =>
        code is MessagingErrorCode.Unregistered or MessagingErrorCode.InvalidArgument or MessagingErrorCode.SenderIdMismatch;

    public static MulticastMessage BuildMessage(Notification notification, IReadOnlyList<string> tokens) => new()
    {
        Tokens = tokens,
        Notification = new FirebaseAdmin.Messaging.Notification { Title = "TeeUp", Body = notification.Message },
        Android = new AndroidConfig
        {
            Priority = Priority.High,
            Notification = new AndroidNotification { ChannelId = AndroidChannelId }
        },
        Data = new Dictionary<string, string>
        {
            ["notificationId"] = notification.Id.ToString(),
            ["type"] = ((int)notification.Type).ToString(),
            ["relatedEntityId"] = notification.RelatedEntityId?.ToString() ?? string.Empty
        }
    };
}
