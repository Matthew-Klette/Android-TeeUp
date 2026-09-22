using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public interface ICourseRepository : IRepository<Course>
{
    /// <summary>
    /// Case-insensitive substring match on course name, filtered at the data source rather
    /// than pulled into memory first; null or blank returns every course.
    /// </summary>
    Task<IReadOnlyList<Course>> SearchAsync(string? search);
}
