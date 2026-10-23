using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public interface IHoleCoordinateRepository : IRepository<HoleCoordinate>
{
    Task<IReadOnlyList<HoleCoordinate>> GetByCourseIdAsync(Guid courseId);
}
