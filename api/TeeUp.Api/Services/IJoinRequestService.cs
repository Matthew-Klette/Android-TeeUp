using TeeUp.Api.Dtos;
using TeeUp.Api.Models;

namespace TeeUp.Api.Services;

public interface IJoinRequestService
{
    Task<JoinRequestDto> CreateAsync(Guid teeTimeId, Guid guestUserId);
    Task<JoinRequestDto> UpdateStatusAsync(Guid joinRequestId, JoinRequestStatus status);
}
