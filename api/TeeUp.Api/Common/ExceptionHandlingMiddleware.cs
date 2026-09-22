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
        catch (ForbiddenException ex)
        {
            await WriteProblem(context, HttpStatusCode.Forbidden, ex.Message);
        }
        catch (ConflictException ex)
        {
            await WriteProblem(context, HttpStatusCode.Conflict, ex.Message);
        }
        catch (Exception ex)
        {
            // Anything not already a domain error used to propagate unhandled, which
            // leaks a stack trace in Development. The Android client just treats any
            // 5xx as "service unavailable, try again", so a plain generic 500 is
            // enough here. The real detail still goes to the server log.
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
