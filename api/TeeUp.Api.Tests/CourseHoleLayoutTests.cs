using TeeUp.Api.Common;
using TeeUp.Api.Dtos;
using TeeUp.Api.Models;
using TeeUp.Api.Repositories;
using TeeUp.Api.Services;

namespace TeeUp.Api.Tests;

public class CourseHoleLayoutTests
{
    private static readonly Guid CourseId = Guid.NewGuid();

    private static async Task<(CourseService Service, InMemoryHoleCoordinateRepository Coordinates)> CreateServiceAsync()
    {
        var courses = new InMemoryCourseRepository();
        await courses.AddAsync(new Course { Id = CourseId, Name = "Fancourt Links", Latitude = -33.9678, Longitude = 22.4157 });
        var coordinates = new InMemoryHoleCoordinateRepository();
        return (new CourseService(courses, coordinates), coordinates);
    }

    private static Task AddPoint(InMemoryHoleCoordinateRepository repository, int hole, PointOfInterest poi,
        PoiLocation location, double latitude, double longitude) =>
        repository.AddAsync(new HoleCoordinate
        {
            Id = Guid.NewGuid(),
            CourseId = CourseId,
            Hole = hole,
            Poi = poi,
            Location = location,
            SideFw = FairwaySide.Center,
            Latitude = latitude,
            Longitude = longitude
        });

    [Fact]
    public async Task GetHoleLayoutsAsync_UnknownCourse_ThrowsNotFound()
    {
        var (service, _) = await CreateServiceAsync();

        await Assert.ThrowsAsync<NotFoundException>(() => service.GetHoleLayoutsAsync(Guid.NewGuid()));
    }

    [Fact]
    public async Task GetHoleLayoutsAsync_CourseWithoutMapData_ReturnsEmpty()
    {
        var (service, _) = await CreateServiceAsync();

        var holes = await service.GetHoleLayoutsAsync(CourseId);

        Assert.Empty(holes);
    }

    [Fact]
    public async Task GetHoleLayoutsAsync_GroupsPointsIntoHolesOrderedByNumber()
    {
        var (service, coordinates) = await CreateServiceAsync();
        await AddPoint(coordinates, 2, PointOfInterest.FrontTee, PoiLocation.Middle, -33.970, 22.408);
        await AddPoint(coordinates, 2, PointOfInterest.Green, PoiLocation.Middle, -33.969, 22.410);
        await AddPoint(coordinates, 1, PointOfInterest.FrontTee, PoiLocation.Middle, -33.968, 22.405);
        await AddPoint(coordinates, 1, PointOfInterest.Green, PoiLocation.Middle, -33.970, 22.407);
        await AddPoint(coordinates, 1, PointOfInterest.Green, PoiLocation.Front, -33.9699, 22.4071);
        await AddPoint(coordinates, 1, PointOfInterest.Green, PoiLocation.Back, -33.9703, 22.4075);

        var holes = await service.GetHoleLayoutsAsync(CourseId);

        Assert.Equal([1, 2], holes.Select(h => h.Hole));
        var first = holes[0];
        Assert.Equal(new CoordinateDto(-33.968, 22.405), first.Tee);
        Assert.Equal(new CoordinateDto(-33.970, 22.407), first.GreenMiddle);
        Assert.Equal(new CoordinateDto(-33.9699, 22.4071), first.GreenFront);
        Assert.Equal(new CoordinateDto(-33.9703, 22.4075), first.GreenBack);
    }

    [Fact]
    public async Task GetHoleLayoutsAsync_GreenWithoutOutline_LeavesFrontAndBackNull()
    {
        var (service, coordinates) = await CreateServiceAsync();
        await AddPoint(coordinates, 4, PointOfInterest.FrontTee, PoiLocation.Middle, -33.960, 22.410);
        await AddPoint(coordinates, 4, PointOfInterest.Green, PoiLocation.Middle, -33.964, 22.412);

        var hole = Assert.Single(await service.GetHoleLayoutsAsync(CourseId));

        Assert.Null(hole.GreenFront);
        Assert.Null(hole.GreenBack);
    }

    [Fact]
    public async Task GetHoleLayoutsAsync_OrdersDoglegsByDistanceFromTee()
    {
        var (service, coordinates) = await CreateServiceAsync();
        await AddPoint(coordinates, 5, PointOfInterest.FrontTee, PoiLocation.Middle, -33.9600, 22.4100);
        await AddPoint(coordinates, 5, PointOfInterest.Green, PoiLocation.Middle, -33.9650, 22.4100);
        // Stored furthest-first on purpose: the result must come back tee-to-green.
        await AddPoint(coordinates, 5, PointOfInterest.Dogleg, PoiLocation.Middle, -33.9630, 22.4105);
        await AddPoint(coordinates, 5, PointOfInterest.Dogleg, PoiLocation.Middle, -33.9615, 22.4102);

        var hole = Assert.Single(await service.GetHoleLayoutsAsync(CourseId));

        Assert.Equal(
            [new CoordinateDto(-33.9615, 22.4102), new CoordinateDto(-33.9630, 22.4105)],
            hole.Doglegs);
    }

    [Fact]
    public async Task GetHoleLayoutsAsync_SkipsHolesMissingTeeOrGreen()
    {
        var (service, coordinates) = await CreateServiceAsync();
        await AddPoint(coordinates, 7, PointOfInterest.FrontTee, PoiLocation.Middle, -33.960, 22.410);
        await AddPoint(coordinates, 8, PointOfInterest.Green, PoiLocation.Middle, -33.964, 22.412);
        await AddPoint(coordinates, 9, PointOfInterest.FrontTee, PoiLocation.Middle, -33.961, 22.411);
        await AddPoint(coordinates, 9, PointOfInterest.Green, PoiLocation.Middle, -33.965, 22.413);

        var holes = await service.GetHoleLayoutsAsync(CourseId);

        Assert.Equal(9, Assert.Single(holes).Hole);
    }

    [Fact]
    public async Task GetHoleLayoutsAsync_BackTeeUsedWhenNoFrontTee()
    {
        var (service, coordinates) = await CreateServiceAsync();
        await AddPoint(coordinates, 3, PointOfInterest.BackTee, PoiLocation.Middle, -33.960, 22.410);
        await AddPoint(coordinates, 3, PointOfInterest.Green, PoiLocation.Middle, -33.964, 22.412);

        var hole = Assert.Single(await service.GetHoleLayoutsAsync(CourseId));

        Assert.Equal(new CoordinateDto(-33.960, 22.410), hole.Tee);
    }

    [Fact]
    public async Task GetHoleLayoutsAsync_IgnoresOtherCoursesPoints()
    {
        var (service, coordinates) = await CreateServiceAsync();
        await coordinates.AddAsync(new HoleCoordinate
        {
            Id = Guid.NewGuid(), CourseId = Guid.NewGuid(), Hole = 1, Poi = PointOfInterest.FrontTee,
            Location = PoiLocation.Middle, SideFw = FairwaySide.Center, Latitude = 1, Longitude = 1
        });
        await coordinates.AddAsync(new HoleCoordinate
        {
            Id = Guid.NewGuid(), CourseId = Guid.NewGuid(), Hole = 1, Poi = PointOfInterest.Green,
            Location = PoiLocation.Middle, SideFw = FairwaySide.Center, Latitude = 2, Longitude = 2
        });

        Assert.Empty(await service.GetHoleLayoutsAsync(CourseId));
    }
}
