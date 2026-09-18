using Microsoft.EntityFrameworkCore;
using TeeUp.Api.Data;
using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public class EfUserRepository(TeeUpDbContext context) : EfRepository<User>(context), IUserRepository
{
    public Task<User?> GetByFirebaseUidAsync(string firebaseUid)
    {
        return Set.FirstOrDefaultAsync(u => u.FirebaseUid == firebaseUid);
    }
}
