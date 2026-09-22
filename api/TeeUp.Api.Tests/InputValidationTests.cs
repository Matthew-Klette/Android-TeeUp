using TeeUp.Api.Common;
using TeeUp.Api.Dtos;
using TeeUp.Api.Models;
using TeeUp.Api.Repositories;
using TeeUp.Api.Services;

namespace TeeUp.Api.Tests;

public class InputValidationTests
{
    [Theory]
    [InlineData(-1)]
    [InlineData(55)]
    [InlineData(12.25)]
    public async Task InvalidHandicapFilterReturnsValidationError(double handicap)
    {
        var service = new TeeTimeService(new InMemoryTeeTimeRepository(), new InMemoryUserRepository(), new InMemoryCourseRepository(), new InMemoryJoinRequestRepository(), new InMemoryNotificationRepository(), new InMemoryRoundRepository());
        await Assert.ThrowsAsync<DomainValidationException>(() => service.GetAllAsync((decimal)handicap));
    }

    [Fact]
    public async Task InvalidPaceReturnsValidationError()
    {
        var service = new TeeTimeService(new InMemoryTeeTimeRepository(), new InMemoryUserRepository(), new InMemoryCourseRepository(), new InMemoryJoinRequestRepository(), new InMemoryNotificationRepository(), new InMemoryRoundRepository());
        await Assert.ThrowsAsync<DomainValidationException>(() => service.GetAllAsync(pace: (PaceOfPlay)99));
    }

    [Theory]
    [InlineData(0, 4, 2)]
    [InlineData(19, 4, 2)]
    [InlineData(1, 0, 0)]
    [InlineData(1, 4, -1)]
    [InlineData(1, 4, 5)]
    public async Task InvalidScorecardDoesNotCreateRound(int hole, int strokes, int putts)
    {
        var rounds = new InMemoryRoundRepository();
        var scores = new InMemoryScorecardEntryRepository();
        var service = new RoundService(rounds, scores, new InMemoryTeeTimeRepository(), new InMemoryJoinRequestRepository(),
            new InMemoryCourseRepository(), new InMemoryUserRepository());
        await Assert.ThrowsAsync<DomainValidationException>(() => service.PostScorecardAsync(Guid.NewGuid(),
            new PostScorecardRequest([new ScorecardEntryRequest(hole, strokes, putts)]), Guid.NewGuid()));
        Assert.Empty(await rounds.GetAllAsync());
        Assert.Empty(await scores.GetAllAsync());
    }

    [Fact]
    public async Task DuplicateAndEmptyHolesAreRejectedBeforeAnyWrites()
    {
        var rounds = new InMemoryRoundRepository();
        var service = new RoundService(rounds, new InMemoryScorecardEntryRepository(),
            new InMemoryTeeTimeRepository(), new InMemoryJoinRequestRepository(),
            new InMemoryCourseRepository(), new InMemoryUserRepository());
        foreach (var entries in new IReadOnlyList<ScorecardEntryRequest>[] {
            Array.Empty<ScorecardEntryRequest>(), [new(1, 4, 2), new(1, 5, 2)] })
            await Assert.ThrowsAsync<DomainValidationException>(() => service.PostScorecardAsync(Guid.NewGuid(), new(entries), Guid.NewGuid()));
        Assert.Empty(await rounds.GetAllAsync());
    }

    [Theory]
    [InlineData("", "Golfer")]
    [InlineData("uid", "   ")]
    public async Task EmptyRegistrationFieldsAreRejected(string uid, string name)
    {
        var users = new InMemoryUserRepository();
        await Assert.ThrowsAsync<DomainValidationException>(() => new AuthService(users).RegisterAsync(new(uid, name)));
        Assert.Empty(await users.GetAllAsync());
    }
}
