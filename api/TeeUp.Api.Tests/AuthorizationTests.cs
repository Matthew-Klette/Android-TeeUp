using System.Net;
using System.Net.Http.Json;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.Configuration;

namespace TeeUp.Api.Tests;

public class AuthorizationTests : IClassFixture<WebApplicationFactory<Program>>
{
    private readonly HttpClient _client;

    public AuthorizationTests(WebApplicationFactory<Program> factory)
    {
        _client = factory.WithWebHostBuilder(builder =>
        {
            builder.ConfigureAppConfiguration((_, config) =>
            {
                config.AddInMemoryCollection(new Dictionary<string, string?>
                {
                    ["ConnectionStrings:Default"] = "Host=localhost;Database=teeup_test;Username=test;Password=test",
                    ["Firebase:ProjectId"] = "teeup-test"
                });
            });
        }).CreateClient();
    }

    [Fact]
    public async Task RoundSchedule_WithoutToken_Returns401()
    {
        var response = await _client.GetAsync("/api/rounds/me/schedule");
        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task GetProfile_WithoutToken_Returns401()
    {
        var response = await _client.GetAsync("/api/profiles/me");
        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task UpdateProfile_WithoutToken_Returns401()
    {
        var response = await _client.PatchAsJsonAsync("/api/profiles/me", new { displayName = "Changed" });
        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task CreateJoinRequest_WithoutToken_Returns401()
    {
        var response = await _client.PostAsJsonAsync($"/api/teetimes/{Guid.NewGuid()}/joinrequests", new { });

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task UpdateJoinRequestStatus_WithoutToken_Returns401()
    {
        var response = await _client.PatchAsJsonAsync($"/api/join-requests/{Guid.NewGuid()}", new { status = 0 });

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task PostScorecard_WithoutToken_Returns401()
    {
        var response = await _client.PostAsJsonAsync($"/api/rounds/{Guid.NewGuid()}/scorecard", new { entries = Array.Empty<object>() });

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }
}
