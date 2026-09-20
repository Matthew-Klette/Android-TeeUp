using System.Net;
using System.Net.Http.Json;
using System.Security.Claims;
using System.Text.Encodings.Web;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using TeeUp.Api.Dtos;
using TeeUp.Api.Models;
using TeeUp.Api.Repositories;

namespace TeeUp.Api.Tests;

public class ProfileEndpointTests
{
    [Fact]
    public async Task PatchAndGetUseAuthenticatedIdentityAndPreserveOmittedPreferences()
    {
        var users = new InMemoryUserRepository();
        var owner = new User { Id = Guid.NewGuid(), FirebaseUid = "profile-owner", DisplayName = "Owner" };
        var other = new User { Id = Guid.NewGuid(), FirebaseUid = "other-user", DisplayName = "Other" };
        await users.AddAsync(owner);
        await users.AddAsync(other);
        await using var factory = new WebApplicationFactory<Program>().WithWebHostBuilder(builder =>
        {
            builder.ConfigureTestServices(services =>
            {
                services.AddSingleton<IUserRepository>(users);
                services.AddSingleton<ICourseRepository>(new InMemoryCourseRepository());
                services.AddAuthentication(options =>
                {
                    options.DefaultAuthenticateScheme = "ProfileTest";
                    options.DefaultChallengeScheme = "ProfileTest";
                }).AddScheme<AuthenticationSchemeOptions, ProfileTestAuthenticationHandler>("ProfileTest", _ => { });
            });
        });
        using var client = factory.CreateClient();
        var update = await client.PatchAsJsonAsync("/api/profiles/me", new
        {
            displayName = "Updated Owner", handicapIndex = 12.5, paceOfPlay = 1,
            joinRequestNotifications = false, teeTimeReminders = false, weatherAlerts = false,
            firebaseUid = other.FirebaseUid
        });
        Assert.Equal(HttpStatusCode.OK, update.StatusCode);
        var rename = await client.PatchAsJsonAsync("/api/profiles/me", new { displayName = "Renamed Owner" });
        Assert.Equal(HttpStatusCode.OK, rename.StatusCode);
        var saved = await client.GetFromJsonAsync<UserDto>("/api/profiles/me");
        Assert.NotNull(saved);
        Assert.Equal(owner.Id, saved.Id);
        Assert.Equal("Renamed Owner", saved.DisplayName);
        Assert.Equal(12.5m, saved.HandicapIndex);
        Assert.False(saved.JoinRequestNotifications);
        Assert.False(saved.TeeTimeReminders);
        Assert.False(saved.WeatherAlerts);
        Assert.Equal("Other", (await users.GetByIdAsync(other.Id))!.DisplayName);

        var invalid = await client.PatchAsJsonAsync("/api/profiles/me", new { displayName = "Invalid", handicapIndex = 55 });
        Assert.Equal(HttpStatusCode.BadRequest, invalid.StatusCode);
        var unchanged = await client.GetFromJsonAsync<UserDto>("/api/profiles/me");
        Assert.Equal("Renamed Owner", unchanged!.DisplayName);
    }
}

// Test host only: production continues to validate Firebase JWTs.
public class ProfileTestAuthenticationHandler(
    IOptionsMonitor<AuthenticationSchemeOptions> options,
    ILoggerFactory logger,
    UrlEncoder encoder) : AuthenticationHandler<AuthenticationSchemeOptions>(options, logger, encoder)
{
    protected override Task<AuthenticateResult> HandleAuthenticateAsync()
    {
        var identity = new ClaimsIdentity(
            new[] { new Claim(ClaimTypes.NameIdentifier, "profile-owner") }, Scheme.Name);
        return Task.FromResult(AuthenticateResult.Success(
            new AuthenticationTicket(new ClaimsPrincipal(identity), Scheme.Name)));
    }
}
