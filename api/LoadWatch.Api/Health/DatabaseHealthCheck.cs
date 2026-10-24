using LoadWatch.Api.Data;
using Microsoft.Extensions.Diagnostics.HealthChecks;

namespace LoadWatch.Api.Health;

// Healthy when startup has prepared the database and the API can still open a connection to it. Never throws,
// so an unreachable database turns into a 503 from /health rather than an error.
public class DatabaseHealthCheck(LoadWatchDbContext db, DatabaseReadiness readiness) : IHealthCheck
{
    // Health checks must answer quickly, so don't wait out EF Core's retries or a full resume
    public static readonly TimeSpan Timeout = TimeSpan.FromSeconds(10);

    public const string NotReadyMessage =
        "The API is still preparing its database, which may be resuming from a pause. Try again shortly.";
    public const string UnreachableMessage =
        "The database cannot be reached right now. It may be paused or resuming; try again in a minute.";

    public async Task<HealthCheckResult> CheckHealthAsync(HealthCheckContext context, CancellationToken ct = default)
    {
        if (readiness.HasFailed)
        {
            return HealthCheckResult.Unhealthy("The API could not prepare its database and is shutting down.");
        }
        if (!readiness.IsReady)
        {
            return HealthCheckResult.Unhealthy(NotReadyMessage);
        }

        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(ct);
        timeout.CancelAfter(Timeout);
        try
        {
            return await db.Database.CanConnectAsync(timeout.Token)
                ? HealthCheckResult.Healthy("The database is reachable.")
                : HealthCheckResult.Unhealthy(UnreachableMessage);
        }
        catch (Exception ex) when (!ct.IsCancellationRequested)
        {
            return HealthCheckResult.Unhealthy(UnreachableMessage, ex);
        }
    }
}
