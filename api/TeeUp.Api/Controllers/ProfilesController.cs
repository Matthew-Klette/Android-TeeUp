using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using TeeUp.Api.Common;
using TeeUp.Api.Dtos;
using TeeUp.Api.Services;

namespace TeeUp.Api.Controllers;

[ApiController]
[Authorize]
[Route("api/profiles")]
public class ProfilesController(
    IProfileService profileService,
    ICurrentUserService currentUser) : ControllerBase
{
    [HttpGet("me")]
    public async Task<ActionResult<UserDto>> GetMe()
    {
        var firebaseUid = currentUser.FirebaseUid;

        if (string.IsNullOrWhiteSpace(firebaseUid))
            return Unauthorized();

        return Ok(await profileService.GetProfileAsync(firebaseUid));
    }

    [HttpPatch("me")]
    public async Task<ActionResult<UserDto>> UpdateMe(
        UpdateProfileRequest request)
    {
        var firebaseUid = currentUser.FirebaseUid;

        if (string.IsNullOrWhiteSpace(firebaseUid))
            return Unauthorized();

        return Ok(await profileService.UpdateProfileAsync(firebaseUid, request));
    }
}