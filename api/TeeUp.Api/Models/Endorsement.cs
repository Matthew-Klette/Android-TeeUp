namespace TeeUp.Api.Models;

public class Endorsement
{
    public Guid Id { get; set; }
    public Guid RoundId { get; set; }
    public Guid FromUserId { get; set; }
    public Guid ToUserId { get; set; }
    public int Skill { get; set; }
    public int Attitude { get; set; }
    public int Etiquette { get; set; }
}
