using TeeUp.Api.Dtos;

namespace TeeUp.Api.Services;

public interface IAuthService
{
    Task<UserDto> RegisterAsync(RegisterRequest request);
}
