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

    public async Task DeleteAsync(Guid id)
    {
        var entity = await Set.FindAsync(id);
        if (entity is null)
        {
            return;
        }

        Set.Remove(entity);
        await context.SaveChangesAsync();
    }
}
