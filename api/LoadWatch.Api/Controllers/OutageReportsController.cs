using LoadWatch.Api.Data;
using LoadWatch.Api.Dtos;
using LoadWatch.Api.Models;
using LoadWatch.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

namespace LoadWatch.Api.Controllers;

[ApiController]
[Route("api")]
public class OutageReportsController(
    LoadWatchDbContext db,
    UserProfileService profiles,
    AccuracyService accuracy,
    IOptions<OutageReportOptions> options,
    TimeProvider time) : ControllerBase
{
    // Temporary stand-in for the Firebase ID token's uid claim until token validation is added.
    // OutageReportCreate deliberately carries no userId, matching Table 7.
    public const string UserIdHeader = "X-User-Id";

    // Device clocks drift, so allow a report to be slightly ahead of server time
    private static readonly TimeSpan MaxClockSkew = TimeSpan.FromMinutes(5);

    private const int MaxReportsReturned = 500;

    // POST /api/outage-reports (bearer)
    // -> 201 OutageReport for a new report
    // -> 200 with the original report when the clientReportId was already submitted by this user (safe retry)
    // -> 409 when the same user reported the same event for the same area inside the duplicate window
    [Authorize]
    [HttpPost("outage-reports")]
    public async Task<ActionResult<OutageReport>> CreateReport([FromBody] OutageReportCreate request, CancellationToken ct)
    {
        var userId = Request.Headers[UserIdHeader].ToString();
        if (string.IsNullOrWhiteSpace(userId))
        {
            return Problem(statusCode: StatusCodes.Status401Unauthorized, title: "Not signed in",
                detail: $"A signed-in user is required. Send the user id in the {UserIdHeader} header.");
        }
        if (!UserProfileService.IsValidUserId(userId))
        {
            return this.InvalidUserId();
        }

        var original = await FindByClientReportIdAsync(userId, request.ClientReportId, ct);
        if (original is not null)
        {
            return Ok(original);
        }

        var now = time.GetUtcNow();
        var eventType = request.EventType!.Value;
        var reportedAt = request.ReportedAt!.Value.ToUniversalTime();
        if (string.IsNullOrWhiteSpace(request.ClientReportId))
        {
            ModelState.AddModelError("clientReportId", "clientReportId cannot be empty.");
        }
        if (reportedAt > now + MaxClockSkew)
        {
            ModelState.AddModelError("reportedAt", "reportedAt cannot be in the future.");
        }
        if (!await db.Areas.AnyAsync(a => a.AreaId == request.AreaId, ct))
        {
            ModelState.AddModelError("areaId", $"No area exists with id '{request.AreaId}'.");
        }
        if (!ModelState.IsValid)
        {
            return ValidationProblem(ModelState);
        }

        await profiles.EnsureProfileAsync(userId, ct);

        var window = TimeSpan.FromMinutes(options.Value.DuplicateWindowMinutes);
        var windowStart = reportedAt - window;
        var windowEnd = reportedAt + window;
        var duplicate = await db.OutageReports.AsNoTracking()
            .Where(r => r.UserId == userId && r.AreaId == request.AreaId && r.EventType == eventType
                        && r.ReportedAt > windowStart && r.ReportedAt < windowEnd)
            .OrderByDescending(r => r.ReportedAt)
            .FirstOrDefaultAsync(ct);
        if (duplicate is not null)
        {
            var problem = ProblemDetailsFactory.CreateProblemDetails(HttpContext,
                statusCode: StatusCodes.Status409Conflict,
                title: "Duplicate report",
                detail: $"This user already reported {eventType} for area '{request.AreaId}' within "
                        + $"{options.Value.DuplicateWindowMinutes} minutes.");
            problem.Extensions["existingReportId"] = duplicate.ReportId;
            return Conflict(problem);
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
            return Ok(await FindByClientReportIdAsync(userId, request.ClientReportId, ct));
        }

        await accuracy.RecomputeAsync(report.AreaId, ct);
        return Created($"/api/areas/{report.AreaId}/outage-reports", report);
    }

    // GET /api/areas/{areaId}/outage-reports?since= (anonymous)
    // Newest first. since defaults to the accuracy window, and the result is capped so it cannot grow without bound.
    [HttpGet("areas/{areaId}/outage-reports")]
    public async Task<ActionResult<IEnumerable<OutageReport>>> GetAreaReports(
        string areaId, [FromQuery] DateTimeOffset? since, CancellationToken ct)
    {
        if (!await db.Areas.AnyAsync(a => a.AreaId == areaId, ct))
        {
            return this.AreaNotFound(areaId);
        }

        var cutoff = since ?? time.GetUtcNow().AddHours(-AccuracyService.WindowHours);
        return await db.OutageReports.AsNoTracking()
            .Where(r => r.AreaId == areaId && r.ReportedAt >= cutoff)
            .OrderByDescending(r => r.ReportedAt)
            .Take(MaxReportsReturned)
            .ToListAsync(ct);
    }

    // GET /api/areas/{areaId}/accuracy (anonymous)
    // An area nobody has reported on yet gets a zero score with sampleSize 0 rather than a 404,
    // so the client can always render the badge.
    [HttpGet("areas/{areaId}/accuracy")]
    public async Task<ActionResult<AccuracySummary>> GetAreaAccuracy(string areaId, CancellationToken ct)
    {
        if (!await db.Areas.AnyAsync(a => a.AreaId == areaId, ct))
        {
            return this.AreaNotFound(areaId);
        }

        var summary = await db.AccuracySummaries.AsNoTracking().FirstOrDefaultAsync(s => s.AreaId == areaId, ct);
        return summary ?? new AccuracySummary
        {
            AreaId = areaId,
            AccuracyScore = 0m,
            SampleSize = 0,
            WindowHours = AccuracyService.WindowHours,
            CalculatedAt = time.GetUtcNow(),
        };
    }

    private Task<OutageReport?> FindByClientReportIdAsync(string userId, string clientReportId, CancellationToken ct) =>
        db.OutageReports.AsNoTracking()
            .FirstOrDefaultAsync(r => r.UserId == userId && r.ClientReportId == clientReportId, ct);
}
