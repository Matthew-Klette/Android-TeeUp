using TeeUp.Api.Dtos;

namespace TeeUp.Api.Services;

public interface ICourseService
{
    /// <summary>
    /// <paramref name="search"/> filters by course name (case-insensitive substring match);
    /// null or blank returns every course.
    /// </summary>
    Task<IReadOnlyList<CourseDto>> GetAllAsync(string? search = null);
}
