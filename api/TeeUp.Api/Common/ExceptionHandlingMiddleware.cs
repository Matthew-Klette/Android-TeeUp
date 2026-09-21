using System.Net;

namespace TeeUp.Api.Common;

public class ExceptionHandlingMiddleware(RequestDelegate next, ILogger<ExceptionHandlingMiddleware> logger)
{
    public async Task InvokeAsync(HttpContext context)
    {
        try
        {
            await next(context);
        }
        catch (DomainValidationException ex)
        {
            await WriteProblem(context, HttpStatusCode.BadRequest, ex.Message);
        }
        catch (NotFoundException ex)
        {
            await WriteProblem(context, HttpStatusCode.NotFound, ex.Message);
        }
        catch (Exception ex)
        {
            // EME-305 QA pass: anything not already a domain/not-found error (a bug, a DB
            // timeout, an unexpected null) used to propagate unhandled — in Development that
            // returns ASP.NET's diagnostic page complete with a stack trace, which the app has
            // no contract for and which leaks internals. The Android client already treats any
            // 5xx as "service unavailable, try again" (see TeeUpApiClient.httpFailureMessage /
            // TeeUpRepository.call) without reading the body, so a plain generic 500 here is
            // all it needs, and the real detail still goes to the server log for debugging.
            logger.LogError(ex, "Unhandled exception for {Method} {Path}", context.Request.Method, context.Request.Path);
            if (!context.Response.HasStarted)
            {
                await WriteProblem(context, HttpStatusCode.InternalServerError,
                    "Something went wrong on our end. Please try again shortly.");
            }
        }
    }

    private static Task WriteProblem(HttpContext context, HttpStatusCode status, string detail)
    {
        context.Response.StatusCode = (int)status;
        return context.Response.WriteAsJsonAsync(new { status = (int)status, detail });
    }
}
