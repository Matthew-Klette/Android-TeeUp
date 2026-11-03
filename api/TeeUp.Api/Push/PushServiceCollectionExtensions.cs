using FirebaseAdmin;
using FirebaseAdmin.Messaging;
using Google.Apis.Auth.OAuth2;
using TeeUp.Api.Repositories;

namespace TeeUp.Api.Push;

public static class PushServiceCollectionExtensions
{
    public const string DefaultDatabaseUrl = "https://teeup-881a7-default-rtdb.firebaseio.com";

    /// <summary>
    /// Registers push delivery and wraps <see cref="INotificationRepository"/> so every stored notification is pushed.
    /// Credentials come from <c>Firebase:ServiceAccountJson</c> (on Azure the app setting <c>Firebase__ServiceAccountJson</c>),
    /// falling back to Application Default Credentials. With neither, a no-op sender is registered and the app still runs.
    /// </summary>
    /// <returns>True when real push delivery is configured.</returns>
    public static bool AddPushNotifications(this IServiceCollection services, IConfiguration configuration)
    {
        var credential = ResolveCredential(configuration);
        FirebaseMessaging? messaging = null;

        if (credential is not null)
        {
            try
            {
                var projectId = configuration["Firebase:ProjectId"];
                var app = FirebaseApp.DefaultInstance ?? FirebaseApp.Create(new AppOptions
                {
                    Credential = credential,
                    ProjectId = string.IsNullOrWhiteSpace(projectId) ? null : projectId
                });
                messaging = FirebaseMessaging.GetMessaging(app);
            }
            catch (Exception)
            {
                // e.g. application default credentials without a project id: treat as "not configured".
                credential = null;
            }
        }

        if (credential is null || messaging is null)
        {
            services.AddSingleton<IPushSender, NoOpPushSender>();
        }
        else
        {
            var databaseUrl = configuration["Firebase:DatabaseUrl"] is { Length: > 0 } configured
                ? configured
                : DefaultDatabaseUrl;

            services.AddHttpClient(RtdbDeviceTokenStore.HttpClientName);
            services.AddSingleton(messaging);
            services.AddSingleton<IDeviceTokenStore>(sp => new RtdbDeviceTokenStore(
                sp.GetRequiredService<IHttpClientFactory>(), credential, databaseUrl,
                sp.GetRequiredService<ILogger<RtdbDeviceTokenStore>>()));
            services.AddSingleton<IPushSender, FcmPushSender>();
        }

        // Concrete repo is registered on its own so the decorator can wrap it while services keep asking for the interface.
        services.AddScoped<EfNotificationRepository>();
        services.AddScoped<INotificationRepository>(sp => new PushingNotificationRepository(
            sp.GetRequiredService<EfNotificationRepository>(),
            sp.GetRequiredService<IUserRepository>(),
            sp.GetRequiredService<IPushSender>(),
            sp.GetRequiredService<ILogger<PushingNotificationRepository>>(),
            sendInBackground: true));

        return credential is not null && messaging is not null;
    }

    private static GoogleCredential? ResolveCredential(IConfiguration configuration)
    {
        var json = configuration["Firebase:ServiceAccountJson"];
        try
        {
            if (!string.IsNullOrWhiteSpace(json))
                return GoogleCredential.FromJson(json);

            return GoogleCredential.GetApplicationDefault();
        }
        catch (Exception)
        {
            // Malformed JSON or no application default credentials: run without push rather than failing startup.
            return null;
        }
    }
}

/* References:

Google (n.d.). Firebase Admin SDK for .NET: send messages to multiple devices (SendEachForMulticastAsync). [online] Firebase Documentation. Available at: <https://firebase.google.com/docs/cloud-messaging/send-message> [Accessed 3 Nov. 2026].

Google (n.d.). Firebase Realtime Database REST API: retrieving and deleting data. [online] Firebase Documentation. Available at: <https://firebase.google.com/docs/database/rest/retrieve-data> [Accessed 3 Nov. 2026].

Google (n.d.). Authenticate REST requests with a service account access token. [online] Firebase Documentation. Available at: <https://firebase.google.com/docs/database/rest/auth> [Accessed 3 Nov. 2026].

Google (n.d.). Manage FCM registration tokens: detect invalid tokens. [online] Firebase Documentation. Available at: <https://firebase.google.com/docs/cloud-messaging/manage-tokens> [Accessed 3 Nov. 2026].

*/
