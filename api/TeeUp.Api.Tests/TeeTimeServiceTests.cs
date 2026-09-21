using TeeUp.Api.Common;
using TeeUp.Api.Dtos;
using TeeUp.Api.Models;
using TeeUp.Api.Repositories;
using TeeUp.Api.Services;

namespace TeeUp.Api.Tests;

public class TeeTimeServiceTests
{
    private static (TeeTimeService Service, InMemoryTeeTimeRepository TeeTimes, InMemoryUserRepository Users, InMemoryCourseRepository Courses)
        CreateService()
    {
        var teeTimes = new InMemoryTeeTimeRepository();
        var users = new InMemoryUserRepository();
        var courses = new InMemoryCourseRepository();
        return (new TeeTimeService(teeTimes, users, courses, new InMemoryJoinRequestRepository()), teeTimes, users, courses);
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

    private static TeeTime MakeTeeTime(Guid? hostUserId) => new()
    {
        Id = Guid.NewGuid(),
        HostUserId = hostUserId,
        CourseId = Guid.NewGuid(),
        DateTime = DateTime.UtcNow.AddDays(1),
        OpenSpots = 2,
        Price = 0,
        Type = TeeTimeType.OpenRound
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
    public async Task CreateGroupAsync_ForUnknownCourse_ThrowsNotFound()
    {
        var (service, _, users, _) = CreateService();
        var host = await AddHost(users, handicap: null, PaceOfPlay.Standard);

        await Assert.ThrowsAsync<NotFoundException>(() =>
            service.CreateGroupAsync(host.Id, MakeGroupRequest(Guid.NewGuid())));
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
    public async Task CreateGroupAsync_WithMinAboveMax_ThrowsValidationError()
    {
        var (service, _, users, courses) = CreateService();
        var host = await AddHost(users, handicap: null, PaceOfPlay.Standard);
        var course = await AddCourse(courses);

        await Assert.ThrowsAsync<DomainValidationException>(() =>
            service.CreateGroupAsync(host.Id, MakeGroupRequest(course.Id, min: 20m, max: 10m)));
    }
}
