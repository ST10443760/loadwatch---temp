using LoadWatch.Api.Data;
using LoadWatch.Api.Dtos;
using LoadWatch.Api.Models;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

namespace LoadWatch.Api.Services;

public enum ReportSubmissionOutcome
{
    // A new report was stored
    Created,

    // This user already sent this clientReportId, so the original is returned and nothing new is stored
    AlreadyStored,

    // The same event for the same area from the same user inside the duplicate window
    Duplicate,

    Invalid,
}

public record ReportSubmission(
    ReportSubmissionOutcome Outcome,
    OutageReport? Report = null,
    IReadOnlyDictionary<string, string>? Errors = null,
    string? ExistingReportId = null);

// The rules for storing a community report, shared by the single POST and the offline sync batch so both treat a
// report exactly the same way. A retried clientReportId never creates a second row.
public class OutageReportService(LoadWatchDbContext db, IOptions<OutageReportOptions> options, TimeProvider time)
{
    // Device clocks drift, so allow a report to be slightly ahead of server time
    public static readonly TimeSpan MaxClockSkew = TimeSpan.FromMinutes(5);

    public int DuplicateWindowMinutes => options.Value.DuplicateWindowMinutes;

    public async Task<ReportSubmission> SubmitAsync(string userId, OutageReportCreate request, CancellationToken ct)
    {
        var original = await FindByClientReportIdAsync(userId, request.ClientReportId, ct);
        if (original is not null)
        {
            return new ReportSubmission(ReportSubmissionOutcome.AlreadyStored, original);
        }

        var now = time.GetUtcNow();
        var errors = new Dictionary<string, string>();
        if (string.IsNullOrWhiteSpace(request.ClientReportId))
        {
            errors["clientReportId"] = "clientReportId cannot be empty.";
        }
        if (request.EventType is null)
        {
            errors["eventType"] = "eventType is required.";
        }
        if (request.ReportedAt is null)
        {
            errors["reportedAt"] = "reportedAt is required.";
        }
        else if (request.ReportedAt.Value.ToUniversalTime() > now + MaxClockSkew)
        {
            errors["reportedAt"] = "reportedAt cannot be in the future.";
        }
        if (request.ClientCreatedAt is null)
        {
            errors["clientCreatedAt"] = "clientCreatedAt is required.";
        }
        if (string.IsNullOrEmpty(request.AreaId) || !await db.Areas.AnyAsync(a => a.AreaId == request.AreaId, ct))
        {
            errors["areaId"] = $"No area exists with id '{request.AreaId}'.";
        }
        if (errors.Count > 0)
        {
            return new ReportSubmission(ReportSubmissionOutcome.Invalid, Errors: errors);
        }

        var eventType = request.EventType!.Value;
        var reportedAt = request.ReportedAt!.Value.ToUniversalTime();
        var window = TimeSpan.FromMinutes(DuplicateWindowMinutes);
        var windowStart = reportedAt - window;
        var windowEnd = reportedAt + window;
        var duplicate = await db.OutageReports.AsNoTracking()
            .Where(r => r.UserId == userId && r.AreaId == request.AreaId && r.EventType == eventType
                        && r.ReportedAt > windowStart && r.ReportedAt < windowEnd)
            .OrderByDescending(r => r.ReportedAt)
            .FirstOrDefaultAsync(ct);
        if (duplicate is not null)
        {
            return new ReportSubmission(ReportSubmissionOutcome.Duplicate, ExistingReportId: duplicate.ReportId);
        }

        var report = new OutageReport
        {
            ReportId = Guid.NewGuid().ToString(),
            ClientReportId = request.ClientReportId,
            UserId = userId,
            AreaId = request.AreaId,
            EventType = eventType,
            ReportedAt = reportedAt,
            ClientCreatedAt = request.ClientCreatedAt!.Value.ToUniversalTime(),
            // Accepted by the server, so it is synced by definition
            SyncStatus = SyncStatus.SYNCED,
        };
        db.OutageReports.Add(report);

        try
        {
            await db.SaveChangesAsync(ct);
        }
        catch (DbUpdateException ex) when (ex.IsUniqueViolation())
        {
            // A concurrent retry with the same clientReportId won the race, so return its report
            db.Entry(report).State = EntityState.Detached;
            return new ReportSubmission(
                ReportSubmissionOutcome.AlreadyStored, await FindByClientReportIdAsync(userId, request.ClientReportId, ct));
        }

        return new ReportSubmission(ReportSubmissionOutcome.Created, report);
    }

    private Task<OutageReport?> FindByClientReportIdAsync(string userId, string? clientReportId, CancellationToken ct) =>
        db.OutageReports.AsNoTracking()
            .FirstOrDefaultAsync(r => r.UserId == userId && r.ClientReportId == clientReportId, ct);
}
