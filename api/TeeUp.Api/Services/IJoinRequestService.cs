using TeeUp.Api.Dtos;
using TeeUp.Api.Models;

namespace TeeUp.Api.Services;

public interface IJoinRequestService
{
    /// <summary>
    /// Throws <see cref="TeeUp.Api.Common.DomainValidationException"/> if the tee time is the
    /// caller's own, is cancelled/in the past, or the caller already has a pending/accepted
    /// request against it (EME-313).
    /// </summary>
    Task<JoinRequestDto> CreateAsync(Guid teeTimeId, Guid guestUserId);

    /// <summary>
    /// <paramref name="callerId"/> must be the tee time's host — anyone else gets
    /// <see cref="TeeUp.Api.Common.ForbiddenException"/> (EME-313). Accepting into a
    /// full/cancelled/past group, or changing an already-decided request, throws
    /// <see cref="TeeUp.Api.Common.DomainValidationException"/>. Two callers racing to accept
    /// the last open spot: exactly one succeeds (see <see cref="TeeTimeAcceptLock"/>).
    /// </summary>
    Task<JoinRequestDto> UpdateStatusAsync(Guid joinRequestId, JoinRequestStatus status, Guid callerId);

    Task<IReadOnlyList<JoinRequestDto>> GetForTeeTimeAsync(Guid teeTimeId);
}
