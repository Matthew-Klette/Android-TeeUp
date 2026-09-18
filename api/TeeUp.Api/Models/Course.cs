namespace TeeUp.Api.Models;

public class Course
{
    public Guid Id { get; set; }
    public required string Name { get; set; }
    public double Latitude { get; set; }
    public double Longitude { get; set; }
    public decimal? Rating { get; set; }
}
