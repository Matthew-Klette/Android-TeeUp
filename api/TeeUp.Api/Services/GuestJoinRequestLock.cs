using System.Collections.Concurrent;

namespace TeeUp.Api.Services;

/// <summary>
/// One lock per guest user, so an accept that auto-declines that guest's other pending
/// requests (EME-323, in JoinRequestService.UpdateStatusAsync and TeeTimeService.CreateGroupAsync)
/// can't race a concurrent accept/withdraw touching one of those same requests at a different
/// tee time. Neither operation holds the other tee time's <see cref="TeeTimeJoinLock"/> while
/// auto-declining, so without this, two hosts accepting the same guest into different groups at
/// once could both succeed, or a withdraw could race the auto-decline it should have preempted.
/// Must always be acquired before any <see cref="TeeTimeJoinLock"/> in the same call, never
/// after, so the two lock types can't deadlock against each other.
/// </summary>
internal static class GuestJoinRequestLock
{
    private static readonly ConcurrentDictionary<Guid, SemaphoreSlim> Locks = new();

    public static async Task<IDisposable> AcquireAsync(Guid guestUserId)
    {
        var semaphore = Locks.GetOrAdd(guestUserId, _ => new SemaphoreSlim(1, 1));
        await semaphore.WaitAsync();
        return new Releaser(semaphore);
    }

    private sealed class Releaser(SemaphoreSlim semaphore) : IDisposable
    {
        public void Dispose() => semaphore.Release();
    }
}
