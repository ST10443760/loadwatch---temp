using LoadWatch.Api.Data;

namespace LoadWatch.Api.Health;

// Answers API routes with a 503 ProblemDetails while startup is still preparing the database, so clients get
// a clear "try again shortly" rather than a 500 from a missing table.
public static class DatabaseReadinessGate
{
    private const int RetryAfterSeconds = 15;

    public static IApplicationBuilder UseDatabaseReadinessGate(this IApplicationBuilder app) =>
        app.Use(async (context, next) =>
        {
            var readiness = context.RequestServices.GetRequiredService<DatabaseReadiness>();
            if (readiness.IsReady || !context.Request.Path.StartsWithSegments("/api"))
            {
                await next(context);
                return;
            }

            context.Response.StatusCode = StatusCodes.Status503ServiceUnavailable;
            context.Response.Headers.RetryAfter = RetryAfterSeconds.ToString();
            var problemDetails = context.RequestServices.GetRequiredService<IProblemDetailsService>();
            await problemDetails.WriteAsync(new ProblemDetailsContext
            {
                HttpContext = context,
                ProblemDetails =
                {
                    Status = StatusCodes.Status503ServiceUnavailable,
                    Title = "Database not ready",
                    Detail = readiness.HasFailed
                        ? "The API could not prepare its database and is shutting down."
                        : "The API is waiting for its database, which may be resuming from a pause. Try again shortly.",
                },
            });
        });
}
