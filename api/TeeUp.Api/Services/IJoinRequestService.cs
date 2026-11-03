using TeeUp.Api.Dtos;
using TeeUp.Api.Models;

namespace TeeUp.Api.Services;

public interface IJoinRequestService
{
    /// <summary>
    /// Throws <see cref="TeeUp.Api.Common.DomainValidationException"/> if the tee time is the
    /// caller's own or is cancelled/in the past, or
    /// <see cref="TeeUp.Api.Common.ConflictException"/> (409) if the caller already has a
    /// pending/accepted request against it. Two callers requesting at once are
    /// serialized so neither can slip past the duplicate check (see
    /// <see cref="TeeTimeJoinLock"/>). Throws <see cref="TeeUp.Api.Common.ForbiddenException"/> for a
    /// partners-only group the caller isn't allowed to see. On success the host is notified
    /// (<see cref="TeeUp.Api.Models.NotificationType.JoinRequestReceived"/>).
    /// </summary>
    Task<JoinRequestDto> CreateAsync(Guid teeTimeId, Guid guestUserId);

    /// <summary>
    /// <paramref name="callerId"/> must be the tee time's host, or anyone else gets
    /// <see cref="TeeUp.Api.Common.ForbiddenException"/>. Accepting into a
    /// full/cancelled/past group, or changing an already-decided request, throws
    /// <see cref="TeeUp.Api.Common.DomainValidationException"/>. If two callers race to accept
    /// the last open spot, or an accept races a decline on the same request, exactly one
    /// succeeds (see <see cref="TeeTimeJoinLock"/>).
    /// </summary>
    Task<JoinRequestDto> UpdateStatusAsync(Guid joinRequestId, JoinRequestStatus status, Guid callerId);

    /// <summary>
    /// Join requests for a tee time. For a partners-only group, <paramref name="callerUserId"/> must be the host or one
    /// of the host's playing partners, otherwise <see cref="TeeUp.Api.Common.ForbiddenException"/>.
    /// </summary>
    Task<IReadOnlyList<JoinRequestDto>> GetForTeeTimeAsync(Guid teeTimeId, Guid? callerUserId = null);

    /// <summary>
    /// Withdraws a guest's own pending join request (EME-323). <paramref name="guestUserId"/>
    /// must be the request's own guest (<see cref="TeeUp.Api.Common.ForbiddenException"/>
    /// otherwise), and the request must still be Pending
    /// (<see cref="TeeUp.Api.Common.DomainValidationException"/> otherwise). Hard-deletes rather
    /// than soft-declining, since a withdrawn request has no host decision to keep history of —
    /// unlike a decline, which records the host's choice. Serialized against a concurrent
    /// accept/decline on the same tee time via <see cref="TeeTimeJoinLock"/>.
    /// </summary>
    Task WithdrawAsync(Guid joinRequestId, Guid guestUserId);
}
