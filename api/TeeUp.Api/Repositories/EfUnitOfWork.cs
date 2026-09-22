using TeeUp.Api.Data;

namespace TeeUp.Api.Repositories;

public class EfUnitOfWork(TeeUpDbContext context) : IUnitOfWork
{
    public async Task RunInTransactionAsync(Func<Task> action)
    {
        await using var transaction = await context.Database.BeginTransactionAsync();
        try
        {
            await action();
            await transaction.CommitAsync();
        }
        catch
        {
            await transaction.RollbackAsync();
            throw;
        }
    }
}
