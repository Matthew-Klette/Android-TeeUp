using TeeUp.Api.Dtos;
using TeeUp.Api.Repositories;

namespace TeeUp.Api.Services;

public class CourseService(ICourseRepository courseRepository) : ICourseService
{
    public async Task<IReadOnlyList<CourseDto>> GetAllAsync(string? search = null)
    {
        var courses = await courseRepository.GetAllAsync();
        var filtered = string.IsNullOrWhiteSpace(search)
            ? courses
            : courses.Where(c => c.Name.Contains(search, StringComparison.OrdinalIgnoreCase));
        return filtered.Select(CourseDto.From).ToList();
    }
}
