using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public class InMemoryTeeTimeRepository : InMemoryRepository<TeeTime>, ITeeTimeRepository
{
    public InMemoryTeeTimeRepository() : base(t => t.Id)
    {
    }
}
