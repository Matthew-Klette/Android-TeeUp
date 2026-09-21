using System.Security.Claims;
using System.Text.Encodings.Web;
using Microsoft.AspNetCore.Authentication;
using Microsoft.Extensions.Options;

namespace TeeUp.Api.Common;

/// <summary>
/// Dev-only stand-in for real Firebase JWT auth. Only ever registered when
/// Program.cs decides the host is running in Development AND DevAuth:Enabled
/// is explicitly set — lets the app (join requests, rounds, profile) be
/// exercised end-to-end without a real Firebase project wired up locally.
/// Trusts whatever id the caller puts in X-Dev-User-Id with zero verification,
/// so it must never be reachable outside dev — see the two-gate check in
/// Program.cs, not just this class, for why that's safe.
/// </summary>
public class DevBypassAuthHandler(
    IOptionsMonitor<AuthenticationSchemeOptions> options,
    ILoggerFactory logger,
    UrlEncoder encoder) : AuthenticationHandler<AuthenticationSchemeOptions>(options, logger, encoder)
{
    public const string SchemeName = "DevBypass";
    private const string HeaderName = "X-Dev-User-Id";

    protected override Task<AuthenticateResult> HandleAuthenticateAsync()
    {
        var devUserId = Request.Headers[HeaderName].FirstOrDefault();
        if (string.IsNullOrWhiteSpace(devUserId))
        {
            return Task.FromResult(AuthenticateResult.Fail($"Missing {HeaderName} header."));
        }

        var identity = new ClaimsIdentity(
            new[] { new Claim(ClaimTypes.NameIdentifier, devUserId) }, SchemeName);
        var ticket = new AuthenticationTicket(new ClaimsPrincipal(identity), SchemeName);
        return Task.FromResult(AuthenticateResult.Success(ticket));
    }
}
