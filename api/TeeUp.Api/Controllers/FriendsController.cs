using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using TeeUp.Api.Common;
using TeeUp.Api.Dtos;
using TeeUp.Api.Repositories;
using TeeUp.Api.Services;

namespace TeeUp.Api.Controllers;

/// <summary>Playing partners (saved golfers) and the user search used to find them.</summary>
[ApiController]
[Authorize]
public class FriendsController(
    IFriendService friendService,
    ICurrentUserService currentUser,
    IUserRepository userRepository) : ControllerBase
{
    [HttpGet("api/friends")]
    public async Task<ActionResult<IReadOnlyList<FriendDto>>> GetFriends()
    {
        return Ok(await friendService.GetFriendsAsync(await ResolveCurrentUserIdAsync()));
    }

    [HttpPost("api/friends")]
    public async Task<ActionResult<FriendDto>> AddFriend(AddFriendRequest request)
    {
        return Ok(await friendService.AddFriendAsync(await ResolveCurrentUserIdAsync(), request.FriendUserId));
    }

    [HttpDelete("api/friends/{friendUserId:guid}")]
    public async Task<IActionResult> RemoveFriend(Guid friendUserId)
    {
        await friendService.RemoveFriendAsync(await ResolveCurrentUserIdAsync(), friendUserId);
        return NoContent();
    }

    [HttpGet("api/users/search")]
    public async Task<ActionResult<IReadOnlyList<UserSearchResultDto>>> SearchUsers([FromQuery] string? query)
    {
        return Ok(await friendService.SearchUsersAsync(await ResolveCurrentUserIdAsync(), query));
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
