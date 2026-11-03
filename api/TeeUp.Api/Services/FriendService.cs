using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Abstractions;
using TeeUp.Api.Common;
using TeeUp.Api.Dtos;
using TeeUp.Api.Models;
using TeeUp.Api.Repositories;

namespace TeeUp.Api.Services;

/// <summary>Manages a golfer's saved playing partners and the user search used to find them.</summary>
public class FriendService(
    IFriendshipRepository friendshipRepository,
    IUserRepository userRepository,
    ILogger<FriendService>? logger = null) : IFriendService
{
    private const int MinQueryLength = 2;
    private const int MaxSearchResults = 20;
    private readonly ILogger log = (ILogger?)logger ?? NullLogger.Instance;

    public async Task<IReadOnlyList<FriendDto>> GetFriendsAsync(Guid userId)
    {
        var friendships = await friendshipRepository.GetByUserIdAsync(userId);
        var usersById = (await userRepository.GetAllAsync()).ToDictionary(u => u.Id);

        return friendships
            .Select(f => usersById.GetValueOrDefault(f.FriendUserId))
            .OfType<User>()
            .OrderBy(u => u.DisplayName, StringComparer.OrdinalIgnoreCase)
            .Select(ToDto)
            .ToList();
    }

    public async Task<FriendDto> AddFriendAsync(Guid userId, Guid friendUserId)
    {
        if (userId == friendUserId)
            throw new DomainValidationException("You can't add yourself as a playing partner.");

        var friend = await userRepository.GetByIdAsync(friendUserId)
            ?? throw new NotFoundException($"User {friendUserId} not found.");

        if (await friendshipRepository.GetAsync(userId, friendUserId) is null)
        {
            await friendshipRepository.AddAsync(new Friendship
            {
                Id = Guid.NewGuid(),
                UserId = userId,
                FriendUserId = friendUserId
            });
            log.LogInformation("User {UserId} added playing partner {FriendUserId}.", userId, friendUserId);
        }
        else
        {
            log.LogDebug("User {UserId} already has playing partner {FriendUserId}; add ignored.", userId, friendUserId);
        }

        return ToDto(friend);
    }

    public async Task RemoveFriendAsync(Guid userId, Guid friendUserId)
    {
        var existing = await friendshipRepository.GetAsync(userId, friendUserId);
        if (existing is null)
        {
            log.LogDebug("User {UserId} has no playing partner {FriendUserId}; remove ignored.", userId, friendUserId);
            return;
        }

        await friendshipRepository.DeleteAsync(existing.Id);
        log.LogInformation("User {UserId} removed playing partner {FriendUserId}.", userId, friendUserId);
    }

    public async Task<IReadOnlyList<UserSearchResultDto>> SearchUsersAsync(Guid callerId, string? query)
    {
        var term = query?.Trim() ?? string.Empty;
        if (term.Length < MinQueryLength)
            return [];

        var partnerIds = (await friendshipRepository.GetByUserIdAsync(callerId))
            .Select(f => f.FriendUserId)
            .ToHashSet();

        return (await userRepository.GetAllAsync())
            .Where(u => u.Id != callerId
                && u.DisplayName.Contains(term, StringComparison.OrdinalIgnoreCase))
            .OrderBy(u => u.DisplayName, StringComparer.OrdinalIgnoreCase)
            .Take(MaxSearchResults)
            .Select(u => new UserSearchResultDto(
                u.Id, u.DisplayName, u.HandicapIndex, u.PaceOfPlay, partnerIds.Contains(u.Id)))
            .ToList();
    }

    private static FriendDto ToDto(User user) =>
        new(user.Id, user.DisplayName, user.HandicapIndex, user.PaceOfPlay);
}
