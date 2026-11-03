using System.Net.Http.Headers;
using System.Text.Json;
using Google.Apis.Auth.OAuth2;
using Microsoft.Extensions.Logging;

namespace TeeUp.Api.Push;

/// <summary>
/// Reads and deletes device tokens in Firebase Realtime Database over its REST API, authorised with the
/// service-account credential (the .NET Admin SDK has no Realtime Database client).
/// </summary>
public class RtdbDeviceTokenStore(
    IHttpClientFactory httpClientFactory,
    GoogleCredential credential,
    string databaseUrl,
    ILogger<RtdbDeviceTokenStore> logger) : IDeviceTokenStore
{
    public const string HttpClientName = "firebase-rtdb";

    private static readonly string[] Scopes =
    [
        "https://www.googleapis.com/auth/firebase.database",
        "https://www.googleapis.com/auth/userinfo.email"
    ];

    private readonly string baseUrl = databaseUrl.TrimEnd('/');
    private readonly ICredential scopedCredential = credential.CreateScoped(Scopes);

    public async Task<IReadOnlyList<string>> GetTokensAsync(string firebaseUid, CancellationToken cancellationToken = default)
    {
        using var request = await BuildRequestAsync(HttpMethod.Get, firebaseUid, null, cancellationToken);
        using var response = await httpClientFactory.CreateClient(HttpClientName).SendAsync(request, cancellationToken);
        response.EnsureSuccessStatusCode();

        // An absent node comes back as the JSON literal null, otherwise an object of { token: updatedAtMillis }.
        using var json = await JsonDocument.ParseAsync(await response.Content.ReadAsStreamAsync(cancellationToken), cancellationToken: cancellationToken);
        if (json.RootElement.ValueKind != JsonValueKind.Object)
            return [];

        return json.RootElement.EnumerateObject().Select(p => p.Name).ToList();
    }

    public async Task RemoveTokenAsync(string firebaseUid, string token, CancellationToken cancellationToken = default)
    {
        try
        {
            using var request = await BuildRequestAsync(HttpMethod.Delete, firebaseUid, token, cancellationToken);
            using var response = await httpClientFactory.CreateClient(HttpClientName).SendAsync(request, cancellationToken);
            response.EnsureSuccessStatusCode();
            logger.LogInformation("Removed dead device token for user {FirebaseUid}.", firebaseUid);
        }
        catch (Exception ex) when (ex is not OperationCanceledException)
        {
            logger.LogWarning(ex, "Could not remove dead device token for user {FirebaseUid}.", firebaseUid);
        }
    }

    private async Task<HttpRequestMessage> BuildRequestAsync(HttpMethod method, string uid, string? token, CancellationToken ct)
    {
        var path = $"{baseUrl}/deviceTokens/{Uri.EscapeDataString(uid)}"
            + (token is null ? string.Empty : $"/{Uri.EscapeDataString(token)}")
            + ".json";

        var accessToken = await scopedCredential.GetAccessTokenForRequestAsync(cancellationToken: ct);
        var request = new HttpRequestMessage(method, path);
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", accessToken);
        return request;
    }
}
