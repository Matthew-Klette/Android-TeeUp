namespace TeeUp.Api.Models;

/// <summary>
/// One mapped point on a course hole (a tee, the front/middle/back of a green, a dogleg...).
/// A hole's map is the set of these sharing a <see cref="CourseId"/> and <see cref="Hole"/>;
/// <see cref="Services.CourseService.GetHoleLayoutsAsync"/> assembles them into a layout.
/// Seeded from OpenStreetMap by infra/osm-holes/import_osm_holes.py.
/// </summary>
public class HoleCoordinate
{
    public Guid Id { get; set; }
    public Guid CourseId { get; set; }
    public int Hole { get; set; }
    public PointOfInterest Poi { get; set; }
    public PoiLocation Location { get; set; }
    public FairwaySide SideFw { get; set; }
    public double Latitude { get; set; }
    public double Longitude { get; set; }
}
