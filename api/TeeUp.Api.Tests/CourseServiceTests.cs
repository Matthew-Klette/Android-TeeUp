using TeeUp.Api.Models;
using TeeUp.Api.Repositories;
using TeeUp.Api.Services;

namespace TeeUp.Api.Tests;

public class CourseServiceTests
{
    private static (CourseService Service, InMemoryCourseRepository Courses) CreateService()
    {
        var courses = new InMemoryCourseRepository();
        return (new CourseService(courses), courses);
    }

    private static Course MakeCourse(string name) => new()
    {
        Id = Guid.NewGuid(),
        Name = name,
        Latitude = 0,
        Longitude = 0
    };

    [Fact]
    public async Task GetAllAsync_WithNoSearch_ReturnsEveryCourse()
    {
        var (service, courses) = CreateService();
        await courses.AddAsync(MakeCourse("Fancourt Links"));
        await courses.AddAsync(MakeCourse("Humewood Golf Club"));

        var result = await service.GetAllAsync();

        Assert.Equal(2, result.Count);
    }

    [Fact]
    public async Task GetAllAsync_WithSearch_FiltersByNameCaseInsensitively()
    {
        var (service, courses) = CreateService();
        await courses.AddAsync(MakeCourse("Fancourt Links"));
        await courses.AddAsync(MakeCourse("Humewood Golf Club"));

        var result = await service.GetAllAsync("humewood");

        var onlyResult = Assert.Single(result);
        Assert.Equal("Humewood Golf Club", onlyResult.Name);
    }

    [Fact]
    public async Task GetAllAsync_WithSearchMatchingNothing_ReturnsEmpty()
    {
        var (service, courses) = CreateService();
        await courses.AddAsync(MakeCourse("Fancourt Links"));

        var result = await service.GetAllAsync("Wanderers");

        Assert.Empty(result);
    }

    [Theory]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData(null)]
    public async Task GetAllAsync_WithBlankSearch_ReturnsEveryCourse(string? search)
    {
        var (service, courses) = CreateService();
        await courses.AddAsync(MakeCourse("Fancourt Links"));
        await courses.AddAsync(MakeCourse("Humewood Golf Club"));

        var result = await service.GetAllAsync(search);

        Assert.Equal(2, result.Count);
    }
}
