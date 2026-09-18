namespace TeeUp.Api.Models;

public class User
{
    public Guid Id { get; set; }
    public required string FirebaseUid { get; set; }
    public required string DisplayName { get; set; }
    public decimal? HandicapIndex { get; set; }
    public Guid? HomeCourseId { get; set; }
    public PaceOfPlay PaceOfPlay { get; set; } = PaceOfPlay.Standard;
    public Language Language { get; set; } = Language.En;
}
