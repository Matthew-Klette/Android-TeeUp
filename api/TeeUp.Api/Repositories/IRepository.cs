namespace TeeUp.Api.Repositories;

public interface IRepository<T>
{
    Task<T?> GetByIdAsync(Guid id);

    /// <summary>
    /// Bypasses any per-request tracked/cached copy of this entity and re-reads from the
    /// database. Needed inside a concurrency-critical section (EME-313's join-request
    /// accept/decline/create locks) where a concurrent caller may already have committed a
    /// change that this repository's own earlier <see cref="GetByIdAsync"/> call wouldn't
    /// reflect — EF's <c>FindAsync</c> (used by <c>GetByIdAsync</c>) returns an already-tracked
    /// entity from local memory without re-querying, even if another DbContext changed the row
    /// since. The in-memory test double has no such cache, so it's identical to
    /// <see cref="GetByIdAsync"/> there.
    /// </summary>
    Task<T?> GetByIdFreshAsync(Guid id);

    Task<IReadOnlyList<T>> GetAllAsync();
    Task<T> AddAsync(T entity);
    Task UpdateAsync(T entity);
}
