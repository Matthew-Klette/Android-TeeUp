using System.Net;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging.Abstractions;
using TeeUp.Api.Common;

namespace TeeUp.Api.Tests;

/// <summary>
/// EME-305 manual QA pass: confirms every failure mode a controller/service can throw is
/// turned into a clean JSON response with an appropriate status code — never an unhandled
/// exception that would bubble up as a raw 5xx/stack trace to the Android client.
/// </summary>
public class ExceptionHandlingMiddlewareTests
{
    private static async Task<(int StatusCode, string Body)> InvokeAsync(RequestDelegate next)
    {
        var middleware = new ExceptionHandlingMiddleware(next, NullLogger<ExceptionHandlingMiddleware>.Instance);
        var context = new DefaultHttpContext
        {
            RequestServices = new ServiceCollection().BuildServiceProvider(),
            Response = { Body = new MemoryStream() }
        };

        await middleware.InvokeAsync(context);

        context.Response.Body.Seek(0, SeekOrigin.Begin);
        var body = await new StreamReader(context.Response.Body).ReadToEndAsync();
        return (context.Response.StatusCode, body);
    }

    [Fact]
    public async Task DomainValidationExceptionMapsTo400WithItsMessage()
    {
        var (status, body) = await InvokeAsync(_ => throw new DomainValidationException("Handicap must be between 0 and 54."));

        Assert.Equal((int)HttpStatusCode.BadRequest, status);
        Assert.Contains("Handicap must be between 0 and 54.", body);
    }

    [Fact]
    public async Task NotFoundExceptionMapsTo404WithItsMessage()
    {
        var (status, body) = await InvokeAsync(_ => throw new NotFoundException("Tee time not found."));

        Assert.Equal((int)HttpStatusCode.NotFound, status);
        Assert.Contains("Tee time not found.", body);
    }

    [Fact]
    public async Task UnexpectedExceptionMapsTo500WithoutLeakingItsDetails()
    {
        // A bug, a DB timeout, a null-ref — anything that isn't a recognised domain
        // error — must still come back as a plain, safe response: no stack trace,
        // no exception type name, no message that might contain internal details.
        const string internalDetail = "connection string password=hunter2";
        var (status, body) = await InvokeAsync(_ => throw new InvalidOperationException(internalDetail));

        Assert.Equal((int)HttpStatusCode.InternalServerError, status);
        Assert.DoesNotContain(internalDetail, body);
        Assert.DoesNotContain("InvalidOperationException", body);
        Assert.DoesNotContain("StackTrace", body);
    }
}
