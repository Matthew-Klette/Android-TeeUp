using System.Security.Claims;

namespace TeeUp.Api.Common;

public class HttpContextCurrentUserService(IHttpContextAccessor httpContextAccessor) : ICurrentUserService
{
    public string? FirebaseUid =>
        httpContextAccessor.HttpContext?.User.FindFirstValue(ClaimTypes.NameIdentifier)
        ?? httpContextAccessor.HttpContext?.User.FindFirstValue("sub");
}
