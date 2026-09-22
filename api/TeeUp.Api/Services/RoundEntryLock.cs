using System.Collections.Concurrent;

namespace TeeUp.Api.Services;

/// <summary>
/// One lock per tee time, so posting or deleting scorecard entries for the same round can't
/// race each other. Without this, RoundService.PostScorecardAsync's find-or-create of the Round
/// row, and its decision to insert vs. replace a hole's entry, are only safe against sequential
/// calls: two truly concurrent posts for the same tee time could otherwise both see "no round
/// yet" and each create one (splitting that round's holes across two Round rows), or both see
/// "no entry for this hole yet" and each insert one (colliding with the DB's unique
/// (RoundId, HoleNumber) index, or silently duplicating it in a store that doesn't enforce that).
/// </summary>
internal static class RoundEntryLock
{
    private static readonly ConcurrentDictionary<Guid, SemaphoreSlim> Locks = new();

    public static async Task<IDisposable> AcquireAsync(Guid teeTimeId)
    {
        var semaphore = Locks.GetOrAdd(teeTimeId, _ => new SemaphoreSlim(1, 1));
        await semaphore.WaitAsync();
        return new Releaser(semaphore);
    }

    private sealed class Releaser(SemaphoreSlim semaphore) : IDisposable
    {
        public void Dispose() => semaphore.Release();
    }
}
