using TeeUp.Api.Dtos;
using TeeUp.Api.Models;
using TeeUp.Api.Repositories;

namespace TeeUp.Api.Services;

public class AuthService(IUserRepository userRepository) : IAuthService
{
    public async Task<UserDto> RegisterAsync(RegisterRequest request)
    {
        var existing = await userRepository.GetByFirebaseUidAsync(request.FirebaseUid);
        if (existing is not null)
        {
            return UserDto.From(existing);
        }

        var user = new User
        {
            Id = Guid.NewGuid(),
            FirebaseUid = request.FirebaseUid,
            DisplayName = request.DisplayName
        };

        await userRepository.AddAsync(user);
        return UserDto.From(user);
    }
}
