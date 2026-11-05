namespace LoadWatch.Api.Services;

public enum StageAlertSendResult
{
    Sent,

    // Firebase is not configured, so nothing was sent; the change still counts as handled
    Skipped,

    // Firebase refused or could not be reached; the change is tried again on the next check
    Failed,
}

public interface IStageAlertSender
{
    Task<StageAlertSendResult> SendAsync(string topic, IReadOnlyDictionary<string, string> data, CancellationToken ct);
}
