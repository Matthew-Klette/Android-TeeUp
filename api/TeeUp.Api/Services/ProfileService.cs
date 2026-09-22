using TeeUp.Api.Common;
using TeeUp.Api.Dtos;
using TeeUp.Api.Repositories;

namespace TeeUp.Api.Services;

public class ProfileService(
    IUserRepository userRepository,
    ICourseRepository courseRepository) : IProfileService
{
    public async Task<UserDto> GetProfileAsync(string firebaseUid)
    {
        var user = await userRepository.GetByFirebaseUidAsync(firebaseUid)
            ?? throw new NotFoundException("Your profile could not be found.");

        return UserDto.From(user);
    }

    public async Task<UserDto> UpdateProfileAsync(
        string firebaseUid,
        UpdateProfileRequest request)
    {
        var user = await userRepository.GetByFirebaseUidAsync(firebaseUid)
            ?? throw new NotFoundException("Your profile could not be found.");

        // Validate the entire request before changing the stored user.
        var displayName = request.DisplayName?.Trim();

        if (displayName is not null &&
            (displayName.Length == 0 || displayName.Length > 100))
        {
            throw new DomainValidationException(
                "Display name must contain between 1 and 100 characters.");
        }

        if (request.ClearHandicapIndex && request.HandicapIndex is not null)
        {
            throw new DomainValidationException(
                "You cannot set and clear your handicap in the same request.");
        }

        if (request.HandicapIndex is decimal handicap &&
            (handicap < 0m || handicap > 54m ||
             decimal.Round(handicap, 1) != handicap))
        {
            throw new DomainValidationException(
                "Handicap must be between 0 and 54, with at most one decimal place.");
        }

        if (request.ClearHomeCourse && request.HomeCourseId is not null)
        {
            throw new DomainValidationException(
                "You cannot set and clear your home course in the same request.");
        }

        if (request.HomeCourseId is Guid courseId &&
            await courseRepository.GetByIdAsync(courseId) is null)
        {
            throw new DomainValidationException(
                "Select an existing home course.");
        }

        if (request.PaceOfPlay is { } pace && !Enum.IsDefined(pace))
        {
            throw new DomainValidationException(
                "Select a valid pace of play.");
        }

        if (request.Language is { } language && !Enum.IsDefined(language))
        {
            throw new DomainValidationException(
                "Select a valid language.");
        }

        if (displayName is not null)
            user.DisplayName = displayName;

        if (request.ClearHandicapIndex)
            user.HandicapIndex = null;
        else if (request.HandicapIndex is not null)
            user.HandicapIndex = request.HandicapIndex;

        if (request.ClearHomeCourse)
            user.HomeCourseId = null;
        else if (request.HomeCourseId is not null)
            user.HomeCourseId = request.HomeCourseId;

        if (request.PaceOfPlay is { } updatedPace)
            user.PaceOfPlay = updatedPace;

        if (request.Language is { } updatedLanguage)
            user.Language = updatedLanguage;

        if (request.ProfileComplete is { } profileComplete)
            user.ProfileComplete = profileComplete;

        // Nullable booleans distinguish "unchanged" from explicitly disabled.
        if (request.JoinRequestNotifications is { } joinNotifications)
            user.JoinRequestNotifications = joinNotifications;

        if (request.TeeTimeReminders is { } teeTimeReminders)
            user.TeeTimeReminders = teeTimeReminders;

        await userRepository.UpdateAsync(user);
        return UserDto.From(user);
    }
}