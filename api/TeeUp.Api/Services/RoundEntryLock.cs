using System.Collections.Concurrent;

namespace TeeUp.Api.Services;

/// <summary>
/// One lock per tee time, so posting or deleting scorecard entries for the same round can't race each other and split a round across two rows or duplicate a hole's entry.
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
