using TeeUp.Api.Common;
using TeeUp.Api.Dtos;
using TeeUp.Api.Repositories;

namespace TeeUp.Api.Services;

public class ProfileService(IUserRepository userRepository) : IProfileService
{
    public async Task<UserDto> UpdateProfileAsync(string firebaseUid, UpdateProfileRequest request)
    {
        var user = await userRepository.GetByFirebaseUidAsync(firebaseUid)
            ?? throw new NotFoundException($"No user registered for Firebase UID {firebaseUid}.");

        if (request.DisplayName is not null) user.DisplayName = request.DisplayName;
        if (request.HandicapIndex is not null) user.HandicapIndex = request.HandicapIndex;
        if (request.HomeCourseId is not null) user.HomeCourseId = request.HomeCourseId;
        if (request.PaceOfPlay is not null) user.PaceOfPlay = request.PaceOfPlay.Value;
        if (request.Language is not null) user.Language = request.Language.Value;
        if (request.ProfileComplete is not null) user.ProfileComplete = request.ProfileComplete.Value;

        await userRepository.UpdateAsync(user);
        return UserDto.From(user);
    }
}
