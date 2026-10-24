using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.AspNetCore.Diagnostics.HealthChecks;
using Microsoft.Extensions.Diagnostics.HealthChecks;

namespace LoadWatch.Api.Health;

// GET /health, anonymous: 200 with {"status":"Healthy",...} when the API and its database are up, otherwise 503
// with a "detail" saying why, for example while a paused database resumes.
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
        var found = report.Entries.TryGetValue(DatabaseCheck, out var entry);
        var database = found ? entry.Status : HealthStatus.Unhealthy;
        // Only explain when something is wrong; a healthy answer stays small
        var detail = database == HealthStatus.Healthy ? null : found ? entry.Description : "The database check did not run.";
        var body = new HealthResponse(report.Status.ToString(), database.ToString(), detail, DateTimeOffset.UtcNow);

        context.Response.ContentType = "application/json";
        return context.Response.WriteAsync(JsonSerializer.Serialize(body, JsonOptions));
    }

    public record HealthResponse(
        string Status,
        string Database,
        [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)] string? Detail,
        DateTimeOffset CheckedAt);
}
