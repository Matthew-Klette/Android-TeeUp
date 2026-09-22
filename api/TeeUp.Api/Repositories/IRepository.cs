namespace TeeUp.Api.Repositories;

public interface IRepository<T>
{
    Task<T?> GetByIdAsync(Guid id);

    /// <summary>
    /// Skips any tracked/cached copy of this entity and re-reads from the database.
    /// Needed inside a concurrency-critical section, since EF's FindAsync (used by
    /// GetByIdAsync) returns an already-tracked entity without re-querying, even if
    /// another DbContext changed the row since. The in-memory test double has no
    /// such cache, so it behaves the same as GetByIdAsync there.
    /// </summary>
    Task<T?> GetByIdFreshAsync(Guid id);

    Task<IReadOnlyList<T>> GetAllAsync();
    Task<T> AddAsync(T entity);
    Task UpdateAsync(T entity);
}
