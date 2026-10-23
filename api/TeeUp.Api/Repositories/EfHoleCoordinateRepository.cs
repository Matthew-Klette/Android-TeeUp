using Microsoft.EntityFrameworkCore;
using TeeUp.Api.Data;
using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public class EfHoleCoordinateRepository(TeeUpDbContext context)
    : EfRepository<HoleCoordinate>(context), IHoleCoordinateRepository
{
    public async Task<IReadOnlyList<HoleCoordinate>> GetByCourseIdAsync(Guid courseId)
    {
        return await Set.AsNoTracking().Where(c => c.CourseId == courseId).ToListAsync();
    }
}
