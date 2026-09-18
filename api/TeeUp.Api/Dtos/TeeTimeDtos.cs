using TeeUp.Api.Models;

namespace TeeUp.Api.Dtos;

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
