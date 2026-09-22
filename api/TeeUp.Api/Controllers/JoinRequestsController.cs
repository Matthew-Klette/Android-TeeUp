using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using TeeUp.Api.Common;
using TeeUp.Api.Dtos;
using TeeUp.Api.Repositories;
using TeeUp.Api.Services;

namespace TeeUp.Api.Controllers;

[ApiController]
[Route("api/join-requests")]
public class JoinRequestsController(
    IJoinRequestService joinRequestService,
    ICurrentUserService currentUser,
    IUserRepository userRepository) : ControllerBase
{
    [Authorize]
    [HttpPatch("{id:guid}")]
    public async Task<ActionResult<JoinRequestDto>> UpdateStatus(Guid id, UpdateJoinRequestRequest request)
    {
        var callerId = await ResolveCurrentUserIdAsync();
        var joinRequest = await joinRequestService.UpdateStatusAsync(id, request.Status, callerId);
        return Ok(joinRequest);
    }

    private async Task<Guid> ResolveCurrentUserIdAsync()
    {
        if (currentUser.FirebaseUid is null)
        {
            throw new NotFoundException("No authenticated user on this request.");
        }

        var user = await userRepository.GetByFirebaseUidAsync(currentUser.FirebaseUid)
            ?? throw new NotFoundException($"No user registered for Firebase UID {currentUser.FirebaseUid}.");

        return user.Id;
    }
}
