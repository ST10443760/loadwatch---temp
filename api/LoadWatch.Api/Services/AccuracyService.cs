using LoadWatch.Api.Data;
using LoadWatch.Api.Models;
using Microsoft.EntityFrameworkCore;

namespace LoadWatch.Api.Services;

// Recomputes an area's AccuracySummary from the community reports in the last 72 hours.
// A report is matched when its event type agrees with the schedule at reportedAt, allowing 15 minutes either way.
public class AccuracyService(LoadWatchDbContext db, TimeProvider time)
{
    public const int WindowHours = 72;
    private static readonly TimeSpan Tolerance = TimeSpan.FromMinutes(15);

    public async Task<AccuracySummary> RecomputeAsync(string areaId, CancellationToken ct = default)
    {
        var now = time.GetUtcNow();
        var windowStart = now.AddHours(-WindowHours);

        var reports = await db.OutageReports.AsNoTracking()
            .Where(r => r.AreaId == areaId && r.ReportedAt >= windowStart)
            .Select(r => new { r.EventType, r.ReportedAt })
            .ToListAsync(ct);

        var events = new List<(DateTimeOffset StartAt, DateTimeOffset EndAt)>();
        if (reports.Count > 0)
        {
            var from = reports.Min(r => r.ReportedAt) - Tolerance;
            var to = reports.Max(r => r.ReportedAt) + Tolerance;
            events = (await db.ScheduleEvents.AsNoTracking()
                    .Where(e => e.AreaId == areaId && e.StartAt <= to && e.EndAt >= from)
                    .Select(e => new { e.StartAt, e.EndAt })
                    .ToListAsync(ct))
                .Select(e => (e.StartAt, e.EndAt))
                .ToList();
        }

        var matched = reports.Count(r => IsMatched(r.EventType, r.ReportedAt, events));
        var score = reports.Count == 0
            ? 0m
            : Math.Round((decimal)matched / reports.Count, 2, MidpointRounding.AwayFromZero);

        var summary = await db.AccuracySummaries.FirstOrDefaultAsync(s => s.AreaId == areaId, ct);
        var isNew = summary is null;
        summary ??= new AccuracySummary { AreaId = areaId };
        summary.AccuracyScore = score;
        summary.SampleSize = reports.Count;
        summary.WindowHours = WindowHours;
        summary.CalculatedAt = now;
        if (isNew)
        {
            db.AccuracySummaries.Add(summary);
        }

        try
        {
            await db.SaveChangesAsync(ct);
        }
        catch (DbUpdateException ex) when (isNew && ex.IsUniqueViolation())
        {
            // A concurrent report created the row first; recompute against it
            db.Entry(summary).State = EntityState.Detached;
            return await RecomputeAsync(areaId, ct);
        }

        return summary;
    }

    // POWER_OFF agrees when some scheduled window overlaps reportedAt ± tolerance.
    // POWER_BACK_ON agrees unless a single window covers the whole of reportedAt ± tolerance.
    private static bool IsMatched(
        OutageEventType eventType, DateTimeOffset reportedAt, List<(DateTimeOffset StartAt, DateTimeOffset EndAt)> events)
    {
        var lo = reportedAt - Tolerance;
        var hi = reportedAt + Tolerance;
        return eventType switch
        {
            OutageEventType.POWER_OFF => events.Any(e => e.StartAt <= hi && e.EndAt >= lo),
            OutageEventType.POWER_BACK_ON => !events.Any(e => e.StartAt <= lo && e.EndAt >= hi),
            _ => false,
        };
    }
}
