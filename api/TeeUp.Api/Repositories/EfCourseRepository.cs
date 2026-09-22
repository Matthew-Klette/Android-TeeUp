using Microsoft.EntityFrameworkCore;
using TeeUp.Api.Data;
using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public class EfCourseRepository(TeeUpDbContext context) : EfRepository<Course>(context), ICourseRepository
{
    public async Task<IReadOnlyList<Course>> SearchAsync(string? search)
    {
        var query = Set.AsNoTracking();
        if (!string.IsNullOrWhiteSpace(search))
        {
            // ILIKE is Npgsql's case-insensitive LIKE, translated straight to SQL rather than
            // pulling every row into memory to filter with LINQ. LIKE wildcards (%, _) in the
            // search text itself must be escaped, or e.g. typing "%" matches every course
            // instead of none. Backslash escaped first so escaping % / _ doesn't get re-escaped.
            var escaped = search.Replace("\\", "\\\\").Replace("%", "\\%").Replace("_", "\\_");
            query = query.Where(c => EF.Functions.ILike(c.Name, $"%{escaped}%", "\\"));
        }

        return await query.ToListAsync();
    }
}
