using TeeUp.Api.Dtos;
using TeeUp.Api.Repositories;

namespace TeeUp.Api.Services;

public class CourseService(ICourseRepository courseRepository) : ICourseService
{
    public async Task<IReadOnlyList<CourseDto>> GetAllAsync()
    {
        var courses = await courseRepository.GetAllAsync();
        return courses.Select(CourseDto.From).ToList();
    }
}
