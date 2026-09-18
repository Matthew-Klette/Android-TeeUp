namespace TeeUp.Api.Models;

public class ScorecardEntry
{
    public Guid Id { get; set; }
    public Guid RoundId { get; set; }
    public int HoleNumber { get; set; }
    public int Strokes { get; set; }
    public int Putts { get; set; }
    public bool Synced { get; set; }
}
