using TeeUp.Api.Models;

namespace TeeUp.Api.Dtos;

public record CreateJoinRequestRequest(Guid GuestUserId);

public record UpdateJoinRequestRequest(JoinRequestStatus Status);

public record JoinRequestDto(Guid Id, Guid TeeTimeId, Guid GuestUserId, JoinRequestStatus Status)
{
    public static JoinRequestDto From(JoinRequest joinRequest) => new(
        joinRequest.Id, joinRequest.TeeTimeId, joinRequest.GuestUserId, joinRequest.Status);
}
