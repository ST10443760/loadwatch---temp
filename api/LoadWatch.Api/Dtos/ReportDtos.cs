using LoadWatch.Api.Models;

namespace LoadWatch.Api.Dtos;

// ClientReportId makes the POST idempotent, which is what allows a queued report to be retried safely.
public record OutageReportCreate(
    string AreaId,
    OutageEventType EventType,
    DateTimeOffset ReportedAt,
    DateTimeOffset ClientCreatedAt,
    string ClientReportId);

// AccuracyScore is between 0.00 and 1.00, recomputed on report ingestion.
public record AccuracySummary(
    string AreaId,
    decimal AccuracyScore,
    int SampleSize,
    int WindowHours,
    DateTimeOffset CalculatedAt);
