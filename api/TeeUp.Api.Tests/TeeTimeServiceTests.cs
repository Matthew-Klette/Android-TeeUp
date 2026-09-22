using TeeUp.Api.Common;
using TeeUp.Api.Dtos;
using TeeUp.Api.Models;
using TeeUp.Api.Repositories;
using TeeUp.Api.Services;

namespace TeeUp.Api.Tests;

public class TeeTimeServiceTests
{
    private static (TeeTimeService Service, InMemoryTeeTimeRepository TeeTimes, InMemoryUserRepository Users, InMemoryCourseRepository Courses)
        CreateService(InMemoryJoinRequestRepository? joinRequests = null)
    {
        return CreateServiceWithJoinRequests(joinRequests ?? new InMemoryJoinRequestRepository());
    }

    private static (TeeTimeService Service, InMemoryTeeTimeRepository TeeTimes, InMemoryUserRepository Users, InMemoryCourseRepository Courses)
        CreateServiceWithJoinRequests(InMemoryJoinRequestRepository joinRequests, InMemoryNotificationRepository? notifications = null)
    {
        var teeTimes = new InMemoryTeeTimeRepository();
        var users = new InMemoryUserRepository();
        var courses = new InMemoryCourseRepository();
        return (
            new TeeTimeService(teeTimes, users, courses, joinRequests, notifications ?? new InMemoryNotificationRepository()),
            teeTimes, users, courses);
    }

    private static async Task<User> AddHost(InMemoryUserRepository users, decimal? handicap, PaceOfPlay pace)
    {
        var host = new User
        {
            Id = Guid.NewGuid(),
            FirebaseUid = Guid.NewGuid().ToString(),
            DisplayName = "Host",
            HandicapIndex = handicap,
            PaceOfPlay = pace
        };
        await users.AddAsync(host);
        return host;
    }

    private static TeeTime MakeTeeTime(
        Guid? hostUserId, TeeTimeType type = TeeTimeType.OpenRound,
        TeeTimeStatus status = TeeTimeStatus.Open, DateTime? dateTime = null, int openSpots = 2) => new()
    {
        Id = Guid.NewGuid(),
        HostUserId = hostUserId,
        CourseId = Guid.NewGuid(),
        DateTime = dateTime ?? DateTime.UtcNow.AddDays(1),
        OpenSpots = openSpots,
        Price = 0,
        Type = type,
        Status = status
    };

    [Fact]
    public async Task GetAllAsync_WithNoFilters_ReturnsEverything()
    {
        var (service, teeTimes, users, _) = CreateService();
        var host = await AddHost(users, handicap: 20m, PaceOfPlay.Relaxed);
        await teeTimes.AddAsync(MakeTeeTime(host.Id));
        await teeTimes.AddAsync(MakeTeeTime(hostUserId: null));

        var result = await service.GetAllAsync();

        Assert.Equal(2, result.Count);
    }

    [Fact]
    public async Task GetAllAsync_WithMaxHandicap_ExcludesHostsAboveThreshold()
    {
        var (service, teeTimes, users, _) = CreateService();
        var lowHandicapHost = await AddHost(users, handicap: 10m, PaceOfPlay.Standard);
        var highHandicapHost = await AddHost(users, handicap: 30m, PaceOfPlay.Standard);
        var lowTeeTime = MakeTeeTime(lowHandicapHost.Id);
        await teeTimes.AddAsync(lowTeeTime);
        await teeTimes.AddAsync(MakeTeeTime(highHandicapHost.Id));

        var result = await service.GetAllAsync(maxHandicap: 15m);

        var onlyResult = Assert.Single(result);
        Assert.Equal(lowTeeTime.Id, onlyResult.Id);
    }

    [Fact]
    public async Task GetAllAsync_WithPaceFilter_OnlyMatchesExactPace()
    {
        var (service, teeTimes, users, _) = CreateService();
        var briskHost = await AddHost(users, handicap: null, PaceOfPlay.Brisk);
        var relaxedHost = await AddHost(users, handicap: null, PaceOfPlay.Relaxed);
        var briskTeeTime = MakeTeeTime(briskHost.Id);
        await teeTimes.AddAsync(briskTeeTime);
        await teeTimes.AddAsync(MakeTeeTime(relaxedHost.Id));

        var result = await service.GetAllAsync(pace: PaceOfPlay.Brisk);

        var onlyResult = Assert.Single(result);
        Assert.Equal(briskTeeTime.Id, onlyResult.Id);
    }

    [Fact]
    public async Task GetAllAsync_WithFilterActive_ExcludesTeeTimesWithNoHost()
    {
        var (service, teeTimes, _, _) = CreateService();
        await teeTimes.AddAsync(MakeTeeTime(hostUserId: null));

        var result = await service.GetAllAsync(maxHandicap: 54m);

        Assert.Empty(result);
    }

    [Fact]
    public async Task GetAllAsync_WithMaxHandicap_ExcludesHostWithNoHandicapSet()
    {
        var (service, teeTimes, users, _) = CreateService();
        var host = await AddHost(users, handicap: null, PaceOfPlay.Standard);
        await teeTimes.AddAsync(MakeTeeTime(host.Id));

        var result = await service.GetAllAsync(maxHandicap: 54m);

        Assert.Empty(result);
    }

    [Fact]
    public async Task GetAllAsync_IncludesHostAndAcceptedGuestsAsMembers()
    {
        var (service, teeTimes, users, _) = CreateService();
        var host = await AddHost(users, handicap: 12m, PaceOfPlay.Standard);
        var teeTime = MakeTeeTime(host.Id);
        await teeTimes.AddAsync(teeTime);

        var result = await service.GetAllAsync();

        var onlyResult = Assert.Single(result);
        var member = Assert.Single(onlyResult.Members);
        Assert.Equal(host.Id, member.UserId);
        Assert.True(member.IsHost);
    }

    [Fact]
    public async Task GetAllAsync_JoinableOnly_ExcludesBookingsFullCancelledAndPastGroups()
    {
        var (service, teeTimes, users, _) = CreateService();
        var host = await AddHost(users, handicap: null, PaceOfPlay.Standard);

        var joinableGroup = MakeTeeTime(host.Id);
        var booking = MakeTeeTime(host.Id, type: TeeTimeType.Booking);
        var full = MakeTeeTime(host.Id, status: TeeTimeStatus.Full);
        var cancelled = MakeTeeTime(host.Id, status: TeeTimeStatus.Cancelled);
        var past = MakeTeeTime(host.Id, dateTime: DateTime.UtcNow.AddDays(-1));

        await teeTimes.AddAsync(joinableGroup);
        await teeTimes.AddAsync(booking);
        await teeTimes.AddAsync(full);
        await teeTimes.AddAsync(cancelled);
        await teeTimes.AddAsync(past);

        var result = await service.GetAllAsync(joinableOnly: true);

        var onlyResult = Assert.Single(result);
        Assert.Equal(joinableGroup.Id, onlyResult.Id);
    }

    [Fact]
    public async Task GetAllAsync_JoinableOnly_ExcludesOpenGroupsWithNoRemainingCapacity()
    {
        var joinRequests = new InMemoryJoinRequestRepository();
        var (service, teeTimes, users, _) = CreateService(joinRequests);
        var host = await AddHost(users, handicap: null, PaceOfPlay.Standard);

        // Status alone says Open, but its single spot has already been accepted.
        // Must still be excluded, since Status alone can't be trusted here.
        var fullButStillOpen = MakeTeeTime(host.Id, openSpots: 1);
        await teeTimes.AddAsync(fullButStillOpen);
        await joinRequests.AddAsync(new JoinRequest
        {
            Id = Guid.NewGuid(),
            TeeTimeId = fullButStillOpen.Id,
            GuestUserId = Guid.NewGuid(),
            Status = JoinRequestStatus.Accepted
        });

        var trulyOpen = MakeTeeTime(host.Id, openSpots: 1);
        await teeTimes.AddAsync(trulyOpen);

        var result = await service.GetAllAsync(joinableOnly: true);

        var onlyResult = Assert.Single(result);
        Assert.Equal(trulyOpen.Id, onlyResult.Id);
    }

    [Fact]
    public async Task GetAllAsync_JoinableOnlyFalse_StillReturnsBookingsFullCancelledAndPastGroups()
    {
        var (service, teeTimes, users, _) = CreateService();
        var host = await AddHost(users, handicap: null, PaceOfPlay.Standard);

        await teeTimes.AddAsync(MakeTeeTime(host.Id));
        await teeTimes.AddAsync(MakeTeeTime(host.Id, type: TeeTimeType.Booking));
        await teeTimes.AddAsync(MakeTeeTime(host.Id, status: TeeTimeStatus.Full));
        await teeTimes.AddAsync(MakeTeeTime(host.Id, status: TeeTimeStatus.Cancelled));
        await teeTimes.AddAsync(MakeTeeTime(host.Id, dateTime: DateTime.UtcNow.AddDays(-1)));

        var result = await service.GetAllAsync();

        Assert.Equal(5, result.Count);
    }

    private static async Task<Course> AddCourse(InMemoryCourseRepository courses)
    {
        var course = new Course { Id = Guid.NewGuid(), Name = "Test Course", Latitude = 0, Longitude = 0 };
        await courses.AddAsync(course);
        return course;
    }

    private static CreateGroupRequest MakeGroupRequest(Guid courseId, int holes = 18, int openSpots = 3,
        decimal? min = null, decimal? max = null, PaceOfPlay? pace = null) =>
        new(courseId, DateTime.UtcNow.AddDays(1), holes, openSpots, min, max, pace);

    [Fact]
    public async Task CreateGroupAsync_WithValidRequest_CreatesOpenGroupHostedByCaller()
    {
        var (service, _, users, courses) = CreateService();
        var host = await AddHost(users, handicap: 14.5m, PaceOfPlay.Relaxed);
        var course = await AddCourse(courses);

        var result = await service.CreateGroupAsync(host.Id, MakeGroupRequest(course.Id, min: 5m, max: 20m, pace: PaceOfPlay.Relaxed));

        Assert.Equal(host.Id, result.HostUserId);
        Assert.Equal(TeeTimeType.OpenRound, result.Type);
        Assert.Equal(TeeTimeStatus.Open, result.Status);
        Assert.Equal(18, result.Holes);
        Assert.Equal(3, result.OpenSpots);
        var member = Assert.Single(result.Members);
        Assert.True(member.IsHost);
        Assert.Equal(host.Id, member.UserId);
    }

    [Fact]
    public async Task CreateGroupAsync_ForUnknownCourse_ThrowsValidationError()
    {
        var (service, _, users, _) = CreateService();
        var host = await AddHost(users, handicap: null, PaceOfPlay.Standard);

        // CourseId is a request-body field, not a URL resource, so an unknown
        // value is a bad request (400), not a missing resource (404).
        var ex = await Assert.ThrowsAsync<DomainValidationException>(() =>
            service.CreateGroupAsync(host.Id, MakeGroupRequest(Guid.NewGuid())));
        Assert.Contains("does not exist", ex.Message);
    }

    [Fact]
    public async Task CreateGroupAsync_ForFreshGroup_SpotsRemainingEqualsOpenSpots()
    {
        var (service, _, users, courses) = CreateService();
        var host = await AddHost(users, handicap: null, PaceOfPlay.Standard);
        var course = await AddCourse(courses);

        var result = await service.CreateGroupAsync(host.Id, MakeGroupRequest(course.Id, openSpots: 3));

        Assert.Equal(3, result.OpenSpots);
        Assert.Equal(3, result.SpotsRemaining);
    }

    [Fact]
    public async Task CreateGroupAsync_InThePast_ThrowsValidationError()
    {
        var (service, _, users, courses) = CreateService();
        var host = await AddHost(users, handicap: null, PaceOfPlay.Standard);
        var course = await AddCourse(courses);
        var request = MakeGroupRequest(course.Id) with { DateTime = DateTime.UtcNow.AddDays(-1) };

        await Assert.ThrowsAsync<DomainValidationException>(() => service.CreateGroupAsync(host.Id, request));
    }

    [Theory]
    [InlineData(1)]
    [InlineData(19)]
    public async Task CreateGroupAsync_WithInvalidHoles_ThrowsValidationError(int holes)
    {
        var (service, _, users, courses) = CreateService();
        var host = await AddHost(users, handicap: null, PaceOfPlay.Standard);
        var course = await AddCourse(courses);

        await Assert.ThrowsAsync<DomainValidationException>(() =>
            service.CreateGroupAsync(host.Id, MakeGroupRequest(course.Id, holes: holes)));
    }

    [Fact]
    public async Task CreateGroupAsync_WithZeroOpenSpots_ThrowsValidationError()
    {
        var (service, _, users, courses) = CreateService();
        var host = await AddHost(users, handicap: null, PaceOfPlay.Standard);
        var course = await AddCourse(courses);

        await Assert.ThrowsAsync<DomainValidationException>(() =>
            service.CreateGroupAsync(host.Id, MakeGroupRequest(course.Id, openSpots: 0)));
    }

    [Fact]
    public async Task CreateSoloAsync_WithNoHoles_DefaultsToEighteen()
    {
        var (service, _, users, courses) = CreateService();
        var host = await AddHost(users, handicap: null, PaceOfPlay.Standard);
        var course = await AddCourse(courses);

        var result = await service.CreateSoloAsync(host.Id, course.Id);

        Assert.Equal(18, result.Holes);
    }

    [Theory]
    [InlineData(9)]
    [InlineData(18)]
    public async Task CreateSoloAsync_WithValidHoles_PersistsThem(int holes)
    {
        var (service, _, users, courses) = CreateService();
        var host = await AddHost(users, handicap: null, PaceOfPlay.Standard);
        var course = await AddCourse(courses);

        var result = await service.CreateSoloAsync(host.Id, course.Id, holes);

        Assert.Equal(holes, result.Holes);
    }

    [Fact]
    public async Task CreateSoloAsync_WithInvalidHoles_ThrowsValidationError()
    {
        var (service, _, users, courses) = CreateService();
        var host = await AddHost(users, handicap: null, PaceOfPlay.Standard);
        var course = await AddCourse(courses);

        await Assert.ThrowsAsync<DomainValidationException>(() => service.CreateSoloAsync(host.Id, course.Id, 12));
    }

    [Fact]
    public async Task CreateGroupAsync_WithMinAboveMax_ThrowsValidationError()
    {
        var (service, _, users, courses) = CreateService();
        var host = await AddHost(users, handicap: null, PaceOfPlay.Standard);
        var course = await AddCourse(courses);

        await Assert.ThrowsAsync<DomainValidationException>(() =>
            service.CreateGroupAsync(host.Id, MakeGroupRequest(course.Id, min: 20m, max: 10m)));
    }

    [Fact]
    public async Task CreateGroupAsync_AutoDeclinesHostsOwnPendingGuestRequestsElsewhere()
    {
        var joinRequests = new InMemoryJoinRequestRepository();
        var (service, _, users, courses) = CreateServiceWithJoinRequests(joinRequests);
        var host = await AddHost(users, handicap: null, PaceOfPlay.Standard);
        var course = await AddCourse(courses);

        var ownPending = new JoinRequest
        {
            Id = Guid.NewGuid(), TeeTimeId = Guid.NewGuid(), GuestUserId = host.Id, Status = JoinRequestStatus.Pending
        };
        await joinRequests.AddAsync(ownPending);

        await service.CreateGroupAsync(host.Id, MakeGroupRequest(course.Id));

        Assert.Equal(JoinRequestStatus.Declined, (await joinRequests.GetByIdAsync(ownPending.Id))!.Status);
    }

    private static UpdateGroupRequest MakeEditRequest(int holes = 18, int openSpots = 3,
        decimal? min = null, decimal? max = null, PaceOfPlay? pace = null) =>
        new(DateTime.UtcNow.AddDays(2), holes, openSpots, min, max, pace);

    [Fact]
    public async Task EditAsync_ByHost_UpdatesDetails()
    {
        var (service, teeTimes, users, courses) = CreateService();
        var host = await AddHost(users, handicap: null, PaceOfPlay.Standard);
        var course = await AddCourse(courses);
        var created = await service.CreateGroupAsync(host.Id, MakeGroupRequest(course.Id, openSpots: 2));

        var result = await service.EditAsync(created.Id, host.Id, MakeEditRequest(holes: 9, openSpots: 5));

        Assert.Equal(9, result.Holes);
        Assert.Equal(5, result.OpenSpots);
    }

    [Fact]
    public async Task EditAsync_ByNonHost_ThrowsForbidden()
    {
        var (service, _, users, courses) = CreateService();
        var host = await AddHost(users, handicap: null, PaceOfPlay.Standard);
        var course = await AddCourse(courses);
        var created = await service.CreateGroupAsync(host.Id, MakeGroupRequest(course.Id));

        await Assert.ThrowsAsync<ForbiddenException>(() =>
            service.EditAsync(created.Id, Guid.NewGuid(), MakeEditRequest()));
    }

    [Fact]
    public async Task EditAsync_ShrinkingBelowAcceptedGuestCount_ThrowsValidationError()
    {
        var joinRequests = new InMemoryJoinRequestRepository();
        var (service, teeTimes, users, courses) = CreateServiceWithJoinRequests(joinRequests);
        var host = await AddHost(users, handicap: null, PaceOfPlay.Standard);
        var course = await AddCourse(courses);
        var created = await service.CreateGroupAsync(host.Id, MakeGroupRequest(course.Id, openSpots: 3));
        await joinRequests.AddAsync(new JoinRequest
        {
            Id = Guid.NewGuid(), TeeTimeId = created.Id, GuestUserId = Guid.NewGuid(), Status = JoinRequestStatus.Accepted
        });
        await joinRequests.AddAsync(new JoinRequest
        {
            Id = Guid.NewGuid(), TeeTimeId = created.Id, GuestUserId = Guid.NewGuid(), Status = JoinRequestStatus.Accepted
        });

        // 2 guests already accepted — shrinking to 1 open spot must be rejected, not silently
        // drop an accepted guest.
        await Assert.ThrowsAsync<DomainValidationException>(() =>
            service.EditAsync(created.Id, host.Id, MakeEditRequest(openSpots: 1)));
    }

    [Fact]
    public async Task EditAsync_OnCancelledGroup_ThrowsValidationError()
    {
        var (service, _, users, courses) = CreateService();
        var host = await AddHost(users, handicap: null, PaceOfPlay.Standard);
        var course = await AddCourse(courses);
        var created = await service.CreateGroupAsync(host.Id, MakeGroupRequest(course.Id));
        await service.CancelAsync(created.Id, host.Id);

        await Assert.ThrowsAsync<DomainValidationException>(() =>
            service.EditAsync(created.Id, host.Id, MakeEditRequest()));
    }

    [Fact]
    public async Task CancelAsync_ByHost_SetsCancelledAndNotifiesAffectedGuests()
    {
        var joinRequests = new InMemoryJoinRequestRepository();
        var notifications = new InMemoryNotificationRepository();
        var (service, teeTimes, users, courses) = CreateServiceWithJoinRequests(joinRequests, notifications);
        var host = await AddHost(users, handicap: null, PaceOfPlay.Standard);
        var course = await AddCourse(courses);
        var created = await service.CreateGroupAsync(host.Id, MakeGroupRequest(course.Id, openSpots: 2));
        var pendingGuestId = Guid.NewGuid();
        await joinRequests.AddAsync(new JoinRequest
        {
            Id = Guid.NewGuid(), TeeTimeId = created.Id, GuestUserId = pendingGuestId, Status = JoinRequestStatus.Pending
        });

        var result = await service.CancelAsync(created.Id, host.Id);

        Assert.Equal(TeeTimeStatus.Cancelled, result.Status);
        var notification = Assert.Single(await notifications.GetByUserIdAsync(pendingGuestId));
        Assert.Equal(NotificationType.TeeTimeCancelled, notification.Type);
    }

    [Fact]
    public async Task CancelAsync_ByNonHost_ThrowsForbidden()
    {
        var (service, _, users, courses) = CreateService();
        var host = await AddHost(users, handicap: null, PaceOfPlay.Standard);
        var course = await AddCourse(courses);
        var created = await service.CreateGroupAsync(host.Id, MakeGroupRequest(course.Id));

        await Assert.ThrowsAsync<ForbiddenException>(() => service.CancelAsync(created.Id, Guid.NewGuid()));
    }

    [Fact]
    public async Task CancelAsync_AlreadyCancelled_ThrowsValidationError()
    {
        var (service, _, users, courses) = CreateService();
        var host = await AddHost(users, handicap: null, PaceOfPlay.Standard);
        var course = await AddCourse(courses);
        var created = await service.CreateGroupAsync(host.Id, MakeGroupRequest(course.Id));
        await service.CancelAsync(created.Id, host.Id);

        await Assert.ThrowsAsync<DomainValidationException>(() => service.CancelAsync(created.Id, host.Id));
    }

    [Fact]
    public async Task DeleteAsync_ByHostWithNoJoinRequests_RemovesTheGroup()
    {
        var (service, teeTimes, users, courses) = CreateService();
        var host = await AddHost(users, handicap: null, PaceOfPlay.Standard);
        var course = await AddCourse(courses);
        var created = await service.CreateGroupAsync(host.Id, MakeGroupRequest(course.Id));

        await service.DeleteAsync(created.Id, host.Id);

        Assert.Null(await teeTimes.GetByIdAsync(created.Id));
    }

    [Fact]
    public async Task DeleteAsync_ByNonHost_ThrowsForbidden()
    {
        var (service, _, users, courses) = CreateService();
        var host = await AddHost(users, handicap: null, PaceOfPlay.Standard);
        var course = await AddCourse(courses);
        var created = await service.CreateGroupAsync(host.Id, MakeGroupRequest(course.Id));

        await Assert.ThrowsAsync<ForbiddenException>(() => service.DeleteAsync(created.Id, Guid.NewGuid()));
    }

    [Fact]
    public async Task DeleteAsync_WithExistingJoinRequests_ThrowsValidationError()
    {
        var joinRequests = new InMemoryJoinRequestRepository();
        var (service, teeTimes, users, courses) = CreateServiceWithJoinRequests(joinRequests);
        var host = await AddHost(users, handicap: null, PaceOfPlay.Standard);
        var course = await AddCourse(courses);
        var created = await service.CreateGroupAsync(host.Id, MakeGroupRequest(course.Id));
        await joinRequests.AddAsync(new JoinRequest
        {
            Id = Guid.NewGuid(), TeeTimeId = created.Id, GuestUserId = Guid.NewGuid(), Status = JoinRequestStatus.Declined
        });

        await Assert.ThrowsAsync<DomainValidationException>(() => service.DeleteAsync(created.Id, host.Id));
        Assert.NotNull(await teeTimes.GetByIdAsync(created.Id));
    }
}
