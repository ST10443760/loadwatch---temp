using System.Security.Cryptography;
using System.Text;
using LoadWatch.Api.Data;
using LoadWatch.Api.Dtos;
using LoadWatch.Api.Models;
using LoadWatch.Api.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

namespace LoadWatch.Api.Controllers;

[ApiController]
[Route("api/stages")]
public class StagesController(LoadWatchDbContext db) : ControllerBase
{
    public const string TestKeyHeader = "X-Stage-Test-Key";

    // GET /api/stages/current (anonymous)
    [HttpGet("current")]
    public async Task<ActionResult<CurrentStage>> GetCurrentStage(CancellationToken ct)
    {
        var status = await db.StageStatus.AsNoTracking()
            .FirstOrDefaultAsync(s => s.Id == StageStatus.SingletonId, ct);
        if (status is null)
        {
            return Problem(statusCode: StatusCodes.Status503ServiceUnavailable, title: "Stage unavailable",
                detail: "The current load-shedding stage has not been set.");
        }

        return new CurrentStage(status.Stage, status.UpdatedAt);
    }

    // POST /api/stages/areas/{areaId}/test-change (X-Stage-Test-Key header) -> 200
    // Demo only: sets an area's own stage and runs the stage-change check straight away, so a phone subscribed to
    // the area gets the alert within seconds. Off unless StageAlerts:TestKey is configured.
    [HttpPost("areas/{areaId}/test-change")]
    public async Task<ActionResult<TestStageChangeResult>> TriggerTestStageChange(
        string areaId,
        [FromBody] TestStageChangeRequest request,
        [FromHeader(Name = TestKeyHeader)] string? testKey,
        [FromServices] IOptions<StageAlertOptions> options,
        [FromServices] StageAlertChecker checker,
        [FromServices] TimeProvider time,
        CancellationToken ct)
    {
        var expectedKey = options.Value.TestKey;
        if (string.IsNullOrEmpty(expectedKey))
        {
            return Problem(statusCode: StatusCodes.Status404NotFound, title: "Test stage changes are off",
                detail: "Set StageAlerts:TestKey on the API to turn this endpoint on.");
        }
        if (testKey is null || !CryptographicOperations.FixedTimeEquals(
                Encoding.UTF8.GetBytes(testKey), Encoding.UTF8.GetBytes(expectedKey)))
        {
            return Problem(statusCode: StatusCodes.Status403Forbidden, title: "Wrong test key",
                detail: $"Send the configured key in the {TestKeyHeader} header.");
        }
        if (request.Stage is < 0 or > 8)
        {
            ModelState.AddModelError("stage", "stage must be between 0 and 8, or null to follow the national stage.");
            return ValidationProblem(ModelState);
        }
        if (!await db.Areas.AnyAsync(a => a.AreaId == areaId && a.IsActive, ct))
        {
            return this.AreaNotFound(areaId);
        }

        var national = await db.StageStatus.AsNoTracking().FirstAsync(s => s.Id == StageStatus.SingletonId, ct);
        var previousStage = national.Stage;
        var outcomes = await checker.ChangeThenCheckAsync(async innerCt =>
        {
            var row = await db.AreaStages.FirstOrDefaultAsync(s => s.AreaId == areaId, innerCt);
            if (row is null)
            {
                row = new AreaStage { AreaId = areaId };
                db.AreaStages.Add(row);
            }
            previousStage = StageChangeDetector.EffectiveStage(national.Stage, row.Stage);
            // An area never checked before has no baseline, and would only be recorded rather than alerted
            row.LastAlertedStage ??= previousStage;
            row.Stage = request.Stage;
            row.UpdatedAt = time.GetUtcNow();
            await db.SaveChangesAsync(innerCt);
        }, ct);

        var outcome = outcomes.FirstOrDefault(o => o.Change.AreaId == areaId);
        var stage = StageChangeDetector.EffectiveStage(national.Stage, request.Stage);
        return new TestStageChangeResult(areaId, previousStage, stage, outcome?.Result.ToString() ?? "NoChange");
    }
}
