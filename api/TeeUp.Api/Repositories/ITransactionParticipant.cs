namespace TeeUp.Api.Repositories;

/// <summary>Lets InMemoryUnitOfWork snapshot and restore an in-memory repository's state,
/// standing in for a real database transaction's rollback where there is no real database.</summary>
public interface ITransactionParticipant
{
    object CreateSnapshot();
    void RestoreSnapshot(object snapshot);
}
