namespace TeeUp.Api.Models;

/// <summary>
/// A "playing partner": <see cref="UserId"/> has saved <see cref="FriendUserId"/> to their list of golfers they play with.
/// Directional (owner to partner), so adding someone does not add you to their list. A host's partners are the only people
/// who can see or join that host's partners-only groups.
/// </summary>
public class Friendship
{
    public Guid Id { get; set; }

    /// <summary>The owner of the partner list.</summary>
    public Guid UserId { get; set; }

    /// <summary>The saved playing partner.</summary>
    public Guid FriendUserId { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
