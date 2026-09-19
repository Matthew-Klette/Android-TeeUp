using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using TeeUp.Api.Common;
using TeeUp.Api.Dtos;
using TeeUp.Api.Services;

namespace TeeUp.Api.Controllers;

[ApiController]
[Route("api/profiles")]
public class ProfilesController(IProfileService profileService, ICurrentUserService currentUser) : ControllerBase
{
    [Authorize]
    [HttpPatch("me")]
    public async Task<ActionResult<UserDto>> UpdateMe(UpdateProfileRequest request)
    {
        if (currentUser.FirebaseUid is null)
        {
            throw new NotFoundException("No authenticated user on this request.");
        }

        var user = await profileService.UpdateProfileAsync(currentUser.FirebaseUid, request);
        return Ok(user);
    }
}
