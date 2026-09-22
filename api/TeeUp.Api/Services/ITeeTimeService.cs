using TeeUp.Api.Dtos;
using TeeUp.Api.Models;

namespace TeeUp.Api.Services;

public interface ITeeTimeService
{
    /// <summary>
    /// <paramref name="maxHandicap"/>/<paramref name="pace"/> filter by the tee
    /// time's host, for discovering a group by skill/pace compatibility.
    /// A tee time with no host is excluded whenever either filter is set, since
    /// there's nothing to match against.
    /// <paramref name="joinableOnly"/> narrows to groups a guest could actually
    /// join right now: <see cref="TeeTimeType.OpenRound"/>,
    /// <see cref="TeeTimeStatus.Open"/>, and still in the future. Defaults to false
    /// so callers that look up a specific tee time by id keep seeing
    /// bookings/full/past/cancelled rows too.
    /// </summary>
    Task<IReadOnlyList<TeeTimeDto>> GetAllAsync(decimal? maxHandicap = null, PaceOfPlay? pace = null, bool joinableOnly = false);

    /// <summary>
    /// Creates a tee time for a solo round, dated right now, hosted by and reserved
    /// entirely for <paramref name="hostUserId"/>. No join-request flow needed
    /// before scores can be posted against it.
    /// </summary>
    Task<TeeTimeDto> CreateSoloAsync(Guid hostUserId, Guid courseId);

    /// <summary>
    /// Creates a real group looking for players: a scheduled tee time with a hole
    /// count, a wanted handicap/pace range, and open spots for guests via the existing
    /// join-request flow. The host is automatically the first member.
    /// </summary>
    Task<TeeTimeDto> CreateGroupAsync(Guid hostUserId, CreateGroupRequest request);
}
