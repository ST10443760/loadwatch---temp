namespace LoadWatch.Api.Data;

// Keeps retrying a startup step while the database is only temporarily unavailable, waiting longer after each
// failed attempt, for up to MaxDuration in total. Permanent errors, such as a wrong password, fail at once.
public class StartupRetry(ILogger logger, TimeProvider time, Func<TimeSpan, CancellationToken, Task>? delay = null)
{
    public static readonly TimeSpan MaxDuration = TimeSpan.FromMinutes(3);
    public static readonly TimeSpan FirstDelay = TimeSpan.FromSeconds(2);
    public static readonly TimeSpan MaxDelay = TimeSpan.FromSeconds(30);

    private readonly Func<TimeSpan, CancellationToken, Task> _delay = delay ?? ((wait, ct) => Task.Delay(wait, time, ct));

    public async Task RunAsync(string description, Func<CancellationToken, Task> step, CancellationToken ct = default)
    {
        var startedAt = time.GetUtcNow();
        // Also stops an attempt that is still running when the time is up, including EF Core's own retries
        using var deadline = CancellationTokenSource.CreateLinkedTokenSource(ct);
        deadline.CancelAfter(MaxDuration);
        var wait = FirstDelay;

        for (var attempt = 1; ; attempt++)
        {
            try
            {
                await step(deadline.Token);
                if (attempt > 1)
                {
                    logger.LogInformation("{Description} succeeded on attempt {Attempt}", description, attempt);
                }
                return;
            }
            catch (Exception ex) when (!ct.IsCancellationRequested)
            {
                var elapsed = time.GetUtcNow() - startedAt;
                if (deadline.IsCancellationRequested)
                {
                    throw GiveUp(description, attempt, elapsed, ex);
                }
                if (!TransientSqlErrors.IsTransient(ex))
                {
                    logger.LogCritical(ex, "{Description} failed with a permanent error on attempt {Attempt}; not retrying", description, attempt);
                    throw;
                }
                if (elapsed + wait > MaxDuration)
                {
                    throw GiveUp(description, attempt, elapsed, ex);
                }

                logger.LogWarning(
                    "{Description} failed on attempt {Attempt} because the database is not available yet ({Error}); retrying in {Delay:0} seconds",
                    description, attempt, ex.GetBaseException().Message, wait.TotalSeconds);
                try
                {
                    await _delay(wait, deadline.Token);
                }
                catch (OperationCanceledException) when (deadline.IsCancellationRequested && !ct.IsCancellationRequested)
                {
                    throw GiveUp(description, attempt, time.GetUtcNow() - startedAt, ex);
                }
                wait = TimeSpan.FromTicks(Math.Min(wait.Ticks * 2, MaxDelay.Ticks));
            }
        }
    }

    private TimeoutException GiveUp(string description, int attempts, TimeSpan elapsed, Exception lastError)
    {
        logger.LogCritical(lastError, "{Description} still failing after {Attempts} attempts over {Seconds:0} seconds; giving up",
            description, attempts, elapsed.TotalSeconds);
        return new TimeoutException($"{description} did not succeed within {MaxDuration.TotalMinutes:0} minutes.", lastError);
    }
}
