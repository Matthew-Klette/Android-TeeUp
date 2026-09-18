namespace TeeUp.Api.Common;

/// <summary>
/// Resolves the authenticated caller's Firebase UID from the request.
/// Reads the "sub" claim populated by the JWT bearer handler once Firebase
/// auth is wired in (EME-291); returns null when no token is present.
/// </summary>
public interface ICurrentUserService
{
    string? FirebaseUid { get; }
}
