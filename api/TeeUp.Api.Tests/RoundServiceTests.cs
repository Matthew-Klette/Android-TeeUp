using TeeUp.Api.Common;
using TeeUp.Api.Dtos;
using TeeUp.Api.Models;
using TeeUp.Api.Repositories;
using TeeUp.Api.Services;

namespace TeeUp.Api.Tests;

public class RoundServiceTests
{
    private static (RoundService Service, InMemoryTeeTimeRepository TeeTimes, InMemoryJoinRequestRepository JoinRequests)
        CreateService()
    {
        var teeTimes = new InMemoryTeeTimeRepository();
        var joinRequests = new InMemoryJoinRequestRepository();
        var rounds = new InMemoryRoundRepository();
        var scorecardEntries = new InMemoryScorecardEntryRepository();
        return (new RoundService(rounds, scorecardEntries, teeTimes, joinRequests), teeTimes, joinRequests);
    }

    private static TeeTime MakeTeeTime(DateTime dateTime, Guid? hostUserId = null) => new()
    {
        Id = Guid.NewGuid(),
        HostUserId = hostUserId,
        CourseId = Guid.NewGuid(),
        DateTime = dateTime,
        OpenSpots = 4,
        Price = 0,
        Type = TeeTimeType.OpenRound
    };

    private static PostScorecardRequest OneHole(int strokes = 4, int putts = 2) =>
        new([new ScorecardEntryRequest(1, strokes, putts)]);

    [Fact]
    public async Task PostScorecardAsync_ForStartedTeeTime_Succeeds()
    {
        var (service, teeTimes, _) = CreateService();
        var teeTime = MakeTeeTime(DateTime.UtcNow.AddHours(-1));
        await teeTimes.AddAsync(teeTime);

        var result = await service.PostScorecardAsync(teeTime.Id, OneHole());

        Assert.Single(result.Scorecard);
        Assert.Equal(4, result.Scorecard[0].Strokes);
    }

    [Fact]
    public async Task PostScorecardAsync_ForFutureTeeTime_ThrowsDomainValidation()
    {
        var (service, teeTimes, _) = CreateService();
        var teeTime = MakeTeeTime(DateTime.UtcNow.AddHours(1));
        await teeTimes.AddAsync(teeTime);

        await Assert.ThrowsAsync<DomainValidationException>(
            () => service.PostScorecardAsync(teeTime.Id, OneHole()));
    }

    [Fact]
    public async Task PostScorecardAsync_ForUnknownTeeTime_ThrowsNotFound()
    {
        var (service, _, _) = CreateService();

        await Assert.ThrowsAsync<NotFoundException>(
            () => service.PostScorecardAsync(Guid.NewGuid(), OneHole()));
    }

    [Fact]
    public async Task PostScorecardAsync_CalledForMultipleHoles_AccumulatesOnSameRound()
    {
        var (service, teeTimes, _) = CreateService();
        var teeTime = MakeTeeTime(DateTime.UtcNow.AddHours(-1));
        await teeTimes.AddAsync(teeTime);

        var first = await service.PostScorecardAsync(teeTime.Id, new PostScorecardRequest([new ScorecardEntryRequest(1, 4, 2)]));
        var second = await service.PostScorecardAsync(teeTime.Id, new PostScorecardRequest([new ScorecardEntryRequest(2, 5, 2)]));

        Assert.Equal(first.Id, second.Id);
        Assert.Equal(2, second.Scorecard.Count);
    }

    [Fact]
    public async Task GetRoundsForUserAsync_IncludesHostedAndAcceptedRounds()
    {
        var (service, teeTimes, joinRequests) = CreateService();
        var hostId = Guid.NewGuid();
        var guestId = Guid.NewGuid();

        var hostedTeeTime = MakeTeeTime(DateTime.UtcNow.AddHours(-2), hostId);
        var joinedTeeTime = MakeTeeTime(DateTime.UtcNow.AddHours(-1));
        var unrelatedTeeTime = MakeTeeTime(DateTime.UtcNow.AddHours(-3));
        await teeTimes.AddAsync(hostedTeeTime);
        await teeTimes.AddAsync(joinedTeeTime);
        await teeTimes.AddAsync(unrelatedTeeTime);

        await joinRequests.AddAsync(new JoinRequest
        {
            Id = Guid.NewGuid(),
            TeeTimeId = joinedTeeTime.Id,
            GuestUserId = guestId,
            Status = JoinRequestStatus.Accepted
        });

        await service.PostScorecardAsync(hostedTeeTime.Id, OneHole());
        await service.PostScorecardAsync(joinedTeeTime.Id, OneHole());
        await service.PostScorecardAsync(unrelatedTeeTime.Id, OneHole());

        var rounds = await service.GetRoundsForUserAsync(guestId);
        Assert.Single(rounds);
        Assert.Equal(joinedTeeTime.Id, rounds[0].TeeTimeId);

        var hostRounds = await service.GetRoundsForUserAsync(hostId);
        Assert.Single(hostRounds);
        Assert.Equal(hostedTeeTime.Id, hostRounds[0].TeeTimeId);
    }
}
