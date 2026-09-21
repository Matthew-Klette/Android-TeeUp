using Microsoft.EntityFrameworkCore;
using TeeUp.Api.Common;
using TeeUp.Api.Data;
using TeeUp.Api.Models;
using TeeUp.Api.Repositories;
using TeeUp.Api.Services;

namespace TeeUp.Api.Tests;

/// <summary>
/// Exercises the EME-313 accept-race fix against real EF Core + PostgreSQL repositories, each
/// racer on its own <see cref="TeeUpDbContext"/> — per the code review, the in-memory fakes used
/// in <see cref="JoinRequestServiceTests"/> complete every call synchronously (no real I/O to
/// yield on), so two calls built as arguments to <c>Task.WhenAll</c> can simply run one after
/// the other before either truly overlaps; that test would pass even with the lock removed.
/// This one forces genuine overlap (a shared gate released only after both racers are already
/// scheduled) and uses separate contexts so each has its own tracked copy of the same rows —
/// the specific setup needed to prove <c>GetByIdFreshAsync</c> actually re-queries the database
/// instead of returning a context-local stale copy via EF's <c>FindAsync</c>.
/// </summary>
public class JoinRequestConcurrencyTests
{
    private static TeeUpDbContext NewContext(string connectionString) =>
        new(new DbContextOptionsBuilder<TeeUpDbContext>().UseNpgsql(connectionString).Options);

    private static JoinRequestService NewService(TeeUpDbContext context) => new(
        new EfJoinRequestRepository(context), new EfTeeTimeRepository(context), new EfNotificationRepository(context));

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

            var setupService = NewService(setup);
            firstRequestId = (await setupService.CreateAsync(teeTimeId, guestAId)).Id;
            secondRequestId = (await setupService.CreateAsync(teeTimeId, guestBId)).Id;
        }

        try
        {
            await using var contextA = NewContext(connectionString);
            await using var contextB = NewContext(connectionString);
            var serviceA = NewService(contextA);
            var serviceB = NewService(contextB);

            // Both racers are already running (on their own thread-pool threads, each with its
            // own DbContext) before the gate opens, so they enter UpdateStatusAsync as close to
            // simultaneously as real scheduling allows — this is what actually exercises the
            // lock, rather than relying on incidental timing.
            var gate = new TaskCompletionSource();

            async Task<bool> TryAccept(JoinRequestService service, Guid joinRequestId)
            {
                await gate.Task;
                try
                {
                    await service.UpdateStatusAsync(joinRequestId, JoinRequestStatus.Accepted, hostId);
                    return true;
                }
                catch (DomainValidationException)
                {
                    return false;
                }
            }

            var taskA = Task.Run(() => TryAccept(serviceA, firstRequestId));
            var taskB = Task.Run(() => TryAccept(serviceB, secondRequestId));
            gate.SetResult();
            var results = await Task.WhenAll(taskA, taskB);

            Assert.Single(results, succeeded => succeeded);

            await using var verify = NewContext(connectionString);
            var finalTeeTime = await verify.TeeTimes.AsNoTracking().FirstAsync(t => t.Id == teeTimeId);
            Assert.Equal(TeeTimeStatus.Full, finalTeeTime.Status);
        }
        finally
        {
            // Two contexts racing with concurrent commands need their own separate connections
            // (a single ambient transaction can't be shared across concurrent commands on one
            // connection), so this test commits real rows to the shared dev database rather
            // than rolling back a wrapping transaction like ProfilePersistenceTests — clean up
            // explicitly instead.
            await using var cleanup = NewContext(connectionString);
            cleanup.RemoveRange(await cleanup.Notifications.Where(n => n.RelatedEntityId == teeTimeId).ToListAsync());
            cleanup.RemoveRange(await cleanup.JoinRequests.Where(j => j.TeeTimeId == teeTimeId).ToListAsync());
            cleanup.RemoveRange(await cleanup.TeeTimes.Where(t => t.Id == teeTimeId).ToListAsync());
            cleanup.RemoveRange(await cleanup.Courses.Where(c => c.Id == courseId).ToListAsync());
            cleanup.RemoveRange(await cleanup.Users.Where(u => u.Id == hostId || u.Id == guestAId || u.Id == guestBId).ToListAsync());
            await cleanup.SaveChangesAsync();
        }
    }
}
