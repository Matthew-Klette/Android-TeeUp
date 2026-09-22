using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public class InMemoryCourseRepository : InMemoryRepository<Course>, ICourseRepository
{
    public InMemoryCourseRepository() : base(c => c.Id)
    {
    }

    public async Task<IReadOnlyList<Course>> SearchAsync(string? search)
    {
        var courses = await GetAllAsync();
        return string.IsNullOrWhiteSpace(search)
            ? courses
            : courses.Where(c => c.Name.Contains(search, StringComparison.OrdinalIgnoreCase)).ToList();
    }
}
