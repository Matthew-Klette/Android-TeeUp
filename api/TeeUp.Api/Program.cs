using Microsoft.EntityFrameworkCore;
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

app.MapControllers();

app.Run();

public partial class Program
{
}
