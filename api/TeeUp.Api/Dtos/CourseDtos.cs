using TeeUp.Api.Models;

namespace TeeUp.Api.Dtos;

public record CourseDto(Guid Id, string Name, double Latitude, double Longitude, decimal? Rating)
{
    public static CourseDto From(Course course) => new(
        course.Id, course.Name, course.Latitude, course.Longitude, course.Rating);
}

public record CoordinateDto(double Latitude, double Longitude)
{
    public static CoordinateDto From(HoleCoordinate point) => new(point.Latitude, point.Longitude);
}

/// <summary>
/// Everything the Android hole map needs for one hole. <see cref="GreenFront"/>/<see cref="GreenBack"/>
/// are null when the green's outline isn't mapped; <see cref="GreenMiddle"/> is the pin.
/// <see cref="Doglegs"/> are the turns in the line of play, ordered tee to green.
/// </summary>
public record HoleLayoutDto(
    int Hole,
    CoordinateDto Tee,
    CoordinateDto? GreenFront,
    CoordinateDto GreenMiddle,
    CoordinateDto? GreenBack,
    IReadOnlyList<CoordinateDto> Doglegs);
