using LoadWatch.Api.Models;

namespace LoadWatch.Api.Dtos;

// GET /api/areas/{areaId}/history?month=yyyy-MM. Days are South African dates, and only days with an outage or a
// community report are listed, oldest first.
public record AreaHistory(
    string AreaId,
    string Month,
    IReadOnlyList<HistoryDay> Days);

/// <summary>
/// One South African calendar day of an area's history, with its outages and community reports.
/// </summary>
public record HistoryDay(
    DateOnly Date,
    IReadOnlyList<HistoryOutage> Outages,
    IReadOnlyList<HistoryReport> Reports);

// A scheduled outage that has started. ReportCount is how many community reports fall inside it, allowing the
// accuracy tolerance either side.
public record HistoryOutage(
    string ScheduleId,
    int Stage,
    DateTimeOffset StartAt,
    DateTimeOffset EndAt,
    int ReportCount);

// A community report without its reporter, so Firebase UIDs are never exposed
public record HistoryReport(
    string ReportId,
    OutageEventType EventType,
    DateTimeOffset ReportedAt);
