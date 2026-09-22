namespace TeeUp.Api.Repositories;

/// <summary>IUnitOfWork for the in-memory repositories (dev fallback and tests). Snapshots
/// each participant before running the callback and restores all of them if it throws,
/// since the in-memory store has no real database transaction to roll back on its own.</summary>
public class InMemoryUnitOfWork(params ITransactionParticipant[] participants) : IUnitOfWork
{
    public async Task RunInTransactionAsync(Func<Task> action)
    {
        var snapshots = participants.Select(p => p.CreateSnapshot()).ToList();
        try
        {
            await action();
        }
        catch
        {
            for (var i = 0; i < participants.Length; i++)
            {
                participants[i].RestoreSnapshot(snapshots[i]);
            }
            throw;
        }
    }
}
