namespace LoadWatch.Api.Models;

// A single structured community confirmation. ClientReportId is unique per user
// and makes the POST idempotent, so a queued report can be retried safely.
public class OutageReport
{
    public string ReportId { get; set; } = string.Empty;
    public string ClientReportId { get; set; } = string.Empty;
    public string UserId { get; set; } = string.Empty;
    public string AreaId { get; set; } = string.Empty;
    public OutageEventType EventType { get; set; }
    public DateTimeOffset ReportedAt { get; set; }
    public DateTimeOffset ClientCreatedAt { get; set; }
    public SyncStatus SyncStatus { get; set; }
}

// Member names match the wire format in the API contract
public enum OutageEventType
{
    POWER_OFF,
    POWER_BACK_ON
}

public enum SyncStatus
{
    PENDING,
    SYNCED,
    RETRYING,
    REJECTED
}
