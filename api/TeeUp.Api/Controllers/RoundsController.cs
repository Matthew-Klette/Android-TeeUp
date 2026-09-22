using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using TeeUp.Api.Common;
using TeeUp.Api.Dtos;
using TeeUp.Api.Repositories;
using TeeUp.Api.Services;

namespace TeeUp.Api.Controllers;

[ApiController]
[Route("api/rounds")]
public class RoundsController(
    IRoundService roundService,
    ICurrentUserService currentUser,
    IUserRepository userRepository) : ControllerBase
{
    [Authorize]
    [HttpGet("me/schedule")]
    public async Task<ActionResult<IReadOnlyList<ScheduledRoundDto>>> GetSchedule()
    {
        var userId = await ResolveCurrentUserIdAsync();
        return Ok(await roundService.GetScheduleForUserAsync(userId));
    }

    [Authorize]
    [HttpPost("{id:guid}/scorecard")]
    public async Task<ActionResult<RoundDto>> PostScorecard(Guid id, PostScorecardRequest request)
    {
        var callerId = await ResolveCurrentUserIdAsync();
        var round = await roundService.PostScorecardAsync(id, request, callerId);
        return Ok(round);
    }

    [Authorize]
    [HttpGet("me")]
    public async Task<ActionResult<IReadOnlyList<RoundDto>>> GetMine()
    {
        var userId = await ResolveCurrentUserIdAsync();
        return Ok(await roundService.GetRoundsForUserAsync(userId));
    }

    [Authorize]
    [HttpDelete("{roundId:guid}/scorecard/{holeNumber:int}")]
    public async Task<IActionResult> DeleteScorecardEntry(Guid roundId, int holeNumber)
    {
        var callerId = await ResolveCurrentUserIdAsync();
        await roundService.DeleteScorecardEntryAsync(roundId, holeNumber, callerId);
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
