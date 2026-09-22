using Microsoft.EntityFrameworkCore;
using TeeUp.Api.Common;
using TeeUp.Api.Data;
using TeeUp.Api.Models;
using TeeUp.Api.Repositories;
using TeeUp.Api.Services;

namespace TeeUp.Api.Tests;

/// <summary>
/// Tests the concurrency fix against real EF Core and PostgreSQL repositories, each
/// racer on its own <see cref="TeeUpDbContext"/>. A simple gate before two Task.Run
/// calls doesn't prove much, since the scheduler could just run them one after the
/// other anyway. Instead these tests hook the repository call each racer makes right
/// after acquiring <see cref="TeeTimeJoinLock"/> to prove the second racer really is
/// blocked while the first still holds the lock.
/// </summary>
public class JoinRequestConcurrencyTests
{
    private static TeeUpDbContext NewContext(string connectionString) =>
        new(new DbContextOptionsBuilder<TeeUpDbContext>().UseNpgsql(connectionString).Options);

    /// <summary>
    /// Wraps a real <see cref="ITeeTimeRepository"/> so a test can hook the exact moment a
    /// racer calls GetByIdFreshAsync, the first repository call made after acquiring
    /// <see cref="TeeTimeJoinLock"/>. Both accept and decline call it unconditionally,
    /// so it's a reliable point to check whether a racer got past the lock.
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
        return new JoinRequestService(
            new EfJoinRequestRepository(context), teeTimes, new EfNotificationRepository(context), new EfUserRepository(context));
    }

    private static TeeTimeService NewSteppingTeeTimeService(TeeUpDbContext context, out SteppingTeeTimeRepository teeTimes)
    {
        teeTimes = new SteppingTeeTimeRepository(new EfTeeTimeRepository(context));
        return new TeeTimeService(
            teeTimes,
            new EfUserRepository(context),
            new EfCourseRepository(context),
            new EfJoinRequestRepository(context),
            new EfNotificationRepository(context),
            new EfRoundRepository(context),
            new EfUnitOfWork(context));
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
                new EfJoinRequestRepository(setup), new EfTeeTimeRepository(setup), new EfNotificationRepository(setup),
                new EfUserRepository(setup));
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
                new EfJoinRequestRepository(setup), new EfTeeTimeRepository(setup), new EfNotificationRepository(setup),
                new EfUserRepository(setup));
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

    [PostgreSqlFact]
    public async Task CancelAsync_RacingAnAcceptsAutoDeclineOnTheSameTeeTime_WaitsForItToFinish()
    {
        // EME-323's auto-decline writes to another tee time's JoinRequest rows while an accept
        // holds AutoDeclineLock for it, not that other tee time's own TeeTimeJoinLock —
        // CancelAsync/EditAsync only ever take TeeTimeJoinLock and know nothing about
        // AutoDeclineLock. This proves the two still serialize against each other: a Cancel on
        // tee time A can't run while an accept elsewhere is mid-way through auto-declining the
        // same guest's pending request at A, so Cancel never reads a stale Pending status and
        // sends a cancellation notification for a request that's actually already Declined.
        var connectionString = Environment.GetEnvironmentVariable("TEEUP_TEST_DATABASE")!;
        var hostAId = Guid.NewGuid();
        var hostBId = Guid.NewGuid();
        var guestId = Guid.NewGuid();
        var courseId = Guid.NewGuid();
        var teeTimeAId = Guid.NewGuid();
        var teeTimeBId = Guid.NewGuid();
        Guid requestAtAId;
        Guid requestAtBId;

        await using (var setup = NewContext(connectionString))
        {
            setup.Users.Add(new User { Id = hostAId, FirebaseUid = $"race-host-a-{Guid.NewGuid()}", DisplayName = "Race Host A" });
            setup.Users.Add(new User { Id = hostBId, FirebaseUid = $"race-host-b-{Guid.NewGuid()}", DisplayName = "Race Host B" });
            setup.Users.Add(new User { Id = guestId, FirebaseUid = $"race-guest-{Guid.NewGuid()}", DisplayName = "Race Guest" });
            setup.Courses.Add(new Course { Id = courseId, Name = "Race Course" });
            setup.TeeTimes.Add(new TeeTime
            {
                Id = teeTimeAId, HostUserId = hostAId, CourseId = courseId,
                DateTime = DateTime.UtcNow.AddDays(1), OpenSpots = 1, Price = 0, Type = TeeTimeType.OpenRound
            });
            setup.TeeTimes.Add(new TeeTime
            {
                Id = teeTimeBId, HostUserId = hostBId, CourseId = courseId,
                DateTime = DateTime.UtcNow.AddDays(1), OpenSpots = 1, Price = 0, Type = TeeTimeType.OpenRound
            });
            await setup.SaveChangesAsync();

            var setupService = new JoinRequestService(
                new EfJoinRequestRepository(setup), new EfTeeTimeRepository(setup), new EfNotificationRepository(setup),
                new EfUserRepository(setup));
            requestAtAId = (await setupService.CreateAsync(teeTimeAId, guestId)).Id;
            requestAtBId = (await setupService.CreateAsync(teeTimeBId, guestId)).Id;
        }

        try
        {
            await using var joinContext = NewContext(connectionString);
            await using var teeTimeContext = NewContext(connectionString);

            var joinService = NewSteppingService(joinContext, out var joinTeeTimes);
            var teeTimeService = NewSteppingTeeTimeService(teeTimeContext, out var teeTimeTeeTimes);

            var acceptEnteredLock = new TaskCompletionSource();
            var releaseAccept = new TaskCompletionSource();
            joinTeeTimes.BeforeGetByIdFreshAsync = async () =>
            {
                acceptEnteredLock.TrySetResult();
                await releaseAccept.Task;
            };

            var cancelEnteredLock = new TaskCompletionSource();
            teeTimeTeeTimes.BeforeGetByIdFreshAsync = () =>
            {
                cancelEnteredLock.TrySetResult();
                return Task.CompletedTask;
            };

            // Accept the guest's request at B — this should also lock A via AutoDeclineLock, to
            // auto-decline their pending request there.
            var acceptTask = Task.Run(() => joinService.UpdateStatusAsync(requestAtBId, JoinRequestStatus.Accepted, hostBId));
            await acceptEnteredLock.Task;

            // Cancel runs on A while the accept above still holds A's TeeTimeJoinLock.
            var cancelTask = Task.Run(() => teeTimeService.CancelAsync(teeTimeAId, hostAId));
            await Task.WhenAny(cancelEnteredLock.Task, Task.Delay(TimeSpan.FromMilliseconds(300)));
            Assert.False(cancelEnteredLock.Task.IsCompleted,
                "CancelAsync reached its post-lock repository call for tee time A while the " +
                "accept's auto-decline still held TeeTimeJoinLock for A — they aren't serialized.");

            releaseAccept.SetResult();
            await acceptTask;

            await cancelEnteredLock.Task;
            await cancelTask;

            await using var verify = NewContext(connectionString);
            var requestAtAAfter = await verify.JoinRequests.AsNoTracking().FirstAsync(j => j.Id == requestAtAId);
            Assert.Equal(JoinRequestStatus.Declined, requestAtAAfter.Status);

            // Cancel must not have sent a "tee time cancelled" notification for a request that
            // was already auto-declined by the time it actually ran.
            var notificationsForA = await verify.Notifications.CountAsync(n => n.RelatedEntityId == teeTimeAId);
            Assert.Equal(0, notificationsForA);
        }
        finally
        {
            // Both tee times share courseId, and TeeTime -> Course is Restrict, not Cascade, so
            // both tee times (and everything referencing them) must go before the course does —
            // the shared CleanUp helper only handles one tee time (and its own course) at a time.
            await using var cleanup = NewContext(connectionString);
            cleanup.RemoveRange(await cleanup.Notifications
                .Where(n => n.RelatedEntityId == teeTimeAId || n.RelatedEntityId == teeTimeBId).ToListAsync());
            cleanup.RemoveRange(await cleanup.JoinRequests
                .Where(j => j.TeeTimeId == teeTimeAId || j.TeeTimeId == teeTimeBId).ToListAsync());
            cleanup.RemoveRange(await cleanup.TeeTimes
                .Where(t => t.Id == teeTimeAId || t.Id == teeTimeBId).ToListAsync());
            cleanup.RemoveRange(await cleanup.Courses.Where(c => c.Id == courseId).ToListAsync());
            cleanup.RemoveRange(await cleanup.Users
                .Where(u => u.Id == hostAId || u.Id == hostBId || u.Id == guestId).ToListAsync());
            await cleanup.SaveChangesAsync();
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
