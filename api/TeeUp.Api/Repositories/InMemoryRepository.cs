using System.Collections.Concurrent;

namespace TeeUp.Api.Repositories;

/// <summary>
/// Temporary in-process store used until the EF Core / PostgreSQL-backed
/// repositories land (EME-290). Implements <see cref="IRepository{T}"/> so
/// services depend only on the abstraction and swapping the backing store
/// later needs no service-layer changes.
/// </summary>
public class InMemoryRepository<T> : IRepository<T> where T : class
{
    private readonly ConcurrentDictionary<Guid, T> _store = new();
    private readonly Func<T, Guid> _idSelector;

    public InMemoryRepository(Func<T, Guid> idSelector)
    {
        _idSelector = idSelector;
    }

    public Task<T?> GetByIdAsync(Guid id)
    {
        _store.TryGetValue(id, out var entity);
        return Task.FromResult(entity);
    }

    // No per-request tracking cache here, so there's nothing to bypass.
    public Task<T?> GetByIdFreshAsync(Guid id) => GetByIdAsync(id);

    public Task<IReadOnlyList<T>> GetAllAsync()
    {
        return Task.FromResult((IReadOnlyList<T>)_store.Values.ToList());
    }

    public Task<T> AddAsync(T entity)
    {
        _store[_idSelector(entity)] = entity;
        return Task.FromResult(entity);
    }

    public Task UpdateAsync(T entity)
    {
        _store[_idSelector(entity)] = entity;
        return Task.CompletedTask;
    }

    public Task DeleteAsync(Guid id)
    {
        _store.TryRemove(id, out _);
        return Task.CompletedTask;
    }
}
