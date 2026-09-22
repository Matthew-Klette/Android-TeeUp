using Microsoft.EntityFrameworkCore;
using TeeUp.Api.Data;
using TeeUp.Api.Dtos;
using TeeUp.Api.Models;
using TeeUp.Api.Repositories;
using TeeUp.Api.Services;

namespace TeeUp.Api.Tests;

public class PostgreSqlFactAttribute : FactAttribute
{
    public PostgreSqlFactAttribute()
    {
        if (string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable("TEEUP_TEST_DATABASE")))
            Skip = "Set TEEUP_TEST_DATABASE to a migrated local PostgreSQL connection string.";
    }
}

public class ProfilePersistenceTests
{
    [PostgreSqlFact]
    public async Task ProfileEditsPersistAcrossReloadsAndPartialUpdates()
    {
        var options = new DbContextOptionsBuilder<TeeUpDbContext>()
            .UseNpgsql(Environment.GetEnvironmentVariable("TEEUP_TEST_DATABASE"))
            .Options;
        await using var context = new TeeUpDbContext(options);
        // Roll back all fixture data, including when an assertion fails.
        await using var transaction = await context.Database.BeginTransactionAsync();
        var user = new User
        {
            Id = Guid.NewGuid(),
            FirebaseUid = $"profile-test-{Guid.NewGuid()}",
            DisplayName = "Before"
        };
        context.Users.Add(user);
        await context.SaveChangesAsync();
        var service = new ProfileService(
            new EfUserRepository(context), new EfCourseRepository(context));

        await service.UpdateProfileAsync(user.FirebaseUid,
            new UpdateProfileRequest("Updated Golfer", 14.5m, null, PaceOfPlay.Brisk, null, null,
                JoinRequestNotifications: false, TeeTimeReminders: false));
        context.ChangeTracker.Clear();
        var saved = await service.GetProfileAsync(user.FirebaseUid);
        Assert.Equal("Updated Golfer", saved.DisplayName);
        Assert.Equal(14.5m, saved.HandicapIndex);
        Assert.Equal(PaceOfPlay.Brisk, saved.PaceOfPlay);
        Assert.False(saved.JoinRequestNotifications);
        Assert.False(saved.TeeTimeReminders);

        await service.UpdateProfileAsync(user.FirebaseUid,
            new UpdateProfileRequest("Renamed", null, null, null, null, null, ClearHandicapIndex: true));
        context.ChangeTracker.Clear();
        var reloaded = await service.GetProfileAsync(user.FirebaseUid);
        Assert.Equal("Renamed", reloaded.DisplayName);
        Assert.Null(reloaded.HandicapIndex);
        Assert.Equal(PaceOfPlay.Brisk, reloaded.PaceOfPlay);
        Assert.False(reloaded.JoinRequestNotifications);
        Assert.False(reloaded.TeeTimeReminders);
    }
}
