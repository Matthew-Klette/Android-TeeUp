using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using TeeUp.Api.Dtos;
using TeeUp.Api.Services;

namespace TeeUp.Api.Controllers;

[ApiController]
[Route("api/join-requests")]
public class JoinRequestsController(IJoinRequestService joinRequestService) : ControllerBase
{
    [Authorize]
    [HttpPatch("{id:guid}")]
    public async Task<ActionResult<JoinRequestDto>> UpdateStatus(Guid id, UpdateJoinRequestRequest request)
    {
        var joinRequest = await joinRequestService.UpdateStatusAsync(id, request.Status);
        return Ok(joinRequest);
    }
}
