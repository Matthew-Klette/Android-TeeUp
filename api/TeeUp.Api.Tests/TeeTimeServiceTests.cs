using TeeUp.Api.Models;
using TeeUp.Api.Repositories;
using TeeUp.Api.Services;

namespace TeeUp.Api.Tests;

public class TeeTimeServiceTests
{
    private static (TeeTimeService Service, InMemoryTeeTimeRepository TeeTimes, InMemoryUserRepository Users)
        CreateService()
    {
        var teeTimes = new InMemoryTeeTimeRepository();
        var users = new InMemoryUserRepository();
        return (new TeeTimeService(teeTimes, users, new InMemoryCourseRepository()), teeTimes, users);
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
        var (service, teeTimes, users) = CreateService();
        var host = await AddHost(users, handicap: 20m, PaceOfPlay.Relaxed);
        await teeTimes.AddAsync(MakeTeeTime(host.Id));
        await teeTimes.AddAsync(MakeTeeTime(hostUserId: null));

        var result = await service.GetAllAsync();

        Assert.Equal(2, result.Count);
    }

    [Fact]
    public async Task GetAllAsync_WithMaxHandicap_ExcludesHostsAboveThreshold()
    {
        var (service, teeTimes, users) = CreateService();
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
        var (service, teeTimes, users) = CreateService();
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
        var (service, teeTimes, _) = CreateService();
        await teeTimes.AddAsync(MakeTeeTime(hostUserId: null));

        var result = await service.GetAllAsync(maxHandicap: 54m);

        Assert.Empty(result);
    }

    [Fact]
    public async Task GetAllAsync_WithMaxHandicap_ExcludesHostWithNoHandicapSet()
    {
        var (service, teeTimes, users) = CreateService();
        var host = await AddHost(users, handicap: null, PaceOfPlay.Standard);
        await teeTimes.AddAsync(MakeTeeTime(host.Id));

        var result = await service.GetAllAsync(maxHandicap: 54m);

        Assert.Empty(result);
    }
}
