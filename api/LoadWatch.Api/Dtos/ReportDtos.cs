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
