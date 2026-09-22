using TeeUp.Api.Models;

namespace TeeUp.Api.Dtos;

// Omitted or null values preserve existing settings.
// Explicit clear flags let users remove optional playing details.
public record UpdateProfileRequest(
    string? DisplayName,
    decimal? HandicapIndex,
    Guid? HomeCourseId,
    PaceOfPlay? PaceOfPlay,
    Language? Language,
    bool? ProfileComplete,
    bool? JoinRequestNotifications = null,
    bool? TeeTimeReminders = null,
    bool ClearHandicapIndex = false,
    bool ClearHomeCourse = false);