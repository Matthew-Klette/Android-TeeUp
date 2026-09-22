using TeeUp.Api.Dtos;
using TeeUp.Api.Repositories;

namespace TeeUp.Api.Services;

public class CourseService(ICourseRepository courseRepository) : ICourseService
{
    public async Task<IReadOnlyList<CourseDto>> GetAllAsync(string? search = null)
    {
        var courses = await courseRepository.SearchAsync(search);
        return courses.Select(CourseDto.From).ToList();
    }
}
