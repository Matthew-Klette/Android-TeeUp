using TeeUp.Api.Dtos;

namespace TeeUp.Api.Services;

public interface IProfileService
{
    Task<UserDto> UpdateProfileAsync(string firebaseUid, UpdateProfileRequest request);
}
