namespace LoadWatch.Api.Health;

// GET /, anonymous: a fixed 200 saying the API process is up. It never touches the database, so a platform
// warmup or startup probe on / gets a fast answer even while a paused database is still resuming.
// Use /health to find out whether the database is reachable.
public static class RootEndpoint
{
    public const string Message = "LoadWatch API is running";

    private static readonly RootResponse Body = new("LoadWatch API", "Running", Message, HealthEndpoint.Path);

    public static IEndpointConventionBuilder MapLoadWatchRoot(this IEndpointRouteBuilder endpoints) =>
        endpoints
            .MapGet("/", () => Results.Ok(Body))
            .AllowAnonymous()
            .ExcludeFromDescription();

    public record RootResponse(string Service, string Status, string Message, string Health);
}
