using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public class InMemoryUserRepository : InMemoryRepository<User>, IUserRepository
{
    public InMemoryUserRepository() : base(u => u.Id)
    {
    }

    public async Task<User?> GetByFirebaseUidAsync(string firebaseUid)
    {
        var all = await GetAllAsync();
        return all.FirstOrDefault(u => u.FirebaseUid == firebaseUid);
    }
}
