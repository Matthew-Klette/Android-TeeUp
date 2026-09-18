using TeeUp.Api.Models;

namespace TeeUp.Api.Dtos;

public record CourseDto(Guid Id, string Name, double Latitude, double Longitude, decimal? Rating)
{
    public static CourseDto From(Course course) => new(
        course.Id, course.Name, course.Latitude, course.Longitude, course.Rating);
}
