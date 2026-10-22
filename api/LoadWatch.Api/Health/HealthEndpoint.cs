using System.Text.Json;
using Microsoft.AspNetCore.Diagnostics.HealthChecks;
using Microsoft.Extensions.Diagnostics.HealthChecks;

namespace LoadWatch.Api.Health;

// GET /health, anonymous: 200 with {"status":"Healthy",...} when the API and its database are up, 503 otherwise.
// Used by the Azure App Service health check and to confirm a deployment worked.
public static class HealthEndpoint
{
    public const string Path = "/health";
    private const string DatabaseCheck = "database";

    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);

    public static IServiceCollection AddLoadWatchHealthChecks(this IServiceCollection services)
    {
        services.AddHealthChecks().AddCheck<DatabaseHealthCheck>(DatabaseCheck);
        return services;
    }

    public static IEndpointConventionBuilder MapLoadWatchHealth(this IEndpointRouteBuilder endpoints) =>
        endpoints
            .MapHealthChecks(Path, new HealthCheckOptions { ResponseWriter = WriteJsonAsync })
            .AllowAnonymous();

    private static Task WriteJsonAsync(HttpContext context, HealthReport report)
    {
        var database = report.Entries.TryGetValue(DatabaseCheck, out var entry) ? entry.Status : HealthStatus.Unhealthy;
        var body = new HealthResponse(report.Status.ToString(), database.ToString(), DateTimeOffset.UtcNow);

        context.Response.ContentType = "application/json";
        return context.Response.WriteAsync(JsonSerializer.Serialize(body, JsonOptions));
    }

    public record HealthResponse(string Status, string Database, DateTimeOffset CheckedAt);
}
