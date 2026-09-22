using System.Collections.Concurrent;

namespace TeeUp.Api.Services;

/// <summary>
/// One lock per tee time, so create/accept/decline on the same tee time can't race each other.
/// Stops two accepts both grabbing the last open spot, or an accept and a decline both acting
/// on the same request. Only works within a single API instance, which is fine since this app
/// doesn't scale horizontally. Locks are never removed once created, but that's a small amount
/// of memory for this app's scale.
/// Per-key async lock pattern (MarkCiliaVincenti, 2024; Cleary, 2014).
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

/* References:

MarkCiliaVincenti (2024). AsyncKeyedLock wiki. [online] GitHub. Available at: <https://github.com/MarkCiliaVincenti/AsyncKeyedLock/wiki> [Accessed 21 Sep. 2026].

Cleary, S. (2014). AsyncEx: AsyncLock. [online] GitHub. Available at: <https://github.com/StephenCleary/AsyncEx/wiki/AsyncLock> [Accessed 21 Sep. 2026].

*/
