using Microsoft.AspNetCore.Mvc;
using TeeUp.Api.Dtos;
using TeeUp.Api.Services;

namespace TeeUp.Api.Controllers;

[ApiController]
[Route("api/courses")]
public class CoursesController(ICourseService courseService) : ControllerBase
{
    [HttpGet]
    public async Task<ActionResult<IReadOnlyList<CourseDto>>> GetAll()
    {
        return Ok(await courseService.GetAllAsync());
    }
}
