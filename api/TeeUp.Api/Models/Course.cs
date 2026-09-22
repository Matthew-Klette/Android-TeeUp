namespace TeeUp.Api.Models;

public class Course
{
    public Guid Id { get; set; }
    public required string Name { get; set; }
    public double Latitude { get; set; }
    public double Longitude { get; set; }
    public decimal? Rating { get; set; }

    /// <summary>
    /// Full 18-hole par. No per-hole par/stroke-index data exists in this schema, so
    /// Stableford scoring (RoundDto) spreads this evenly across holes played rather than
    /// using each hole's real par. Defaults to 72 (the most common course par) for rows
    /// that predate this column.
    /// </summary>
    public int Par { get; set; } = 72;
}
