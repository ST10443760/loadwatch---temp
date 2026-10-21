using LoadWatch.Api.Data;
using LoadWatch.Api.Dtos;
using LoadWatch.Api.Models;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LoadWatch.Api.Controllers;

[ApiController]
[Route("api/stages")]
public class StagesController(LoadWatchDbContext db) : ControllerBase
{
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
}
