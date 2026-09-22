using TeeUp.Api.Common;
using TeeUp.Api.Dtos;
using TeeUp.Api.Models;
using TeeUp.Api.Repositories;
using TeeUp.Api.Services;

namespace TeeUp.Api.Tests;

public class RoundServiceTests
{
    [Fact]
    public async Task ScheduleIncludesUnscoredBookingsAndExcludesPendingDeclinedAndOtherUsers()
    {
        var (service, teeTimes, requests) = CreateService();
        var user = Guid.NewGuid();
        var hosted = MakeTeeTime(DateTime.UtcNow.AddDays(2), user);
        var accepted = MakeTeeTime(DateTime.UtcNow.AddDays(1));
        var past = MakeTeeTime(DateTime.UtcNow.AddDays(-1), user);
        await teeTimes.AddAsync(hosted);
        await teeTimes.AddAsync(accepted);
        await teeTimes.AddAsync(past);
        foreach (var status in new[] { JoinRequestStatus.Pending, JoinRequestStatus.Declined, JoinRequestStatus.Accepted })
        {
            var excluded = MakeTeeTime(DateTime.UtcNow.AddDays(3));
            await teeTimes.AddAsync(excluded);
            await requests.AddAsync(new JoinRequest { Id = Guid.NewGuid(), TeeTimeId = excluded.Id,
                GuestUserId = status == JoinRequestStatus.Accepted ? Guid.NewGuid() : user, Status = status });
        }
        await requests.AddAsync(new JoinRequest { Id = Guid.NewGuid(), TeeTimeId = accepted.Id,
            GuestUserId = user, Status = JoinRequestStatus.Accepted });
        // A host who also has an accepted request must appear only once.
        await requests.AddAsync(new JoinRequest { Id = Guid.NewGuid(), TeeTimeId = hosted.Id,
            GuestUserId = user, Status = JoinRequestStatus.Accepted });
        await service.PostScorecardAsync(past.Id, OneHole(), user);
        var schedule = await service.GetScheduleForUserAsync(user);
        Assert.Equal(new[] { past.Id, accepted.Id, hosted.Id }, schedule.Select(r => r.TeeTimeId));
        Assert.Single(schedule[0].Round!.Scorecard);
        Assert.Null(schedule[1].Round);
        Assert.Null(schedule[2].Round);
    }

    private static (RoundService Service, InMemoryTeeTimeRepository TeeTimes, InMemoryJoinRequestRepository JoinRequests)
        CreateService(InMemoryCourseRepository? courses = null, InMemoryUserRepository? users = null)
    {
        var teeTimes = new InMemoryTeeTimeRepository();
        var joinRequests = new InMemoryJoinRequestRepository();
        var rounds = new InMemoryRoundRepository();
        var scorecardEntries = new InMemoryScorecardEntryRepository();
        return (new RoundService(rounds, scorecardEntries, teeTimes, joinRequests,
            courses ?? new InMemoryCourseRepository(), users ?? new InMemoryUserRepository()), teeTimes, joinRequests);
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
    public async Task GetScheduleForUserAsync_IncludesTheTeeTimesHoleCount()
    {
        var (service, teeTimes, _) = CreateService();
        var hostId = Guid.NewGuid();
        var teeTime = MakeTeeTime(DateTime.UtcNow.AddDays(1), hostId);
        teeTime.Holes = 9;
        await teeTimes.AddAsync(teeTime);

        var schedule = await service.GetScheduleForUserAsync(hostId);

        Assert.Equal(9, Assert.Single(schedule).Holes);
    }

    [Fact]
    public async Task PostScorecardAsync_ForStartedTeeTime_Succeeds()
    {
        var (service, teeTimes, _) = CreateService();
        var teeTime = MakeTeeTime(DateTime.UtcNow.AddHours(-1));
        await teeTimes.AddAsync(teeTime);

        var result = await service.PostScorecardAsync(teeTime.Id, OneHole(), Guid.NewGuid());

        Assert.Single(result.Scorecard);
        Assert.Equal(4, result.Scorecard[0].Strokes);
    }

    [Fact]
    public async Task PostScorecardAsync_BeyondTheTeeTimesHoleCount_ThrowsDomainValidation()
    {
        var (service, teeTimes, _) = CreateService();
        var teeTime = MakeTeeTime(DateTime.UtcNow.AddHours(-1));
        teeTime.Holes = 9;
        await teeTimes.AddAsync(teeTime);

        await Assert.ThrowsAsync<DomainValidationException>(
            () => service.PostScorecardAsync(teeTime.Id, new PostScorecardRequest([new ScorecardEntryRequest(10, 4, 2)]), Guid.NewGuid()));
    }

    [Fact]
    public async Task PostScorecardAsync_WithNoHoleCountSet_AllowsUpToEighteen()
    {
        var (service, teeTimes, _) = CreateService();
        var teeTime = MakeTeeTime(DateTime.UtcNow.AddHours(-1));
        await teeTimes.AddAsync(teeTime);

        var result = await service.PostScorecardAsync(teeTime.Id, new PostScorecardRequest([new ScorecardEntryRequest(18, 4, 2)]), Guid.NewGuid());

        Assert.Single(result.Scorecard);
    }

    [Fact]
    public async Task PostScorecardAsync_ForFutureTeeTime_ThrowsDomainValidation()
    {
        var (service, teeTimes, _) = CreateService();
        var teeTime = MakeTeeTime(DateTime.UtcNow.AddHours(1));
        await teeTimes.AddAsync(teeTime);

        await Assert.ThrowsAsync<DomainValidationException>(
            () => service.PostScorecardAsync(teeTime.Id, OneHole(), Guid.NewGuid()));
    }

    [Fact]
    public async Task PostScorecardAsync_ForUnknownTeeTime_ThrowsNotFound()
    {
        var (service, _, _) = CreateService();

        await Assert.ThrowsAsync<NotFoundException>(
            () => service.PostScorecardAsync(Guid.NewGuid(), OneHole(), Guid.NewGuid()));
    }

    [Fact]
    public async Task PostScorecardAsync_CalledForMultipleHoles_AccumulatesOnSameRound()
    {
        var (service, teeTimes, _) = CreateService();
        var teeTime = MakeTeeTime(DateTime.UtcNow.AddHours(-1));
        await teeTimes.AddAsync(teeTime);

        var first = await service.PostScorecardAsync(teeTime.Id, new PostScorecardRequest([new ScorecardEntryRequest(1, 4, 2)]), Guid.NewGuid());
        var second = await service.PostScorecardAsync(teeTime.Id, new PostScorecardRequest([new ScorecardEntryRequest(2, 5, 2)]), Guid.NewGuid());

        Assert.Equal(first.Id, second.Id);
        Assert.Equal(2, second.Scorecard.Count);
    }

    [Fact]
    public async Task PostScorecardAsync_ResubmittingAHoleAlreadySaved_ReplacesItInPlace()
    {
        var (service, teeTimes, _) = CreateService();
        var teeTime = MakeTeeTime(DateTime.UtcNow.AddHours(-1));
        await teeTimes.AddAsync(teeTime);
        var first = await service.PostScorecardAsync(teeTime.Id, OneHole(strokes: 4, putts: 2), Guid.NewGuid());

        // Two overlapping requests for the same hole (two devices, a retry) must not create a
        // second row that double-counts strokes/putts — the later one replaces the earlier.
        var second = await service.PostScorecardAsync(teeTime.Id, OneHole(strokes: 5, putts: 3), Guid.NewGuid());

        Assert.Equal(first.Id, second.Id);
        var onlyEntry = Assert.Single(second.Scorecard);
        Assert.Equal(5, onlyEntry.Strokes);
        Assert.Equal(3, onlyEntry.Putts);
    }

    [Fact]
    public async Task PostScorecardAsync_AfterDeletingAHole_RepostsItCleanly()
    {
        var (service, teeTimes, _) = CreateService();
        var hostId = Guid.NewGuid();
        var teeTime = MakeTeeTime(DateTime.UtcNow.AddHours(-1), hostId);
        await teeTimes.AddAsync(teeTime);
        var posted = await service.PostScorecardAsync(teeTime.Id, OneHole(strokes: 4, putts: 2), hostId);
        await service.DeleteScorecardEntryAsync(posted.Id, 1, hostId);

        var reposted = await service.PostScorecardAsync(teeTime.Id, OneHole(strokes: 6, putts: 3), hostId);

        var onlyEntry = Assert.Single(reposted.Scorecard);
        Assert.Equal(6, onlyEntry.Strokes);
        Assert.Equal(3, onlyEntry.Putts);
    }

    [Fact]
    public async Task DeleteScorecardEntryAsync_ByHost_RemovesTheEntry()
    {
        var (service, teeTimes, _) = CreateService();
        var hostId = Guid.NewGuid();
        var teeTime = MakeTeeTime(DateTime.UtcNow.AddHours(-1), hostId);
        await teeTimes.AddAsync(teeTime);
        var posted = await service.PostScorecardAsync(teeTime.Id, OneHole(), hostId);

        await service.DeleteScorecardEntryAsync(posted.Id, 1, hostId);

        var refreshed = await service.GetRoundsForUserAsync(hostId);
        Assert.Empty(Assert.Single(refreshed).Scorecard);
    }

    [Fact]
    public async Task DeleteScorecardEntryAsync_ByAcceptedGuest_RemovesTheEntry()
    {
        var (service, teeTimes, joinRequests) = CreateService();
        var teeTime = MakeTeeTime(DateTime.UtcNow.AddHours(-1));
        await teeTimes.AddAsync(teeTime);
        var guestId = Guid.NewGuid();
        await joinRequests.AddAsync(new JoinRequest
        {
            Id = Guid.NewGuid(), TeeTimeId = teeTime.Id, GuestUserId = guestId, Status = JoinRequestStatus.Accepted
        });
        var posted = await service.PostScorecardAsync(teeTime.Id, OneHole(), guestId);

        await service.DeleteScorecardEntryAsync(posted.Id, 1, guestId);

        var refreshed = await service.GetRoundsForUserAsync(guestId);
        Assert.Empty(Assert.Single(refreshed).Scorecard);
    }

    [Fact]
    public async Task DeleteScorecardEntryAsync_ByUnrelatedUser_ThrowsForbidden()
    {
        var (service, teeTimes, _) = CreateService();
        var teeTime = MakeTeeTime(DateTime.UtcNow.AddHours(-1), Guid.NewGuid());
        await teeTimes.AddAsync(teeTime);
        var posted = await service.PostScorecardAsync(teeTime.Id, OneHole(), Guid.NewGuid());

        await Assert.ThrowsAsync<ForbiddenException>(
            () => service.DeleteScorecardEntryAsync(posted.Id, 1, Guid.NewGuid()));
    }

    [Fact]
    public async Task DeleteScorecardEntryAsync_ForUnknownHole_ThrowsNotFound()
    {
        var (service, teeTimes, _) = CreateService();
        var hostId = Guid.NewGuid();
        var teeTime = MakeTeeTime(DateTime.UtcNow.AddHours(-1), hostId);
        await teeTimes.AddAsync(teeTime);
        var posted = await service.PostScorecardAsync(teeTime.Id, OneHole(), hostId);

        await Assert.ThrowsAsync<NotFoundException>(
            () => service.DeleteScorecardEntryAsync(posted.Id, 5, hostId));
    }

    [Fact]
    public async Task DeleteRoundAsync_ByHost_RemovesTheWholeRound()
    {
        var (service, teeTimes, _) = CreateService();
        var hostId = Guid.NewGuid();
        var teeTime = MakeTeeTime(DateTime.UtcNow.AddHours(-1), hostId);
        await teeTimes.AddAsync(teeTime);
        var posted = await service.PostScorecardAsync(teeTime.Id, OneHole(), hostId);

        await service.DeleteRoundAsync(posted.Id, hostId);

        Assert.Empty(await service.GetRoundsForUserAsync(hostId));
    }

    [Fact]
    public async Task DeleteRoundAsync_ByAcceptedGuest_RemovesTheWholeRound()
    {
        var (service, teeTimes, joinRequests) = CreateService();
        var teeTime = MakeTeeTime(DateTime.UtcNow.AddHours(-1));
        await teeTimes.AddAsync(teeTime);
        var guestId = Guid.NewGuid();
        await joinRequests.AddAsync(new JoinRequest
        {
            Id = Guid.NewGuid(), TeeTimeId = teeTime.Id, GuestUserId = guestId, Status = JoinRequestStatus.Accepted
        });
        var posted = await service.PostScorecardAsync(teeTime.Id, OneHole(), guestId);

        await service.DeleteRoundAsync(posted.Id, guestId);

        Assert.Empty(await service.GetRoundsForUserAsync(guestId));
    }

    [Fact]
    public async Task DeleteRoundAsync_ByUnrelatedUser_ThrowsForbidden()
    {
        var (service, teeTimes, _) = CreateService();
        var teeTime = MakeTeeTime(DateTime.UtcNow.AddHours(-1), Guid.NewGuid());
        await teeTimes.AddAsync(teeTime);
        var posted = await service.PostScorecardAsync(teeTime.Id, OneHole(), Guid.NewGuid());

        await Assert.ThrowsAsync<ForbiddenException>(
            () => service.DeleteRoundAsync(posted.Id, Guid.NewGuid()));
    }

    [Fact]
    public async Task DeleteRoundAsync_ForUnknownRound_ThrowsNotFound()
    {
        var (service, _, _) = CreateService();

        await Assert.ThrowsAsync<NotFoundException>(
            () => service.DeleteRoundAsync(Guid.NewGuid(), Guid.NewGuid()));
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

        await service.PostScorecardAsync(hostedTeeTime.Id, OneHole(), hostId);
        await service.PostScorecardAsync(joinedTeeTime.Id, OneHole(), guestId);
        await service.PostScorecardAsync(unrelatedTeeTime.Id, OneHole(), Guid.NewGuid());

        var rounds = await service.GetRoundsForUserAsync(guestId);
        Assert.Single(rounds);
        Assert.Equal(joinedTeeTime.Id, rounds[0].TeeTimeId);

        var hostRounds = await service.GetRoundsForUserAsync(hostId);
        Assert.Single(hostRounds);
        Assert.Equal(hostedTeeTime.Id, hostRounds[0].TeeTimeId);
    }

    // EME-304: total strokes/putts (SUM), average putts (AVG), net score and Stableford,
    // all computed by RoundDto.From from the scorecard rows plus the viewer's handicap
    // and the course's par.
    [Fact]
    public async Task GetRoundsForUserAsync_ComputesNetScoreAndStablefordFromViewerHandicap()
    {
        var courses = new InMemoryCourseRepository();
        var users = new InMemoryUserRepository();
        var (service, teeTimes, _) = CreateService(courses, users);
        var hostId = Guid.NewGuid();
        await users.AddAsync(new User { Id = hostId, FirebaseUid = "uid", DisplayName = "Host", HandicapIndex = 9.4m });
        var course = new Course { Id = Guid.NewGuid(), Name = "Test Links", Par = 72 };
        await courses.AddAsync(course);
        var teeTime = MakeTeeTime(DateTime.UtcNow.AddHours(-1), hostId);
        teeTime.CourseId = course.Id;
        await teeTimes.AddAsync(teeTime);

        await service.PostScorecardAsync(teeTime.Id, new PostScorecardRequest([
            new ScorecardEntryRequest(1, 5, 2),
            new ScorecardEntryRequest(2, 4, 1),
            new ScorecardEntryRequest(3, 6, 3),
        ]), hostId);

        var round = Assert.Single(await service.GetRoundsForUserAsync(hostId));

        Assert.Equal(15, round.TotalStrokes);
        Assert.Equal(6, round.TotalPutts);
        Assert.Equal(2.0, round.AveragePutts);
        // 15 strokes - 9.4 handicap.
        Assert.Equal(5.6m, round.NetScore);
        // Par 72 -> 4 per hole; handicap 9.4 rounds to 9 strokes received, so every one of
        // these 3 holes gets one: net 4/3/5 vs par 4 -> points 2/3/1 -> 6 total.
        Assert.Equal(6, round.StablefordScore);
    }

    [Fact]
    public async Task GetRoundsForUserAsync_WithNoHandicapSet_LeavesNetScoreAndStablefordNull()
    {
        var (service, teeTimes, _) = CreateService();
        var hostId = Guid.NewGuid();
        var teeTime = MakeTeeTime(DateTime.UtcNow.AddHours(-1), hostId);
        await teeTimes.AddAsync(teeTime);

        await service.PostScorecardAsync(teeTime.Id, OneHole(strokes: 5, putts: 2), hostId);

        var round = Assert.Single(await service.GetRoundsForUserAsync(hostId));

        Assert.Equal(5, round.TotalStrokes);
        Assert.Null(round.NetScore);
        Assert.Null(round.StablefordScore);
    }

    // EME-304 review fix: a round can have its scorecard fully cleared (each hole deleted
    // one at a time via DeleteScorecardEntryAsync) while the Round row itself stays. NetScore
    // must go null here too, the same as StablefordScore already does, instead of coming back
    // as 0 minus the handicap.
    [Fact]
    public async Task GetRoundsForUserAsync_WithAllHolesDeleted_LeavesNetScoreNullNotNegativeHandicap()
    {
        var users = new InMemoryUserRepository();
        var (service, teeTimes, _) = CreateService(users: users);
        var hostId = Guid.NewGuid();
        await users.AddAsync(new User { Id = hostId, FirebaseUid = "uid", DisplayName = "Host", HandicapIndex = 9.4m });
        var teeTime = MakeTeeTime(DateTime.UtcNow.AddHours(-1), hostId);
        await teeTimes.AddAsync(teeTime);
        var posted = await service.PostScorecardAsync(teeTime.Id, OneHole(), hostId);

        await service.DeleteScorecardEntryAsync(posted.Id, 1, hostId);

        var round = Assert.Single(await service.GetRoundsForUserAsync(hostId));
        Assert.Empty(round.Scorecard);
        Assert.Null(round.NetScore);
        Assert.Null(round.StablefordScore);
    }
}
