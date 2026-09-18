namespace TeeUp.Api.Models;

public class TeeTime
{
    public Guid Id { get; set; }
    public Guid? HostUserId { get; set; }
    public Guid CourseId { get; set; }
    public DateTime DateTime { get; set; }
    public int OpenSpots { get; set; }
    public decimal Price { get; set; }
    public TeeTimeType Type { get; set; }
}
