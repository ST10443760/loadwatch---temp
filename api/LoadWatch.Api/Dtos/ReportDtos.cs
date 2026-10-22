using System.ComponentModel.DataAnnotations;
using LoadWatch.Api.Models;

namespace LoadWatch.Api.Dtos;

// ClientReportId makes the POST idempotent, which is what allows a queued report to be retried safely.
// Value types are nullable and [Required] so a missing field is rejected instead of defaulting.
public record OutageReportCreate
{
    public string AreaId { get; init; } = null!;

    [Required]
    public OutageEventType? EventType { get; init; }

    [Required]
    public DateTimeOffset? ReportedAt { get; init; }

    [Required]
    public DateTimeOffset? ClientCreatedAt { get; init; }

    public string ClientReportId { get; init; } = null!;
}

// Returned by the anonymous GET /api/areas/{areaId}/outage-reports. It is the OutageReport without userId,
// so reporters' Firebase UIDs are never exposed publicly.
public record PublicOutageReport(
    string ReportId,
    string ClientReportId,
    string AreaId,
    OutageEventType EventType,
    DateTimeOffset ReportedAt,
    DateTimeOffset ClientCreatedAt,
    SyncStatus SyncStatus);
