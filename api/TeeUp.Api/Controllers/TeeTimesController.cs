using Microsoft.AspNetCore.Mvc;
using TeeUp.Api.Dtos;
using TeeUp.Api.Services;

namespace TeeUp.Api.Controllers;

[ApiController]
[Route("api/teetimes")]
public class TeeTimesController(ITeeTimeService teeTimeService, IJoinRequestService joinRequestService)
    : ControllerBase
{
    [HttpGet]
    public async Task<ActionResult<IReadOnlyList<TeeTimeDto>>> GetAll()
    {
        return Ok(await teeTimeService.GetAllAsync());
    }

    [HttpPost("{id:guid}/joinrequests")]
    public async Task<ActionResult<JoinRequestDto>> CreateJoinRequest(Guid id, CreateJoinRequestRequest request)
    {
        var joinRequest = await joinRequestService.CreateAsync(id, request.GuestUserId);
        return Ok(joinRequest);
    }
}
