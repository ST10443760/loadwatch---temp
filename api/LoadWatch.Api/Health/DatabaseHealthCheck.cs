using LoadWatch.Api.Data;
using Microsoft.Extensions.Diagnostics.HealthChecks;

namespace LoadWatch.Api.Health;

// Healthy when the API can open a connection to its database
public class DatabaseHealthCheck(LoadWatchDbContext db) : IHealthCheck
{
    public async Task<HealthCheckResult> CheckHealthAsync(HealthCheckContext context, CancellationToken ct = default) =>
        await db.Database.CanConnectAsync(ct)
            ? HealthCheckResult.Healthy("The database is reachable.")
            : HealthCheckResult.Unhealthy("The database cannot be reached.");
}
