using Microsoft.EntityFrameworkCore;
using TeeUp.Api.Common;
using TeeUp.Api.Data;
using TeeUp.Api.Models;
using TeeUp.Api.Repositories;
using TeeUp.Api.Services;

namespace TeeUp.Api.Tests;

/// <summary>
/// Exercises the EME-313 concurrency fix against real EF Core + PostgreSQL repositories, each
/// racer on its own <see cref="TeeUpDbContext"/>. Per the second review round, a
/// <c>TaskCompletionSource</c> "gate" opened right before two <c>Task.Run</c> calls doesn't prove
/// anything: nothing stops the scheduler from running the first call to completion before the
/// second one is even dispatched, so that shape would pass even with the lock removed. These
/// tests instead instrument the repository call each racer makes immediately after acquiring
/// <see cref="TeeTimeJoinLock"/> (<see cref="SteppingTeeTimeRepository"/>) to prove, directly,
/// that the second racer cannot reach that call while the first still holds the lock — not just
/// that it happened not to.
/// </summary>
public class JoinRequestConcurrencyTests
{
    private static TeeUpDbContext NewContext(string connectionString) =>
        new(new DbContextOptionsBuilder<TeeUpDbContext>().UseNpgsql(connectionString).Options);

    /// <summary>
    /// Wraps a real <see cref="ITeeTimeRepository"/> so a test can hook the exact moment a racer
    /// calls <c>GetByIdFreshAsync</c> — the first repository call <c>JoinRequestService</c> makes
    /// on this repository after acquiring <see cref="TeeTimeJoinLock"/>, and one made
    /// unconditionally by both the accept and the decline path before either can early-exit. That
    /// makes it a reliable choke point for proving whether a racer has actually gotten past the
    /// lock, regardless of which status change it's making or how its check eventually resolves.
    /// </summary>
    private sealed class SteppingTeeTimeRepository(ITeeTimeRepository inner) : ITeeTimeRepository
    {
        public Func<Task>? BeforeGetByIdFreshAsync { get; set; }

        public Task<TeeTime?> GetByIdAsync(Guid id) => inner.GetByIdAsync(id);

        public async Task<TeeTime?> GetByIdFreshAsync(Guid id)
        {
            if (BeforeGetByIdFreshAsync is { } hook)
            {
                await hook();
            }

            return await inner.GetByIdFreshAsync(id);
        }

        public Task<IReadOnlyList<TeeTime>> GetAllAsync() => inner.GetAllAsync();
        public Task<TeeTime> AddAsync(TeeTime entity) => inner.AddAsync(entity);
        public Task UpdateAsync(TeeTime entity) => inner.UpdateAsync(entity);
        public Task DeleteAsync(Guid id) => inner.DeleteAsync(id);
    }

    private static JoinRequestService NewSteppingService(TeeUpDbContext context, out SteppingTeeTimeRepository teeTimes)
    {
        teeTimes = new SteppingTeeTimeRepository(new EfTeeTimeRepository(context));
        return new JoinRequestService(new EfJoinRequestRepository(context), teeTimes, new EfNotificationRepository(context));
    }

    private static async Task<bool> TryUpdateStatus(
        JoinRequestService service, Guid joinRequestId, JoinRequestStatus status, Guid hostId)
    {
        try
        {
            await service.UpdateStatusAsync(joinRequestId, status, hostId);
            return true;
        }
        catch (DomainValidationException)
        {
            return false;
        }
    }

    /// <summary>
    /// Builds racer A and B against their own contexts, runs racer A up to (and blocked inside)
    /// the lock, confirms racer B cannot get past the same lock while A holds it, then releases A
    /// and lets B proceed. Returns both outcomes.
    /// </summary>
    private static async Task<(bool ResultA, bool ResultB)> RaceInsideLock(
        string connectionString,
        Func<JoinRequestService, Task<bool>> runA,
        Func<JoinRequestService, Task<bool>> runB)
    {
        await using var contextA = NewContext(connectionString);
        await using var contextB = NewContext(connectionString);

        var serviceA = NewSteppingService(contextA, out var teeTimesA);
        var serviceB = NewSteppingService(contextB, out var teeTimesB);

        var aEnteredLock = new TaskCompletionSource();
        var releaseA = new TaskCompletionSource();
        teeTimesA.BeforeGetByIdFreshAsync = async () =>
        {
            aEnteredLock.TrySetResult();
            await releaseA.Task;
        };

        var bEnteredLock = new TaskCompletionSource();
        teeTimesB.BeforeGetByIdFreshAsync = () =>
        {
            bEnteredLock.TrySetResult();
            return Task.CompletedTask;
        };

        var taskA = Task.Run(() => runA(serviceA));
        await aEnteredLock.Task;

        var taskB = Task.Run(() => runB(serviceB));
        // Give B a real window to run before proving it didn't: with the lock removed, B would
        // reach GetByIdFreshAsync (and complete bEnteredLock) almost immediately, so this
        // assertion would fail reliably rather than depending on incidental scheduling timing.
        await Task.WhenAny(bEnteredLock.Task, Task.Delay(TimeSpan.FromMilliseconds(300)));
        Assert.False(bEnteredLock.Task.IsCompleted,
            "The second racer reached the post-lock repository call while the first still held " +
            "TeeTimeJoinLock — the lock isn't actually serializing these calls.");

        releaseA.SetResult();
        var resultA = await taskA;

        await bEnteredLock.Task;
        var resultB = await taskB;

        return (resultA, resultB);
    }

    [PostgreSqlFact]
    public async Task UpdateStatusAsync_ConcurrentAcceptsForLastOpenSpot_ExactlyOneSucceeds()
    {
        var connectionString = Environment.GetEnvironmentVariable("TEEUP_TEST_DATABASE")!;
        var hostId = Guid.NewGuid();
        var guestAId = Guid.NewGuid();
        var guestBId = Guid.NewGuid();
        var courseId = Guid.NewGuid();
        var teeTimeId = Guid.NewGuid();
        Guid firstRequestId;
        Guid secondRequestId;

        await using (var setup = NewContext(connectionString))
        {
            setup.Users.Add(new User { Id = hostId, FirebaseUid = $"race-host-{Guid.NewGuid()}", DisplayName = "Race Host" });
            setup.Users.Add(new User { Id = guestAId, FirebaseUid = $"race-guest-a-{Guid.NewGuid()}", DisplayName = "Race Guest A" });
            setup.Users.Add(new User { Id = guestBId, FirebaseUid = $"race-guest-b-{Guid.NewGuid()}", DisplayName = "Race Guest B" });
            setup.Courses.Add(new Course { Id = courseId, Name = "Race Course" });
            setup.TeeTimes.Add(new TeeTime
            {
                Id = teeTimeId,
                HostUserId = hostId,
                CourseId = courseId,
                DateTime = DateTime.UtcNow.AddDays(1),
                OpenSpots = 1,
                Price = 0,
                Type = TeeTimeType.OpenRound
            });
            await setup.SaveChangesAsync();

            var setupService = new JoinRequestService(
                new EfJoinRequestRepository(setup), new EfTeeTimeRepository(setup), new EfNotificationRepository(setup));
            firstRequestId = (await setupService.CreateAsync(teeTimeId, guestAId)).Id;
            secondRequestId = (await setupService.CreateAsync(teeTimeId, guestBId)).Id;
        }

        try
        {
            var (resultA, resultB) = await RaceInsideLock(
                connectionString,
                service => TryUpdateStatus(service, firstRequestId, JoinRequestStatus.Accepted, hostId),
                service => TryUpdateStatus(service, secondRequestId, JoinRequestStatus.Accepted, hostId));

            Assert.True(resultA);
            Assert.False(resultB);

            await using var verify = NewContext(connectionString);
            var finalTeeTime = await verify.TeeTimes.AsNoTracking().FirstAsync(t => t.Id == teeTimeId);
            Assert.Equal(TeeTimeStatus.Full, finalTeeTime.Status);

            var notificationCount = await verify.Notifications.CountAsync(n => n.RelatedEntityId == teeTimeId);
            Assert.Equal(1, notificationCount);
        }
        finally
        {
            await CleanUp(connectionString, teeTimeId, courseId, [hostId, guestAId, guestBId]);
        }
    }

    [PostgreSqlFact]
    public async Task UpdateStatusAsync_AcceptAndDeclineRaceOnSameRequest_ExactlyOneSucceeds()
    {
        // The prior round's PostgreSQL test only raced two different requests against each other;
        // per this review round, that doesn't prove the same-request accept/decline race (P1#2)
        // is fixed against real EF tracking, since two different rows were never at risk of the
        // stale-read bug being the same row two decisions were made on. This races Accept and
        // Decline on one shared request.
        var connectionString = Environment.GetEnvironmentVariable("TEEUP_TEST_DATABASE")!;
        var hostId = Guid.NewGuid();
        var guestId = Guid.NewGuid();
        var courseId = Guid.NewGuid();
        var teeTimeId = Guid.NewGuid();
        Guid joinRequestId;

        await using (var setup = NewContext(connectionString))
        {
            setup.Users.Add(new User { Id = hostId, FirebaseUid = $"race-host-{Guid.NewGuid()}", DisplayName = "Race Host" });
            setup.Users.Add(new User { Id = guestId, FirebaseUid = $"race-guest-{Guid.NewGuid()}", DisplayName = "Race Guest" });
            setup.Courses.Add(new Course { Id = courseId, Name = "Race Course" });
            setup.TeeTimes.Add(new TeeTime
            {
                Id = teeTimeId,
                HostUserId = hostId,
                CourseId = courseId,
                DateTime = DateTime.UtcNow.AddDays(1),
                OpenSpots = 1,
                Price = 0,
                Type = TeeTimeType.OpenRound
            });
            await setup.SaveChangesAsync();

            var setupService = new JoinRequestService(
                new EfJoinRequestRepository(setup), new EfTeeTimeRepository(setup), new EfNotificationRepository(setup));
            joinRequestId = (await setupService.CreateAsync(teeTimeId, guestId)).Id;
        }

        try
        {
            var (acceptResult, declineResult) = await RaceInsideLock(
                connectionString,
                service => TryUpdateStatus(service, joinRequestId, JoinRequestStatus.Accepted, hostId),
                service => TryUpdateStatus(service, joinRequestId, JoinRequestStatus.Declined, hostId));

            Assert.True(acceptResult);
            Assert.False(declineResult);

            await using var verify = NewContext(connectionString);
            var finalRequest = await verify.JoinRequests.AsNoTracking().FirstAsync(j => j.Id == joinRequestId);
            Assert.Equal(JoinRequestStatus.Accepted, finalRequest.Status);

            var notificationCount = await verify.Notifications.CountAsync(n => n.RelatedEntityId == teeTimeId);
            Assert.Equal(1, notificationCount);
        }
        finally
        {
            await CleanUp(connectionString, teeTimeId, courseId, [hostId, guestId]);
        }
    }

    private static async Task CleanUp(string connectionString, Guid teeTimeId, Guid courseId, Guid[] userIds)
    {
        await using var cleanup = NewContext(connectionString);
        cleanup.RemoveRange(await cleanup.Notifications.Where(n => n.RelatedEntityId == teeTimeId).ToListAsync());
        cleanup.RemoveRange(await cleanup.JoinRequests.Where(j => j.TeeTimeId == teeTimeId).ToListAsync());
        cleanup.RemoveRange(await cleanup.TeeTimes.Where(t => t.Id == teeTimeId).ToListAsync());
        cleanup.RemoveRange(await cleanup.Courses.Where(c => c.Id == courseId).ToListAsync());
        cleanup.RemoveRange(await cleanup.Users.Where(u => userIds.Contains(u.Id)).ToListAsync());
        await cleanup.SaveChangesAsync();
    }
}
