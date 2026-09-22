using TeeUp.Api.Models;
using TeeUp.Api.Repositories;

namespace TeeUp.Api.Services;

/// <summary>
/// Acquires <see cref="TeeTimeJoinLock"/> for every tee time an EME-323 auto-decline touches, reusing that same lock type so this interleaves safely with TeeTimeService.CancelAsync/EditAsync instead of missing each other's writes.
/// Locks are acquired together in a fixed ascending-Guid order to avoid deadlocks, and the touched set is re-checked and retried until stable.
/// </summary>
internal static class AutoDeclineLock
{
    public static async Task<IDisposable> AcquireAsync(
        IJoinRequestRepository joinRequestRepository, Guid primaryTeeTimeId, Guid guestUserId)
    {
        while (true)
        {
            var teeTimeIds = await RelevantTeeTimeIdsAsync(joinRequestRepository, primaryTeeTimeId, guestUserId);

            var releasers = new List<IDisposable>(teeTimeIds.Count);
            foreach (var teeTimeId in teeTimeIds)
            {
                releasers.Add(await TeeTimeJoinLock.AcquireAsync(teeTimeId));
            }

            var recheck = await RelevantTeeTimeIdsAsync(joinRequestRepository, primaryTeeTimeId, guestUserId);
            if (recheck.SequenceEqual(teeTimeIds))
            {
                return new CompositeReleaser(releasers);
            }

            foreach (var releaser in releasers)
            {
                releaser.Dispose();
            }
        }
    }

    private static async Task<List<Guid>> RelevantTeeTimeIdsAsync(
        IJoinRequestRepository joinRequestRepository, Guid primaryTeeTimeId, Guid guestUserId)
    {
        var otherPending = (await joinRequestRepository.GetAllAsync())
            .Where(j => j.GuestUserId == guestUserId
                && j.Status == JoinRequestStatus.Pending
                && j.TeeTimeId != primaryTeeTimeId)
            .Select(j => j.TeeTimeId);

        return new[] { primaryTeeTimeId }.Concat(otherPending).Distinct().OrderBy(id => id).ToList();
    }

    private sealed class CompositeReleaser(List<IDisposable> releasers) : IDisposable
    {
        public void Dispose()
        {
            // Reverse order, matching how nested `using` statements would unwind.
            for (var i = releasers.Count - 1; i >= 0; i--)
            {
                releasers[i].Dispose();
            }
        }
    }
}
