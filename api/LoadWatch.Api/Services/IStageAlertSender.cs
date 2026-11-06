namespace LoadWatch.Api.Services;

/// <summary>The result of trying to send one stage-change alert.</summary>
public enum StageAlertSendResult
{
    Sent,

    // Firebase is not configured, so nothing was sent; the change still counts as handled
    Skipped,

    // Firebase refused or could not be reached; the change is tried again on the next check
    Failed,
}

/// <summary>
/// Sends a stage-change alert to an area's FCM topic. An interface so the tests can record alerts instead of
/// calling Firebase.
/// </summary>
public interface IStageAlertSender
{
    Task<StageAlertSendResult> SendAsync(string topic, IReadOnlyDictionary<string, string> data, CancellationToken ct);
}
