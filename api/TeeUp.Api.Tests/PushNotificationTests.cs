using Microsoft.Extensions.Logging.Abstractions;
using TeeUp.Api.Models;
using TeeUp.Api.Push;
using TeeUp.Api.Repositories;

namespace TeeUp.Api.Tests;

public class PushNotificationTests
{
    private sealed class FakePushSender : IPushSender
    {
        public List<(string Uid, Notification Notification)> Sent { get; } = [];
        public Exception? ToThrow { get; set; }

        public Task SendAsync(string firebaseUid, Notification notification, CancellationToken cancellationToken = default)
        {
            if (ToThrow is not null) throw ToThrow;
            Sent.Add((firebaseUid, notification));
            return Task.CompletedTask;
        }
    }

    private static async Task<(PushingNotificationRepository Repo, InMemoryNotificationRepository Inner, FakePushSender Sender, User User)>
        Create(bool joinRequests = true, bool reminders = true)
    {
        var users = new InMemoryUserRepository();
        var user = new User
        {
            Id = Guid.NewGuid(), FirebaseUid = "uid-1", DisplayName = "U",
            JoinRequestNotifications = joinRequests, TeeTimeReminders = reminders
        };
        await users.AddAsync(user);
        var inner = new InMemoryNotificationRepository();
        var sender = new FakePushSender();
        return (new PushingNotificationRepository(inner, users, sender, NullLogger<PushingNotificationRepository>.Instance), inner, sender, user);
    }

    private static Notification Make(Guid userId, NotificationType type) =>
        new() { Id = Guid.NewGuid(), UserId = userId, Type = type, Message = "hello" };

    [Theory]
    [InlineData(NotificationType.JoinRequestReceived)]
    [InlineData(NotificationType.RequestAccepted)]
    [InlineData(NotificationType.RequestDeclined)]
    [InlineData(NotificationType.PrivateGroupInvite)]
    public async Task JoinRelatedTypes_SkipPushButStillStore_WhenJoinRequestNotificationsOff(NotificationType type)
    {
        var (repo, inner, sender, user) = await Create(joinRequests: false);

        await repo.AddAsync(Make(user.Id, type));

        Assert.Empty(sender.Sent);
        Assert.Single(await inner.GetAllAsync());
    }

    [Theory]
    [InlineData(NotificationType.TeeTimeCancelled)]
    [InlineData(NotificationType.TeeTimeReminder)]
    public async Task ReminderTypes_SkipPush_WhenTeeTimeRemindersOff_ButNotWhenJoinOff(NotificationType type)
    {
        var (repo, inner, sender, user) = await Create(joinRequests: false, reminders: false);

        await repo.AddAsync(Make(user.Id, type));
        Assert.Empty(sender.Sent);
        Assert.Single(await inner.GetAllAsync());

        var (repo2, _, sender2, user2) = await Create(joinRequests: false, reminders: true);
        await repo2.AddAsync(Make(user2.Id, type));
        Assert.Single(sender2.Sent);
    }

    [Fact]
    public async Task OtherTypes_AlwaysPush()
    {
        var (repo, _, sender, user) = await Create(joinRequests: false, reminders: false);

        await repo.AddAsync(Make(user.Id, NotificationType.SyncPending));

        Assert.Single(sender.Sent);
        Assert.Equal("uid-1", sender.Sent[0].Uid);
    }

    [Fact]
    public async Task SenderException_IsSwallowed_AndNotificationStillStored()
    {
        var (repo, inner, sender, user) = await Create();
        sender.ToThrow = new InvalidOperationException("fcm down");

        var stored = await repo.AddAsync(Make(user.Id, NotificationType.JoinRequestReceived));

        Assert.NotNull(stored);
        Assert.Single(await inner.GetAllAsync());
    }

    [Fact]
    public async Task UnknownRecipient_StoresWithoutPushing()
    {
        var (repo, inner, sender, _) = await Create();

        await repo.AddAsync(Make(Guid.NewGuid(), NotificationType.JoinRequestReceived));

        Assert.Empty(sender.Sent);
        Assert.Single(await inner.GetAllAsync());
    }

    [Fact]
    public void FcmMessage_HasExpectedShape()
    {
        var related = Guid.NewGuid();
        var n = new Notification
        {
            Id = Guid.NewGuid(), UserId = Guid.NewGuid(), Type = NotificationType.PrivateGroupInvite,
            Message = "Body text", RelatedEntityId = related
        };

        var message = FcmPushSender.BuildMessage(n, ["t1", "t2"]);

        Assert.Equal("TeeUp", message.Notification.Title);
        Assert.Equal("Body text", message.Notification.Body);
        Assert.Equal("teeup_alerts", message.Android.Notification.ChannelId);
        Assert.Equal(FirebaseAdmin.Messaging.Priority.High, message.Android.Priority);
        Assert.Equal("7", message.Data["type"]);
        Assert.Equal(related.ToString(), message.Data["relatedEntityId"]);
        Assert.Equal(n.Id.ToString(), message.Data["notificationId"]);
        Assert.Equal(2, message.Tokens.Count);

        n.RelatedEntityId = null;
        Assert.Equal(string.Empty, FcmPushSender.BuildMessage(n, ["t"]).Data["relatedEntityId"]);
    }

    [Theory]
    [InlineData(FirebaseAdmin.Messaging.MessagingErrorCode.Unregistered, true)]
    [InlineData(FirebaseAdmin.Messaging.MessagingErrorCode.InvalidArgument, true)]
    [InlineData(FirebaseAdmin.Messaging.MessagingErrorCode.SenderIdMismatch, true)]
    [InlineData(FirebaseAdmin.Messaging.MessagingErrorCode.Unavailable, false)]
    public void DeadTokenCodes_AreClassifiedCorrectly(FirebaseAdmin.Messaging.MessagingErrorCode code, bool dead)
    {
        Assert.Equal(dead, FcmPushSender.IsDeadToken(code));
    }
}
