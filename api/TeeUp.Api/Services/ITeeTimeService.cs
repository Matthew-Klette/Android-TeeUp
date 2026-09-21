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
    /// </summary>
    Task<IReadOnlyList<TeeTimeDto>> GetAllAsync(decimal? maxHandicap = null, PaceOfPlay? pace = null);

    /// <summary>
    /// Creates a tee time for a solo round, dated right now, hosted by and reserved
    /// entirely for <paramref name="hostUserId"/> — no join-request flow needed before
    /// scores can be posted against it (see RoundService's "no posting before it starts" rule).
    /// </summary>
    Task<TeeTimeDto> CreateSoloAsync(Guid hostUserId, Guid courseId);

    /// <summary>
    /// Creates a real group looking for players (EME-311) — a scheduled tee time with a hole
    /// count, a wanted handicap/pace range, and open spots for guests via the existing
    /// join-request flow. The host is automatically the first member.
    /// </summary>
    Task<TeeTimeDto> CreateGroupAsync(Guid hostUserId, CreateGroupRequest request);
}
