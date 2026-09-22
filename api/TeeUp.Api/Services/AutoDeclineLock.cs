using TeeUp.Api.Models;
using TeeUp.Api.Repositories;

namespace TeeUp.Api.Services;

/// <summary>
/// Acquires <see cref="TeeTimeJoinLock"/> for every tee time an EME-323 auto-decline touches:
/// the tee time a guest is being accepted/hosted into, plus every other tee time where that
/// guest currently holds a Pending join request that's about to be auto-declined as a result.
///
/// Reusing TeeTimeJoinLock itself — rather than layering a second, guest-keyed lock on top —
/// is what lets this interleave safely with TeeTimeService.CancelAsync/EditAsync, which only
/// ever take a single TeeTimeJoinLock for their own tee time: there's no second lock type whose
/// acquisition order they'd need to know about, so a cancel/edit and an auto-decline racing on
/// the same tee time now genuinely serialize against each other instead of just missing each
/// other's writes.
///
/// All locks for one call are acquired together, in a fixed ascending-Guid order — never one at
/// a time as each new "other" tee time is discovered — so two of these racing on different tee
/// times for different guests can't deadlock waiting on each other's lock. The set is re-checked
/// immediately after acquiring, in case a new Pending request appeared for this guest while the
/// locks were being acquired; if so, everything is released and retried until the set is stable.
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
