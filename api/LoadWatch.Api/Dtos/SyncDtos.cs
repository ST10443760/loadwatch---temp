namespace LoadWatch.Api.Dtos;

// A batch of reports queued on the device while offline.
public record SyncBatch
{
    public IReadOnlyList<OutageReportCreate> Reports { get; init; } = null!;
}

// Authoritative per-item outcome, keyed by clientReportId. Server state wins any conflict.
public record SyncResult(
    IReadOnlyList<string> Accepted,
    IReadOnlyList<string> Duplicates,
    IReadOnlyList<SyncRejection> Rejected,
    int RetryAfterSeconds);

public record SyncRejection(
    string Id,
    string Reason);
