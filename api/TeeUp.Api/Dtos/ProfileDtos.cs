using TeeUp.Api.Models;

namespace TeeUp.Api.Dtos;

public record UpdateProfileRequest(
    string? DisplayName,
    decimal? HandicapIndex,
    Guid? HomeCourseId,
    PaceOfPlay? PaceOfPlay,
    Language? Language,
    bool? ProfileComplete);
