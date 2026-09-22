using Microsoft.AspNetCore.Mvc;
using TeeUp.Api.Dtos;
using TeeUp.Api.Services;

namespace TeeUp.Api.Controllers;

[ApiController]
[Route("api/courses")]
public class CoursesController(ICourseService courseService) : ControllerBase
{
    /// <summary>
    /// <paramref name="search"/> filters by course name (case-insensitive substring match);
    /// omitted or blank returns every course, same as before this filter existed.
    /// </summary>
    [HttpGet]
    public async Task<ActionResult<IReadOnlyList<CourseDto>>> GetAll([FromQuery] string? search)
    {
        return Ok(await courseService.GetAllAsync(search));
    }
}
