namespace TeeUp.Api.Push;

/// <summary>
/// Read/cleanup access to the FCM device tokens the Android app writes to Firebase Realtime Database at
/// <c>deviceTokens/{firebaseUid}/{fcmToken}</c>. An interface so the push logic can be tested without the network.
/// </summary>
public interface IDeviceTokenStore
{
    Task<IReadOnlyList<string>> GetTokensAsync(string firebaseUid, CancellationToken cancellationToken = default);

    /// <summary>Deletes a token FCM reported as dead. Failures are the implementation's to log, not throw.</summary>
    Task RemoveTokenAsync(string firebaseUid, string token, CancellationToken cancellationToken = default);
}
