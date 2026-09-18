using TeeUp.Api.Dtos;

namespace TeeUp.Api.Services;

public interface ICourseService
{
    Task<IReadOnlyList<CourseDto>> GetAllAsync();
}
