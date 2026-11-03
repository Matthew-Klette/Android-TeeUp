using TeeUp.Api.Dtos;

namespace TeeUp.Api.Services;

public interface IFriendService
{
    /// <summary>The caller's playing partners, sorted by display name.</summary>
    Task<IReadOnlyList<FriendDto>> GetFriendsAsync(Guid userId);

    /// <summary>Saves a partner. Idempotent: adding an existing partner returns it unchanged. 400 for self, 404 for an unknown user.</summary>
    Task<FriendDto> AddFriendAsync(Guid userId, Guid friendUserId);

    /// <summary>Removes a partner. Idempotent: removing someone who is not a partner is a no-op.</summary>
    Task RemoveFriendAsync(Guid userId, Guid friendUserId);

    /// <summary>
    /// Case-insensitive substring search on display name. The query is trimmed; fewer than 2 characters returns nothing.
    /// Excludes the caller, returns at most 20, sorted by name.
    /// </summary>
    Task<IReadOnlyList<UserSearchResultDto>> SearchUsersAsync(Guid callerId, string? query);
}
