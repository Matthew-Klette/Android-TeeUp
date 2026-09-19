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

// Firebase ID tokens are OIDC-compliant JWTs issued by securetoken.google.com.
// Setting Authority lets the handler discover Google's public signing keys
// (and future key rotations) from its OIDC metadata automatically.
var firebaseProjectId = builder.Configuration["Firebase:ProjectId"];

builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
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
