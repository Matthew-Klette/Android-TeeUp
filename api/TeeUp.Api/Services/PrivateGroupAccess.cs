using TeeUp.Api.Models;
using TeeUp.Api.Repositories;

namespace TeeUp.Api.Services;

/// <summary>
/// The single rule for who may see or join a partners-only group: the host, or anyone the host has saved as a playing partner.
/// Public groups are visible to everyone. Shared by <see cref="TeeTimeService"/> and <see cref="JoinRequestService"/> so the
/// list, the join-request list and join-request creation can never disagree.
/// </summary>
internal static class PrivateGroupAccess
{
    public static bool CanSee(TeeTime teeTime, Guid? callerUserId, ISet<(Guid OwnerId, Guid PartnerId)> partnerPairs)
    {
        if (!teeTime.IsPrivate)
            return true;
        if (callerUserId is not Guid caller || teeTime.HostUserId is not Guid hostId)
            return false;
        return caller == hostId || partnerPairs.Contains((hostId, caller));
    }

    /// <summary>All saved (owner, partner) pairs, for in-memory visibility checks over a list.</summary>
    public static async Task<ISet<(Guid OwnerId, Guid PartnerId)>> LoadPairsAsync(IFriendshipRepository? friendships)
    {
        if (friendships is null)
            return new HashSet<(Guid, Guid)>();
        return (await friendships.GetAllAsync()).Select(f => (f.UserId, f.FriendUserId)).ToHashSet();
    }
}
