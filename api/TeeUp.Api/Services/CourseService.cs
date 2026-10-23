using TeeUp.Api.Common;
using TeeUp.Api.Dtos;
using TeeUp.Api.Models;
using TeeUp.Api.Repositories;

namespace TeeUp.Api.Services;

public class CourseService(ICourseRepository courseRepository, IHoleCoordinateRepository holeCoordinateRepository)
    : ICourseService
{
    public async Task<IReadOnlyList<CourseDto>> GetAllAsync(string? search = null)
    {
        var courses = await courseRepository.SearchAsync(search);
        return courses.Select(CourseDto.From).ToList();
    }

    public async Task<IReadOnlyList<HoleLayoutDto>> GetHoleLayoutsAsync(Guid courseId)
    {
        _ = await courseRepository.GetByIdAsync(courseId)
            ?? throw new NotFoundException($"Course {courseId} not found.");

        var points = await holeCoordinateRepository.GetByCourseIdAsync(courseId);
        return points
            .GroupBy(p => p.Hole)
            .OrderBy(g => g.Key)
            .Select(g => ToLayout(g.Key, g.ToList()))
            .OfType<HoleLayoutDto>()
            .ToList();
    }

    private static HoleLayoutDto? ToLayout(int hole, IReadOnlyList<HoleCoordinate> points)
    {
        var tee = Find(points, PointOfInterest.FrontTee) ?? Find(points, PointOfInterest.BackTee);
        var middle = Find(points, PointOfInterest.Green, PoiLocation.Middle);
        if (tee is null || middle is null) return null;

        var doglegs = points
            .Where(p => p.Poi == PointOfInterest.Dogleg)
            .OrderBy(p => ApproxSquaredDistance(tee, p))
            .Select(CoordinateDto.From)
            .ToList();

        return new HoleLayoutDto(
            hole,
            CoordinateDto.From(tee),
            Find(points, PointOfInterest.Green, PoiLocation.Front) is { } front ? CoordinateDto.From(front) : null,
            CoordinateDto.From(middle),
            Find(points, PointOfInterest.Green, PoiLocation.Back) is { } back ? CoordinateDto.From(back) : null,
            doglegs);
    }

    private static HoleCoordinate? Find(IEnumerable<HoleCoordinate> points, PointOfInterest poi, PoiLocation? location = null) =>
        points.FirstOrDefault(p => p.Poi == poi && (location is null || p.Location == location));

    /// <summary>Only used to order points along one hole, so a flat-earth approximation (longitude
    /// scaled by cos(latitude)) is plenty; it ranks points the same as true distance at this scale.</summary>
    private static double ApproxSquaredDistance(HoleCoordinate from, HoleCoordinate to)
    {
        var dLat = to.Latitude - from.Latitude;
        var dLng = (to.Longitude - from.Longitude) * Math.Cos(from.Latitude * Math.PI / 180);
        return dLat * dLat + dLng * dLng;
    }
}
