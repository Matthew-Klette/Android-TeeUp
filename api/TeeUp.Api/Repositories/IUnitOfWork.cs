namespace TeeUp.Api.Repositories;

/// <summary>Runs several repository calls as one atomic unit: if the callback throws,
/// none of the changes it made are left in place.</summary>
public interface IUnitOfWork
{
    Task RunInTransactionAsync(Func<Task> action);
}
