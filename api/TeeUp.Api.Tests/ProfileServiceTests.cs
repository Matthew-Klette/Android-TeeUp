using TeeUp.Api.Common;
using TeeUp.Api.Dtos;
using TeeUp.Api.Models;
using TeeUp.Api.Repositories;
using TeeUp.Api.Services;

namespace TeeUp.Api.Tests;

public class ProfileServiceTests
{
    private static (ProfileService Service, InMemoryUserRepository Users) CreateService()
    {
        var users = new InMemoryUserRepository();
        return (new ProfileService(users), users);
    }

    private static async Task<User> AddUser(InMemoryUserRepository users, string firebaseUid = "uid-1")
    {
        var user = new User
        {
            Id = Guid.NewGuid(),
            FirebaseUid = firebaseUid,
            DisplayName = "New Golfer"
        };
        await users.AddAsync(user);
        return user;
    }

    [Fact]
    public async Task UpdateProfileAsync_NewUser_ProfileCompleteDefaultsFalse()
    {
        var (_, users) = CreateService();
        var user = await AddUser(users);

        Assert.False(user.ProfileComplete);
    }

    [Fact]
    public async Task UpdateProfileAsync_WithProfileCompleteTrue_MarksProfileComplete()
    {
        var (service, users) = CreateService();
        var user = await AddUser(users);

        var result = await service.UpdateProfileAsync(
            user.FirebaseUid,
            new UpdateProfileRequest("Real Name", 14.5m, null, PaceOfPlay.Brisk, null, ProfileComplete: true));

        Assert.True(result.ProfileComplete);
        Assert.Equal("Real Name", result.DisplayName);
        Assert.Equal(PaceOfPlay.Brisk, result.PaceOfPlay);
    }

    [Fact]
    public async Task UpdateProfileAsync_WithoutProfileCompleteField_LeavesExistingValueUnchanged()
    {
        var (service, users) = CreateService();
        var user = await AddUser(users);
        user.ProfileComplete = true;
        await users.UpdateAsync(user);

        var result = await service.UpdateProfileAsync(
            user.FirebaseUid,
            new UpdateProfileRequest(null, null, null, PaceOfPlay.Relaxed, null, ProfileComplete: null));

        Assert.True(result.ProfileComplete);
    }

    [Fact]
    public async Task UpdateProfileAsync_ForUnknownFirebaseUid_ThrowsNotFound()
    {
        var (service, _) = CreateService();

        await Assert.ThrowsAsync<NotFoundException>(() => service.UpdateProfileAsync(
            "unknown-uid", new UpdateProfileRequest(null, null, null, null, null, null)));
    }
}
