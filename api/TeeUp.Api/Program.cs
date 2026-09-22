using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using TeeUp.Api.Common;
using TeeUp.Api.Data;
using TeeUp.Api.Repositories;
using TeeUp.Api.Services;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddOpenApi();

builder.Services.AddHttpContextAccessor();
builder.Services.AddScoped<ICurrentUserService, HttpContextCurrentUserService>();

// Firebase tokens are JWTs, so Google's OIDC metadata gives us the signing keys automatically.
var firebaseProjectId = builder.Configuration["Firebase:ProjectId"];

// Dev-only login bypass so the app works without a real Firebase project set up locally.
// Needs Development environment AND DevAuth:Enabled, so it can't reach a real deployment.
var devAuthEnabled = builder.Environment.IsDevelopment() &&
    builder.Configuration.GetValue<bool>("DevAuth:Enabled");

var authBuilder = builder.Services.AddAuthentication(
    devAuthEnabled ? "JwtOrDevBypass" : JwtBearerDefaults.AuthenticationScheme);

// Configure JWT bearer authentication (damienbod, 2025)
authBuilder.AddJwtBearer(options =>
{
    options.Authority = $"https://securetoken.google.com/{firebaseProjectId}";
    options.TokenValidationParameters = new TokenValidationParameters
    {
        ValidateIssuer = true,
        ValidIssuer = $"https://securetoken.google.com/{firebaseProjectId}",
        ValidateAudience = true,
        ValidAudience = firebaseProjectId,
        ValidateLifetime = true
    };
});

if (devAuthEnabled)
{
    authBuilder.AddScheme<Microsoft.AspNetCore.Authentication.AuthenticationSchemeOptions, DevBypassAuthHandler>(
        DevBypassAuthHandler.SchemeName, _ => { });

    // A real bearer token always wins if present. Only requests with no
    // Authorization header fall back to the dev bypass.
    authBuilder.AddPolicyScheme("JwtOrDevBypass", "JWT bearer or dev bypass", options =>
    {
        options.ForwardDefaultSelector = context =>
            context.Request.Headers.ContainsKey("Authorization")
                ? JwtBearerDefaults.AuthenticationScheme
                : DevBypassAuthHandler.SchemeName;
    });
}

builder.Services.AddAuthorization();

builder.Services.AddDbContext<TeeUpDbContext>(options =>
    options.UseNpgsql(builder.Configuration.GetConnectionString("Default")));

// Repositories: EF Core/PostgreSQL-backed. Services depend only on the
// IRepository<T> abstractions, so this swap needed no changes above.
builder.Services.AddScoped<IUserRepository, EfUserRepository>();
builder.Services.AddScoped<ICourseRepository, EfCourseRepository>();
builder.Services.AddScoped<ITeeTimeRepository, EfTeeTimeRepository>();
builder.Services.AddScoped<IJoinRequestRepository, EfJoinRequestRepository>();
builder.Services.AddScoped<IRoundRepository, EfRoundRepository>();
builder.Services.AddScoped<IScorecardEntryRepository, EfScorecardEntryRepository>();
builder.Services.AddScoped<IEndorsementRepository, EfEndorsementRepository>();
builder.Services.AddScoped<INotificationRepository, EfNotificationRepository>();

// Services
builder.Services.AddScoped<IAuthService, AuthService>();
builder.Services.AddScoped<ICourseService, CourseService>();
builder.Services.AddScoped<ITeeTimeService, TeeTimeService>();
builder.Services.AddScoped<IJoinRequestService, JoinRequestService>();
builder.Services.AddScoped<IRoundService, RoundService>();
builder.Services.AddScoped<IProfileService, ProfileService>();
builder.Services.AddScoped<INotificationService, NotificationService>();

var app = builder.Build();

app.UseMiddleware<ExceptionHandlingMiddleware>();

if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
}

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

app.Run();

public partial class Program
{
}

/* References:

damienbod (2025). Configure JWT bearer authentication in ASP.NET Core. [online] Microsoft.com. Available at: <https://learn.microsoft.com/en-us/aspnet/core/security/authentication/configure-jwt-bearer-authentication?view=aspnetcore-10.0> [Accessed 19 Sep. 2026].

*/
