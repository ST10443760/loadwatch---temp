using System.Globalization;
using LoadWatch.Api.Data;
using LoadWatch.Api.Dtos;
using LoadWatch.Api.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LoadWatch.Api.Controllers;

[ApiController]
[Route("api/areas/{areaId}/history")]
public class HistoryController(LoadWatchDbContext db, OutageHistoryService history) : ControllerBase
{
    // GET /api/areas/{areaId}/history?month=yyyy-MM (anonymous, like the area's schedule and reports)
    // -> 200 AreaHistory, 400 for a missing, malformed or future month, 404 for an unknown area
    [HttpGet]
    public async Task<ActionResult<AreaHistory>> GetHistory(string areaId, [FromQuery] string? month, CancellationToken ct)
    {
        if (!DateOnly.TryParseExact(month, "yyyy-MM", CultureInfo.InvariantCulture, DateTimeStyles.None, out var firstDay))
        {
            ModelState.AddModelError("month", "month must be a year and month such as 2026-11.");
            return ValidationProblem(ModelState);
        }
        if (firstDay > history.CurrentMonth())
        {
            ModelState.AddModelError("month", "month cannot be in the future.");
            return ValidationProblem(ModelState);
        }
        if (!await db.Areas.AnyAsync(a => a.AreaId == areaId, ct))
        {
            return this.AreaNotFound(areaId);
        }

        return await history.GetMonthAsync(areaId, firstDay, ct);
    }
}
