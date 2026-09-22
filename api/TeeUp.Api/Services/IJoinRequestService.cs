using TeeUp.Api.Dtos;
using TeeUp.Api.Models;

namespace TeeUp.Api.Services;

public interface IJoinRequestService
{
    /// <summary>
    /// Throws <see cref="TeeUp.Api.Common.DomainValidationException"/> if the tee time is the
    /// caller's own or is cancelled/in the past, or
    /// <see cref="TeeUp.Api.Common.ConflictException"/> (409) if the caller already has a
    /// pending/accepted request against it (EME-313). Two callers requesting at once are
    /// serialized so neither can slip past the duplicate check (see
    /// <see cref="TeeTimeJoinLock"/>).
    /// </summary>
    Task<JoinRequestDto> CreateAsync(Guid teeTimeId, Guid guestUserId);

    /// <summary>
    /// <paramref name="callerId"/> must be the tee time's host — anyone else gets
    /// <see cref="TeeUp.Api.Common.ForbiddenException"/> (EME-313). Accepting into a
    /// full/cancelled/past group, or changing an already-decided request, throws
    /// <see cref="TeeUp.Api.Common.DomainValidationException"/>. Two callers racing to accept
    /// the last open spot, or an accept racing a decline on the same request: exactly one
    /// succeeds (see <see cref="TeeTimeJoinLock"/>).
    /// </summary>
    Task<JoinRequestDto> UpdateStatusAsync(Guid joinRequestId, JoinRequestStatus status, Guid callerId);

    Task<IReadOnlyList<JoinRequestDto>> GetForTeeTimeAsync(Guid teeTimeId);

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
