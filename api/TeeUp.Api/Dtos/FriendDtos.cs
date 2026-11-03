using TeeUp.Api.Models;

namespace TeeUp.Api.Dtos;

/// <summary>A saved playing partner.</summary>
public record FriendDto(Guid UserId, string DisplayName, decimal? HandicapIndex, PaceOfPlay PaceOfPlay);

/// <summary>Body of POST /api/friends.</summary>
public record AddFriendRequest(Guid FriendUserId);

/// <summary>A golfer found by name search; <paramref name="IsPartner"/> tells the client whether to offer "add" or "remove".</summary>
public record UserSearchResultDto(Guid UserId, string DisplayName, decimal? HandicapIndex, PaceOfPlay PaceOfPlay, bool IsPartner);
