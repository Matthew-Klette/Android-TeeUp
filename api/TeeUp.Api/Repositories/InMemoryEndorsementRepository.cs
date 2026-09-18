using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public class InMemoryEndorsementRepository : InMemoryRepository<Endorsement>, IEndorsementRepository
{
    public InMemoryEndorsementRepository() : base(e => e.Id)
    {
    }
}
