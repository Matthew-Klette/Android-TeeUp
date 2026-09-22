using TeeUp.Api.Models;

namespace TeeUp.Api.Dtos;

public record RegisterRequest(string FirebaseUid, string DisplayName);

public record UserDto(
    Guid Id,
    string DisplayName,
    decimal? HandicapIndex,
    Guid? HomeCourseId,
    PaceOfPlay PaceOfPlay,
    Language Language,
    bool ProfileComplete,
    bool JoinRequestNotifications = true,
    bool TeeTimeReminders = true)
{
    public static UserDto From(User user) => new(
        user.Id,
        user.DisplayName,
        user.HandicapIndex,
        user.HomeCourseId,
        user.PaceOfPlay,
        user.Language,
        user.ProfileComplete,
        user.JoinRequestNotifications,
        user.TeeTimeReminders);
}