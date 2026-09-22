using TeeUp.Api.Dtos;
using TeeUp.Api.Models;

namespace TeeUp.Api.Services;

public interface ITeeTimeService
{
    /// <summary>
    /// <paramref name="maxHandicap"/>/<paramref name="pace"/> filter by the tee
    /// time's host, for discovering a group by skill/pace compatibility (EME-299).
    /// A tee time with no host is excluded whenever either filter is set, since
    /// there's nothing to match against.
    /// <paramref name="joinableOnly"/> (EME-312) narrows to groups a guest could
    /// actually join right now — <see cref="TeeTimeType.OpenRound"/>,
    /// <see cref="TeeTimeStatus.Open"/>, and still in the future. Defaults to false
    /// so callers that look up a specific tee time by id (detail screen, refresh
    /// after accept/decline) keep seeing bookings/full/past/cancelled rows too.
    /// </summary>
    Task<IReadOnlyList<TeeTimeDto>> GetAllAsync(decimal? maxHandicap = null, PaceOfPlay? pace = null, bool joinableOnly = false);

    /// <summary>
    /// Creates a tee time for a solo round, dated right now, hosted by and reserved
    /// entirely for <paramref name="hostUserId"/> — no join-request flow needed before
    /// scores can be posted against it (see RoundService's "no posting before it starts" rule).
    /// <paramref name="holes"/> must be 9 or 18 if provided; null defaults to 18.
    /// </summary>
    Task<TeeTimeDto> CreateSoloAsync(Guid hostUserId, Guid courseId, int? holes = null);

    /// <summary>
    /// Creates a real group looking for players (EME-311) — a scheduled tee time with a hole
    /// count, a wanted handicap/pace range, and open spots for guests via the existing
    /// join-request flow. The host is automatically the first member. Auto-declines (EME-323)
    /// any pending join requests <paramref name="hostUserId"/> is holding as a guest elsewhere,
    /// since they're now hosting their own group.
    /// </summary>
    Task<TeeTimeDto> CreateGroupAsync(Guid hostUserId, CreateGroupRequest request);

    /// <summary>
    /// Edits an existing group (EME-321). <paramref name="hostUserId"/> must be the group's host
    /// (<see cref="TeeUp.Api.Common.ForbiddenException"/> otherwise); a cancelled group can't be
    /// edited, and <paramref name="request"/>'s OpenSpots can't drop below the number of already-
    /// accepted guests. Serialized against a concurrent accept/decline/withdraw on the same tee
    /// time via <see cref="TeeTimeJoinLock"/>.
    /// </summary>
    Task<TeeTimeDto> EditAsync(Guid teeTimeId, Guid hostUserId, UpdateGroupRequest request);

    /// <summary>
    /// Cancels a group (EME-321) — host-only, sets <see cref="TeeTimeStatus.Cancelled"/> and
    /// notifies every guest with a pending/accepted join request against it.
    /// </summary>
    Task<TeeTimeDto> CancelAsync(Guid teeTimeId, Guid hostUserId);

    /// <summary>
    /// Hard-deletes a group (EME-321) — host-only, and only while it has zero join requests
    /// against it (of any status); otherwise throws <see cref="TeeUp.Api.Common.DomainValidationException"/>
    /// directing the caller to cancel instead, so a group with history is never orphaned.
    /// </summary>
    Task DeleteAsync(Guid teeTimeId, Guid hostUserId);
}
