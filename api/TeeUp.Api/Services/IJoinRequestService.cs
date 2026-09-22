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
    /// <see cref="TeeTimeJoinLock"/>).
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

    Task<IReadOnlyList<JoinRequestDto>> GetForTeeTimeAsync(Guid teeTimeId);
}
