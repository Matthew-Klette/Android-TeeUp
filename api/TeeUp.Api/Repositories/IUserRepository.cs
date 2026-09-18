using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public interface IUserRepository : IRepository<User>
{
    Task<User?> GetByFirebaseUidAsync(string firebaseUid);
}
