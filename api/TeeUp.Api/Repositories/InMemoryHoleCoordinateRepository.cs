using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public class InMemoryHoleCoordinateRepository : InMemoryRepository<HoleCoordinate>, IHoleCoordinateRepository
{
    public InMemoryHoleCoordinateRepository() : base(c => c.Id)
    {
    }

    public async Task<IReadOnlyList<HoleCoordinate>> GetByCourseIdAsync(Guid courseId)
    {
        var all = await GetAllAsync();
        return all.Where(c => c.CourseId == courseId).ToList();
    }
}
