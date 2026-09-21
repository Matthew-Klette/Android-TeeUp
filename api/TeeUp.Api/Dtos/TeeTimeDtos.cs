using TeeUp.Api.Models;

namespace TeeUp.Api.Dtos;

/// <summary>A golfer starting a solo round on their own, with no join-request flow — see RoundsActivity's "Start a Round".</summary>
public record CreateTeeTimeRequest(Guid CourseId);

public record TeeTimeDto(
    Guid Id,
    Guid? HostUserId,
    Guid CourseId,
    DateTime DateTime,
    int OpenSpots,
    decimal Price,
    TeeTimeType Type)
{
    public static TeeTimeDto From(TeeTime teeTime) => new(
        teeTime.Id, teeTime.HostUserId, teeTime.CourseId, teeTime.DateTime,
        teeTime.OpenSpots, teeTime.Price, teeTime.Type);
}
