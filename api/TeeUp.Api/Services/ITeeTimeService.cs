using TeeUp.Api.Dtos;
using TeeUp.Api.Models;

namespace TeeUp.Api.Services;

public interface ITeeTimeService
{
    /// <summary>
    /// <paramref name="maxHandicap"/>/<paramref name="pace"/> filter by the tee time's host; a tee time with no host is excluded when either is set.
    /// <paramref name="joinableOnly"/> defaults to false so callers looking up a specific tee time still see bookings, full, past and cancelled rows.
    /// </summary>
    Task<IReadOnlyList<TeeTimeDto>> GetAllAsync(decimal? maxHandicap = null, PaceOfPlay? pace = null, bool joinableOnly = false);

    /// <summary>
    /// Creates a tee time for a solo round, dated right now and reserved entirely for <paramref name="hostUserId"/>, so no join-request flow is needed before scores can be posted.
    /// <paramref name="holes"/> must be 9 or 18 if provided; null defaults to 18.
    /// </summary>
    Task<TeeTimeDto> CreateSoloAsync(Guid hostUserId, Guid courseId, int? holes = null);

    /// <summary>
    /// Creates a scheduled group tee time with open spots for guests; the host is automatically the first member.
    /// Auto-declines (EME-323) any pending join requests <paramref name="hostUserId"/> holds as a guest elsewhere, since they're now hosting their own group.
    /// </summary>
    Task<TeeTimeDto> CreateGroupAsync(Guid hostUserId, CreateGroupRequest request);

    /// <summary>
    /// Edits an existing group (EME-321). Only the host can edit; a cancelled group can't be edited, and OpenSpots can't drop below the number of already-accepted guests.
    /// </summary>
    Task<TeeTimeDto> EditAsync(Guid teeTimeId, Guid hostUserId, UpdateGroupRequest request);

    /// <summary>
    /// Cancels a group (EME-321). Host-only; sets <see cref="TeeTimeStatus.Cancelled"/> and notifies every guest with a pending or accepted join request against it.
    /// </summary>
    Task<TeeTimeDto> CancelAsync(Guid teeTimeId, Guid hostUserId);

    /// <summary>
    /// Hard-deletes a group (EME-321). Host-only, and only while it has zero join requests against it, so a group with history is never orphaned.
    /// </summary>
    Task DeleteAsync(Guid teeTimeId, Guid hostUserId);
}
