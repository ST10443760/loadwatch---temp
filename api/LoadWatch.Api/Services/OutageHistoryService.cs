using LoadWatch.Api.Data;
using LoadWatch.Api.Dtos;
using Microsoft.EntityFrameworkCore;

namespace LoadWatch.Api.Services;

// Builds an area's outage history for one month from the schedule windows that have started and the community
// reports. Both tables keep their past rows, so the history grows over time.
public class OutageHistoryService(LoadWatchDbContext db, TimeProvider time)
{
    // South Africa Standard Time has no daylight saving, so a fixed offset gives the local date
    public static readonly TimeSpan Sast = TimeSpan.FromHours(2);

    // Keeps one month's response bounded even for a busy area
    public const int MaxReports = 2000;

    public async Task<AreaHistory> GetMonthAsync(string areaId, DateOnly month, CancellationToken ct)
    {
        var monthStart = new DateTimeOffset(month.ToDateTime(TimeOnly.MinValue), Sast);
        var monthEnd = monthStart.AddMonths(1);
        var now = time.GetUtcNow();

        var outages = await db.ScheduleEvents.AsNoTracking()
            .Where(e => e.AreaId == areaId && e.StartAt >= monthStart && e.StartAt < monthEnd && e.StartAt <= now)
            .OrderBy(e => e.StartAt)
            .Select(e => new { e.ScheduleId, e.Stage, e.StartAt, e.EndAt })
            .ToListAsync(ct);
        var reports = await db.OutageReports.AsNoTracking()
            .Where(r => r.AreaId == areaId && r.ReportedAt >= monthStart && r.ReportedAt < monthEnd)
            .OrderBy(r => r.ReportedAt)
            .Take(MaxReports)
            .Select(r => new HistoryReport(r.ReportId, r.EventType, r.ReportedAt))
            .ToListAsync(ct);

        var tolerance = AccuracyService.Tolerance;
        var historyOutages = outages.Select(e => new HistoryOutage(
            e.ScheduleId,
            e.Stage,
            e.StartAt,
            e.EndAt,
            reports.Count(r => r.ReportedAt >= e.StartAt - tolerance && r.ReportedAt <= e.EndAt + tolerance)));

        var outagesByDay = historyOutages.ToLookup(o => LocalDate(o.StartAt));
        var reportsByDay = reports.ToLookup(r => LocalDate(r.ReportedAt));
        var days = outagesByDay.Select(g => g.Key)
            .Union(reportsByDay.Select(g => g.Key))
            .Order()
            .Select(date => new HistoryDay(date, outagesByDay[date].ToList(), reportsByDay[date].ToList()))
            .ToList();

        return new AreaHistory(areaId, month.ToString("yyyy-MM"), days);
    }

    // The current month in South Africa, the latest month the history can be asked for
    public DateOnly CurrentMonth()
    {
        var today = LocalDate(time.GetUtcNow());
        return new DateOnly(today.Year, today.Month, 1);
    }

    private static DateOnly LocalDate(DateTimeOffset instant) => DateOnly.FromDateTime(instant.ToOffset(Sast).DateTime);
}
