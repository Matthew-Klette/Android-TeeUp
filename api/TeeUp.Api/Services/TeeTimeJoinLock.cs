using System.Collections.Concurrent;

namespace TeeUp.Api.Services;

/// <summary>
/// Per-tee-time in-process mutual exclusion for every join-request write against it — create,
/// accept, and decline (EME-313). Serializing all three closes several races at once: two
/// near-simultaneous accepts for the last open spot must not both pass the capacity check
/// before either commits; an accept and a decline racing on the same request must not both
/// pass the "still Pending" check; and two requests from the same guest must not both pass the
/// "no existing request" check before either inserts. This API runs as a single instance (no
/// horizontal scaling in this POE's deployment), so an in-process keyed lock is sufficient — a
/// multi-instance deployment would need a database-level concurrency token or advisory lock
/// instead, since this lock only coordinates callers within one process.
/// Locks are never removed once created; at this app's scale (a handful of tee times per demo
/// run) that's a negligible amount of memory to leave behind, not worth the extra complexity of
/// reference-counted cleanup.
/// </summary>
internal static class TeeTimeJoinLock
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
