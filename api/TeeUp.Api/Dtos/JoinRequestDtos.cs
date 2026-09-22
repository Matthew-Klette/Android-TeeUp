using TeeUp.Api.Models;

namespace TeeUp.Api.Dtos;

public record UpdateJoinRequestRequest(JoinRequestStatus Status);

public record JoinRequestDto(Guid Id, Guid TeeTimeId, Guid GuestUserId, string GuestDisplayName, JoinRequestStatus Status)
{
    public static JoinRequestDto From(JoinRequest joinRequest, string guestDisplayName) => new(
        joinRequest.Id, joinRequest.TeeTimeId, joinRequest.GuestUserId, guestDisplayName, joinRequest.Status);
}
