using TeeUp.Api.Common;
using TeeUp.Api.Dtos;
using TeeUp.Api.Models;
using TeeUp.Api.Repositories;
using TeeUp.Api.Services;

namespace TeeUp.Api.Tests;

public class PartnersAndPrivateGroupTests
{
    private sealed class Fixture
    {
        public InMemoryUserRepository Users { get; } = new();
        public InMemoryCourseRepository Courses { get; } = new();
        public InMemoryTeeTimeRepository TeeTimes { get; } = new();
        public InMemoryJoinRequestRepository JoinRequests { get; } = new();
        public InMemoryNotificationRepository Notifications { get; } = new();
        public InMemoryFriendshipRepository Friendships { get; } = new();
        public FriendService Friends { get; }
        public TeeTimeService TeeTimeService { get; }
        public JoinRequestService JoinService { get; }

        public Fixture()
        {
            Friends = new FriendService(Friendships, Users);
            var uow = new InMemoryUnitOfWork(TeeTimes, JoinRequests, Notifications);
            TeeTimeService = new TeeTimeService(
                TeeTimes, Users, Courses, JoinRequests, Notifications, new InMemoryRoundRepository(), uow, Friendships);
            JoinService = new JoinRequestService(JoinRequests, TeeTimes, Notifications, Users, Friendships, Courses);
        }

        public async Task<User> AddUser(string name)
        {
            var user = new User { Id = Guid.NewGuid(), FirebaseUid = Guid.NewGuid().ToString(), DisplayName = name };
            await Users.AddAsync(user);
            return user;
        }

        public async Task<Course> AddCourse()
        {
            var course = new Course { Id = Guid.NewGuid(), Name = "Humewood" };
            await Courses.AddAsync(course);
            return course;
        }

        public Task<TeeTimeDto> CreateGroup(User host, Course course, bool isPrivate) =>
            TeeTimeService.CreateGroupAsync(host.Id, new CreateGroupRequest(
                course.Id, DateTime.UtcNow.AddDays(2), 18, 2, null, null, null, isPrivate));
    }

    // ---- Friends ----

    [Fact]
    public async Task AddFriend_SavesPartnerAndReturnsDto()
    {
        var f = new Fixture();
        var me = await f.AddUser("Me");
        var pal = await f.AddUser("Pal");

        var dto = await f.Friends.AddFriendAsync(me.Id, pal.Id);

        Assert.Equal(pal.Id, dto.UserId);
        Assert.Single(await f.Friends.GetFriendsAsync(me.Id));
    }

    [Fact]
    public async Task AddFriend_Twice_IsIdempotent()
    {
        var f = new Fixture();
        var me = await f.AddUser("Me");
        var pal = await f.AddUser("Pal");

        await f.Friends.AddFriendAsync(me.Id, pal.Id);
        await f.Friends.AddFriendAsync(me.Id, pal.Id);

        Assert.Single(await f.Friendships.GetAllAsync());
    }

    [Fact]
    public async Task AddFriend_Self_ThrowsDomainValidation()
    {
        var f = new Fixture();
        var me = await f.AddUser("Me");

        await Assert.ThrowsAsync<DomainValidationException>(() => f.Friends.AddFriendAsync(me.Id, me.Id));
    }

    [Fact]
    public async Task AddFriend_UnknownUser_ThrowsNotFound()
    {
        var f = new Fixture();
        var me = await f.AddUser("Me");

        await Assert.ThrowsAsync<NotFoundException>(() => f.Friends.AddFriendAsync(me.Id, Guid.NewGuid()));
    }

    [Fact]
    public async Task RemoveFriend_RemovesAndIsIdempotent()
    {
        var f = new Fixture();
        var me = await f.AddUser("Me");
        var pal = await f.AddUser("Pal");
        await f.Friends.AddFriendAsync(me.Id, pal.Id);

        await f.Friends.RemoveFriendAsync(me.Id, pal.Id);
        await f.Friends.RemoveFriendAsync(me.Id, pal.Id);

        Assert.Empty(await f.Friends.GetFriendsAsync(me.Id));
    }

    [Fact]
    public async Task GetFriends_SortedByDisplayName()
    {
        var f = new Fixture();
        var me = await f.AddUser("Me");
        foreach (var name in new[] { "Zed", "alice", "Bob" })
            await f.Friends.AddFriendAsync(me.Id, (await f.AddUser(name)).Id);

        var names = (await f.Friends.GetFriendsAsync(me.Id)).Select(x => x.DisplayName);

        Assert.Equal(["alice", "Bob", "Zed"], names);
    }

    [Fact]
    public async Task Friendship_IsDirectional()
    {
        var f = new Fixture();
        var me = await f.AddUser("Me");
        var pal = await f.AddUser("Pal");
        await f.Friends.AddFriendAsync(me.Id, pal.Id);

        Assert.Empty(await f.Friends.GetFriendsAsync(pal.Id));
    }

    // ---- User search ----

    [Fact]
    public async Task Search_ShortOrBlankQuery_ReturnsEmpty()
    {
        var f = new Fixture();
        var me = await f.AddUser("Me");
        await f.AddUser("Alice");

        Assert.Empty(await f.Friends.SearchUsersAsync(me.Id, "a"));
        Assert.Empty(await f.Friends.SearchUsersAsync(me.Id, "  a "));
        Assert.Empty(await f.Friends.SearchUsersAsync(me.Id, null));
    }

    [Fact]
    public async Task Search_IsCaseInsensitiveTrimmedExcludesCallerAndFlagsPartners()
    {
        var f = new Fixture();
        var me = await f.AddUser("Alicia Me");
        var alice = await f.AddUser("Alice Jones");
        var malik = await f.AddUser("Malice Smith");
        await f.AddUser("Bob");
        await f.Friends.AddFriendAsync(me.Id, alice.Id);

        var results = await f.Friends.SearchUsersAsync(me.Id, "  ALIC ");

        Assert.Equal(["Alice Jones", "Malice Smith"], results.Select(r => r.DisplayName));
        Assert.True(results[0].IsPartner);
        Assert.False(results[1].IsPartner);
        Assert.DoesNotContain(results, r => r.UserId == me.Id);
        Assert.Equal(malik.Id, results[1].UserId);
    }

    [Fact]
    public async Task Search_CapsAtTwentyResults()
    {
        var f = new Fixture();
        var me = await f.AddUser("Me");
        for (var i = 0; i < 25; i++) await f.AddUser($"Golfer {i:00}");

        Assert.Equal(20, (await f.Friends.SearchUsersAsync(me.Id, "golfer")).Count);
    }

    // ---- Private groups ----

    [Fact]
    public async Task GetAll_PrivateGroup_HiddenFromStrangersAndAnonymous_VisibleToPartnerAndHost()
    {
        var f = new Fixture();
        var host = await f.AddUser("Host");
        var partner = await f.AddUser("Partner");
        var stranger = await f.AddUser("Stranger");
        var course = await f.AddCourse();
        await f.Friends.AddFriendAsync(host.Id, partner.Id);
        await f.CreateGroup(host, course, isPrivate: true);

        Assert.Empty(await f.TeeTimeService.GetAllAsync(callerUserId: stranger.Id));
        Assert.Empty(await f.TeeTimeService.GetAllAsync());
        Assert.Single(await f.TeeTimeService.GetAllAsync(callerUserId: partner.Id));
        var forHost = await f.TeeTimeService.GetAllAsync(callerUserId: host.Id);
        Assert.True(Assert.Single(forHost).IsPrivate);
    }

    [Fact]
    public async Task GetAll_PartnerRelationIsDirectional()
    {
        var f = new Fixture();
        var host = await f.AddUser("Host");
        var other = await f.AddUser("Other");
        var course = await f.AddCourse();
        await f.Friends.AddFriendAsync(other.Id, host.Id); // host is other's partner, not vice versa
        await f.CreateGroup(host, course, isPrivate: true);

        Assert.Empty(await f.TeeTimeService.GetAllAsync(callerUserId: other.Id));
    }

    [Fact]
    public async Task CreateJoinRequest_OnPrivateGroupAsNonPartner_ThrowsForbidden_PartnerSucceeds()
    {
        var f = new Fixture();
        var host = await f.AddUser("Host");
        var partner = await f.AddUser("Partner");
        var stranger = await f.AddUser("Stranger");
        var course = await f.AddCourse();
        await f.Friends.AddFriendAsync(host.Id, partner.Id);
        var group = await f.CreateGroup(host, course, isPrivate: true);

        await Assert.ThrowsAsync<ForbiddenException>(() => f.JoinService.CreateAsync(group.Id, stranger.Id));
        var request = await f.JoinService.CreateAsync(group.Id, partner.Id);

        Assert.Equal(JoinRequestStatus.Pending, request.Status);
    }

    [Fact]
    public async Task GetJoinRequests_OnPrivateGroup_ForbiddenForStranger_AllowedForHost()
    {
        var f = new Fixture();
        var host = await f.AddUser("Host");
        var stranger = await f.AddUser("Stranger");
        var course = await f.AddCourse();
        var group = await f.CreateGroup(host, course, isPrivate: true);

        await Assert.ThrowsAsync<ForbiddenException>(() => f.JoinService.GetForTeeTimeAsync(group.Id, stranger.Id));
        await Assert.ThrowsAsync<ForbiddenException>(() => f.JoinService.GetForTeeTimeAsync(group.Id));
        Assert.Empty(await f.JoinService.GetForTeeTimeAsync(group.Id, host.Id));
    }

    [Fact]
    public async Task CreateGroup_Private_NotifiesEachPartnerWithInvite()
    {
        var f = new Fixture();
        var host = await f.AddUser("Hannah");
        var p1 = await f.AddUser("P1");
        var p2 = await f.AddUser("P2");
        var stranger = await f.AddUser("Stranger");
        var course = await f.AddCourse();
        await f.Friends.AddFriendAsync(host.Id, p1.Id);
        await f.Friends.AddFriendAsync(host.Id, p2.Id);

        var group = await f.CreateGroup(host, course, isPrivate: true);

        var all = await f.Notifications.GetAllAsync();
        Assert.Equal(2, all.Count);
        Assert.All(all, n =>
        {
            Assert.Equal(NotificationType.PrivateGroupInvite, n.Type);
            Assert.Equal(group.Id, n.RelatedEntityId);
            Assert.Equal("Hannah invited you to a partners-only round at Humewood.", n.Message);
        });
        Assert.DoesNotContain(all, n => n.UserId == stranger.Id);
    }

    [Fact]
    public async Task CreateGroup_Public_SendsNoInvites()
    {
        var f = new Fixture();
        var host = await f.AddUser("Host");
        var course = await f.AddCourse();
        await f.Friends.AddFriendAsync(host.Id, (await f.AddUser("P")).Id);

        await f.CreateGroup(host, course, isPrivate: false);

        Assert.Empty(await f.Notifications.GetAllAsync());
    }

    [Fact]
    public async Task Edit_FlippingToPrivate_NotifiesPartnersOnce_NullLeavesUnchanged()
    {
        var f = new Fixture();
        var host = await f.AddUser("Host");
        var partner = await f.AddUser("Partner");
        var course = await f.AddCourse();
        await f.Friends.AddFriendAsync(host.Id, partner.Id);
        var group = await f.CreateGroup(host, course, isPrivate: false);
        var when = DateTime.UtcNow.AddDays(3);

        var unchanged = await f.TeeTimeService.EditAsync(group.Id, host.Id, new UpdateGroupRequest(when, 18, 2, null, null, null));
        Assert.False(unchanged.IsPrivate);
        Assert.Empty(await f.Notifications.GetAllAsync());

        var flipped = await f.TeeTimeService.EditAsync(group.Id, host.Id, new UpdateGroupRequest(when, 18, 2, null, null, null, true));
        Assert.True(flipped.IsPrivate);
        Assert.Single(await f.Notifications.GetAllAsync());

        // Already private: editing again must not re-invite.
        await f.TeeTimeService.EditAsync(group.Id, host.Id, new UpdateGroupRequest(when, 18, 2, null, null, null, true));
        Assert.Single(await f.Notifications.GetAllAsync());
    }

    // ---- Join request notification ----

    [Fact]
    public async Task CreateJoinRequest_NotifiesHostWithGuestAndCourseName()
    {
        var f = new Fixture();
        var host = await f.AddUser("Host");
        var guest = await f.AddUser("Gary");
        var course = await f.AddCourse();
        var group = await f.CreateGroup(host, course, isPrivate: false);

        await f.JoinService.CreateAsync(group.Id, guest.Id);

        var notification = Assert.Single(await f.Notifications.GetAllAsync());
        Assert.Equal(host.Id, notification.UserId);
        Assert.Equal(NotificationType.JoinRequestReceived, notification.Type);
        Assert.Equal("Gary asked to join your group at Humewood.", notification.Message);
        Assert.Equal(group.Id, notification.RelatedEntityId);
    }

    [Fact]
    public void NotificationType_PrivateGroupInvite_IsOrdinalSeven()
    {
        Assert.Equal(7, (int)NotificationType.PrivateGroupInvite);
        Assert.Equal(6, (int)NotificationType.TeeTimeCancelled);
    }
}
