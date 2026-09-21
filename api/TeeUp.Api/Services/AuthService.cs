using TeeUp.Api.Dtos;
using TeeUp.Api.Models;
using TeeUp.Api.Repositories;
using TeeUp.Api.Common;

namespace TeeUp.Api.Services;

public class AuthService(IUserRepository userRepository) : IAuthService
{
    public async Task<UserDto> RegisterAsync(RegisterRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.FirebaseUid) || request.FirebaseUid.Length > 128 ||
            string.IsNullOrWhiteSpace(request.DisplayName) || request.DisplayName.Trim().Length > 100)
            throw new DomainValidationException("A valid sign-in identity and a display name of 1 to 100 characters are required.");
        var existing = await userRepository.GetByFirebaseUidAsync(request.FirebaseUid);
        if (existing is not null)
        {
            return UserDto.From(existing);
        }

        var user = new User
        {
            Id = Guid.NewGuid(),
            FirebaseUid = request.FirebaseUid,
            DisplayName = request.DisplayName.Trim()
        };

        await userRepository.AddAsync(user);
        return UserDto.From(user);
    }
}
