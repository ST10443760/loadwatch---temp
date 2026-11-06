using LoadWatch.Api.Data;
using Microsoft.Extensions.Options;

namespace LoadWatch.Api.Services;

// Checks for stage changes every few seconds once the database is ready. It waits for DatabaseStartupService
// rather than retrying on its own, so the startup retry stays the one place that decides whether the API can run.
// A failed pass is logged and tried again on the next tick; it never stops the API.
// Reference: Microsoft Learn, "Background tasks with hosted services in ASP.NET Core",
// learn.microsoft.com/aspnet/core/fundamentals/host/hosted-services
public class StageAlertService(
    IServiceScopeFactory scopes,
    DatabaseReadiness readiness,
    IOptions<StageAlertOptions> options,
    TimeProvider time,
    ILogger<StageAlertService> logger) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        try
        {
            await readiness.WhenReady.WaitAsync(stoppingToken);
        }
        catch (OperationCanceledException)
        {
            return;
        }
        catch (Exception)
        {
            // The database could not be prepared; DatabaseStartupService has logged it and is stopping the API
            return;
        }

        var interval = TimeSpan.FromSeconds(Math.Max(1, options.Value.PollSeconds));
        logger.LogInformation("Checking for stage changes every {Interval}", interval);
        using var timer = new PeriodicTimer(interval, time);
        try
        {
            do
            {
                await CheckOnceAsync(stoppingToken);
            }
            while (await timer.WaitForNextTickAsync(stoppingToken));
        }
        catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
        {
            // The API is shutting down
        }
    }

    /// <summary>
    /// Runs one check in its own DI scope, so each pass gets a fresh DbContext. Errors are logged and the loop
    /// goes on.
    /// </summary>
    private async Task CheckOnceAsync(CancellationToken ct)
    {
        try
        {
            await using var scope = scopes.CreateAsyncScope();
            await scope.ServiceProvider.GetRequiredService<StageAlertChecker>().CheckAsync(ct);
        }
        catch (Exception ex) when (!ct.IsCancellationRequested)
        {
            logger.LogWarning(ex, "The stage change check failed; it runs again on the next tick");
        }
    }
}
