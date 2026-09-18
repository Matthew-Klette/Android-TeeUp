using TeeUp.Api.Common;
using TeeUp.Api.Repositories;
using TeeUp.Api.Services;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddOpenApi();

builder.Services.AddHttpContextAccessor();
builder.Services.AddScoped<ICurrentUserService, HttpContextCurrentUserService>();

// Repositories (in-memory for now; swapped for EF Core/PostgreSQL-backed
// implementations in EME-290 without touching the service layer above them).
builder.Services.AddSingleton<IUserRepository, InMemoryUserRepository>();
builder.Services.AddSingleton<ICourseRepository, InMemoryCourseRepository>();
builder.Services.AddSingleton<ITeeTimeRepository, InMemoryTeeTimeRepository>();
builder.Services.AddSingleton<IJoinRequestRepository, InMemoryJoinRequestRepository>();
builder.Services.AddSingleton<IRoundRepository, InMemoryRoundRepository>();
builder.Services.AddSingleton<IScorecardEntryRepository, InMemoryScorecardEntryRepository>();
builder.Services.AddSingleton<IEndorsementRepository, InMemoryEndorsementRepository>();
builder.Services.AddSingleton<INotificationRepository, InMemoryNotificationRepository>();

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

app.MapControllers();

app.Run();

public partial class Program
{
}
