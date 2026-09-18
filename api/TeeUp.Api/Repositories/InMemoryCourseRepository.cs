using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public class InMemoryCourseRepository : InMemoryRepository<Course>, ICourseRepository
{
    public InMemoryCourseRepository() : base(c => c.Id)
    {
    }
}
