using TeeUp.Api.Dtos;

namespace TeeUp.Api.Services;

public interface ICourseService
{
    /// <summary>
    /// <paramref name="search"/> filters by course name (case-insensitive substring match);
    /// null or blank returns every course.
    /// </summary>
    Task<IReadOnlyList<CourseDto>> GetAllAsync(string? search = null);

    /// <summary>
    /// The course's mapped holes in hole order, for the live hole map. Holes missing a tee or
    /// a green aren't mappable and are left out; a course with no map data returns an empty list.
    /// Throws <see cref="Common.NotFoundException"/> if the course doesn't exist.
    /// </summary>
    Task<IReadOnlyList<HoleLayoutDto>> GetHoleLayoutsAsync(Guid courseId);
}
