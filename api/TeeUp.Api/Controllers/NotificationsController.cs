using Microsoft.AspNetCore.Mvc;
using TeeUp.Api.Common;
using TeeUp.Api.Dtos;
using TeeUp.Api.Repositories;
using TeeUp.Api.Services;

namespace TeeUp.Api.Controllers;

[ApiController]
[Route("api/notifications")]
public class NotificationsController(
    INotificationService notificationService,
    ICurrentUserService currentUser,
    IUserRepository userRepository) : ControllerBase
{
    [HttpGet]
    public async Task<ActionResult<IReadOnlyList<NotificationDto>>> GetMine()
    {
        if (currentUser.FirebaseUid is null)
        {
            throw new NotFoundException("No authenticated user on this request.");
        }

        var user = await userRepository.GetByFirebaseUidAsync(currentUser.FirebaseUid)
            ?? throw new NotFoundException($"No user registered for Firebase UID {currentUser.FirebaseUid}.");

        return Ok(await notificationService.GetForUserAsync(user.Id));
    }
}
