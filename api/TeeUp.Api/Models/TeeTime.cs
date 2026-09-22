namespace TeeUp.Api.Models;

public class TeeTime
{
    public Guid Id { get; set; }
    public Guid? HostUserId { get; set; }
    public Guid CourseId { get; set; }
    public DateTime DateTime { get; set; }

    /// <summary>How many guests (not counting the host) the group can accept. See JoinRequestService's accept-limit check.</summary>
    public int OpenSpots { get; set; }
    public decimal Price { get; set; }
    public TeeTimeType Type { get; set; }

    /// <summary>9 or 18. Null for legacy/solo rows created before EME-311.</summary>
    public int? Holes { get; set; }
    public decimal? WantedHandicapMin { get; set; }
    public decimal? WantedHandicapMax { get; set; }
    public PaceOfPlay? WantedPace { get; set; }
    public TeeTimeStatus Status { get; set; } = TeeTimeStatus.Open;
}
