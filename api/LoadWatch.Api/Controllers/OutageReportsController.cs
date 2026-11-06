using LoadWatch.Api.Auth;
using LoadWatch.Api.Data;
using LoadWatch.Api.Dtos;
using LoadWatch.Api.Models;
using LoadWatch.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LoadWatch.Api.Controllers;

[ApiController]
[Route("api")]
public class OutageReportsController(
    LoadWatchDbContext db,
    AccuracyService accuracy,
    OutageReportService reports,
    TimeProvider time) : ControllerBase
{
    private const int MaxReportsReturned = 500;

    // POST /api/outage-reports (bearer)
    // -> 201 OutageReport for a new report
    // -> 200 with the original report when the clientReportId was already submitted by this user (safe retry)
    // -> 409 when the same user reported the same event for the same area inside the duplicate window
    // The reporter is the signed-in user (the token's sub claim); OutageReportCreate carries no userId (Table 7).
    [Authorize]
    [HttpPost("outage-reports")]
    public async Task<ActionResult<OutageReport>> CreateReport([FromBody] OutageReportCreate request, CancellationToken ct)
    {
        var result = await reports.SubmitAsync(User.GetUserId(), request, ct);
        switch (result.Outcome)
        {
            case ReportSubmissionOutcome.AlreadyStored:
                return Ok(result.Report);

            case ReportSubmissionOutcome.Invalid:
                foreach (var (field, message) in result.Errors!)
                {
                    ModelState.AddModelError(field, message);
                }
                return ValidationProblem(ModelState);

            case ReportSubmissionOutcome.Duplicate:
                var problem = ProblemDetailsFactory.CreateProblemDetails(HttpContext,
                    statusCode: StatusCodes.Status409Conflict,
                    title: "Duplicate report",
                    detail: $"This user already reported {request.EventType} for area '{request.AreaId}' within "
                            + $"{reports.DuplicateWindowMinutes} minutes.");
                problem.Extensions["existingReportId"] = result.ExistingReportId;
                return Conflict(problem);

            default:
                var report = result.Report!;
                await accuracy.RecomputeAsync(report.AreaId, ct);
                return Created($"/api/areas/{report.AreaId}/outage-reports", report);
        }
    }

    // GET /api/areas/{areaId}/outage-reports?since= (anonymous)
    // Newest first. since defaults to the accuracy window, and the result is capped so it cannot grow without bound.
    [HttpGet("areas/{areaId}/outage-reports")]
    public async Task<ActionResult<IEnumerable<PublicOutageReport>>> GetAreaReports(
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
            .Select(r => new PublicOutageReport(
                r.ReportId, r.ClientReportId, r.AreaId, r.EventType, r.ReportedAt, r.ClientCreatedAt, r.SyncStatus))
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
}
