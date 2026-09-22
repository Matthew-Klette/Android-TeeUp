using Microsoft.EntityFrameworkCore;
using TeeUp.Api.Data;

namespace TeeUp.Api.Repositories;

public class EfRepository<T>(TeeUpDbContext context) : IRepository<T> where T : class
{
    protected TeeUpDbContext Context => context;
    protected DbSet<T> Set => context.Set<T>();

    public async Task<T?> GetByIdAsync(Guid id)
    {
        return await Set.FindAsync(id);
    }

    // AsNoTracking skips EF's change tracker so we get a fresh read, not a stale
    // cached entity (Microsoft, 2023).
    public async Task<T?> GetByIdFreshAsync(Guid id)
    {
        return await Set.AsNoTracking().FirstOrDefaultAsync(e => EF.Property<Guid>(e, "Id") == id);
    }

    public async Task<IReadOnlyList<T>> GetAllAsync()
    {
        return await Set.AsNoTracking().ToListAsync();
    }

    public async Task<T> AddAsync(T entity)
    {
        Set.Add(entity);
        await context.SaveChangesAsync();
        return entity;
    }

    public async Task UpdateAsync(T entity)
    {
        Set.Update(entity);
        await context.SaveChangesAsync();
    }
}

/* References:

Microsoft (2023). Tracking vs. No-Tracking Queries - EF Core. [online] Microsoft Learn. Available at: <https://learn.microsoft.com/en-us/ef/core/querying/tracking> [Accessed 21 Sep. 2026].

*/
