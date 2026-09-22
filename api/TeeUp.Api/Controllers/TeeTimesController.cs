using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using TeeUp.Api.Common;
using TeeUp.Api.Dtos;
using TeeUp.Api.Models;
using TeeUp.Api.Repositories;
using TeeUp.Api.Services;

namespace TeeUp.Api.Controllers;

[ApiController]
[Route("api/teetimes")]
public class TeeTimesController(
    ITeeTimeService teeTimeService,
    IJoinRequestService joinRequestService,
    ICurrentUserService currentUser,
    IUserRepository userRepository) : ControllerBase
{
    [HttpGet]
    public async Task<ActionResult<IReadOnlyList<TeeTimeDto>>> GetAll(
        [FromQuery] decimal? maxHandicap, [FromQuery] PaceOfPlay? pace, [FromQuery] bool joinableOnly = false)
    {
        return Ok(await teeTimeService.GetAllAsync(maxHandicap, pace, joinableOnly));
    }

    [Authorize]
    [HttpPost]
    public async Task<ActionResult<TeeTimeDto>> CreateSolo(CreateTeeTimeRequest request)
    {
        var hostUserId = await ResolveCurrentUserIdAsync();
        var teeTime = await teeTimeService.CreateSoloAsync(hostUserId, request.CourseId);
        return Ok(teeTime);
    }

    [Authorize]
    [HttpPost("groups")]
    public async Task<ActionResult<TeeTimeDto>> CreateGroup(CreateGroupRequest request)
    {
        var hostUserId = await ResolveCurrentUserIdAsync();
        var group = await teeTimeService.CreateGroupAsync(hostUserId, request);
        return Ok(group);
    }

    [Authorize]
    [HttpPost("{id:guid}/joinrequests")]
    public async Task<ActionResult<JoinRequestDto>> CreateJoinRequest(Guid id)
    {
        var guestUserId = await ResolveCurrentUserIdAsync();
        var joinRequest = await joinRequestService.CreateAsync(id, guestUserId);
        return Ok(joinRequest);
    }

    [HttpGet("{id:guid}/joinrequests")]
    public async Task<ActionResult<IReadOnlyList<JoinRequestDto>>> GetJoinRequests(Guid id)
    {
        return Ok(await joinRequestService.GetForTeeTimeAsync(id));
    }

    [Authorize]
    [HttpPatch("{id:guid}")]
    public async Task<ActionResult<TeeTimeDto>> EditGroup(Guid id, UpdateGroupRequest request)
    {
        var hostUserId = await ResolveCurrentUserIdAsync();
        return Ok(await teeTimeService.EditAsync(id, hostUserId, request));
    }

    [Authorize]
    [HttpPatch("{id:guid}/cancel")]
    public async Task<ActionResult<TeeTimeDto>> CancelGroup(Guid id)
    {
        var hostUserId = await ResolveCurrentUserIdAsync();
        return Ok(await teeTimeService.CancelAsync(id, hostUserId));
    }

    [Authorize]
    [HttpDelete("{id:guid}")]
    public async Task<IActionResult> DeleteGroup(Guid id)
    {
        var hostUserId = await ResolveCurrentUserIdAsync();
        await teeTimeService.DeleteAsync(id, hostUserId);
        return NoContent();
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
