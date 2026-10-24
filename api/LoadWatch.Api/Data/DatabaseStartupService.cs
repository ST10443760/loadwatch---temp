namespace LoadWatch.Api.Data;

// Migrates and seeds the database in the background once the API is listening, so a sleeping serverless Azure
// SQL database has time to resume while /health already answers. Transient errors are retried for up to three
// minutes; after that, or on a permanent error, the API logs the failure and shuts down with exit code 1.
public class DatabaseStartupService(
    IServiceScopeFactory scopes,
    DatabaseReadiness readiness,
    TimeProvider time,
    IHostApplicationLifetime lifetime,
    ILogger<DatabaseStartupService> logger) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        // Let the host finish starting before the first database call
        await Task.Yield();

        try
        {
            // One three-minute budget for both steps; each is safe to repeat after a partial attempt
            await new StartupRetry(logger, time).RunAsync("Database migration and seeding", ct => WithContext(async db =>
            {
                await DatabaseMigrator.MigrateAsync(db, logger, ct);
                await DbSeeder.SeedAsync(db, time, ct);
            }), stoppingToken);

            readiness.MarkReady();
            logger.LogInformation("Database is ready");
        }
        catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
        {
            // The API is shutting down anyway
        }
        catch (Exception ex)
        {
            // StartupRetry has already logged the cause as critical
            readiness.MarkFailed(ex);
            logger.LogCritical("The database could not be prepared, so the API is stopping");
            Environment.ExitCode = 1;
            lifetime.StopApplication();
        }
    }

    // A fresh context per attempt, so a failed attempt leaves nothing half-tracked behind
    private async Task WithContext(Func<LoadWatchDbContext, Task> work)
    {
        await using var scope = scopes.CreateAsyncScope();
        await work(scope.ServiceProvider.GetRequiredService<LoadWatchDbContext>());
    }
}
