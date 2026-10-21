using LoadWatch.Api.Data;
using LoadWatch.Api.Dtos;
using LoadWatch.Api.Models;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LoadWatch.Api.Controllers;

[ApiController]
[Route("api/schedules")]
public class SchedulesController(LoadWatchDbContext db, TimeProvider time) : ControllerBase
{
    // Only the requested window is transferred (NFR-09), so cap how wide it can be
    private static readonly TimeSpan MaxWindow = TimeSpan.FromDays(31);

    // GET /api/schedules?areaId=&from=&to= (anonymous)
    // Returns every event that overlaps the window, ordered by start.
    [HttpGet]
    public async Task<ActionResult<IEnumerable<ScheduleEvent>>> GetSchedule(
        [FromQuery] string areaId,
        [FromQuery] DateTimeOffset? from,
        [FromQuery] DateTimeOffset? to,
        CancellationToken ct)
    {
        if (from is null)
        {
            ModelState.AddModelError(nameof(from), "from is required as an ISO-8601 date or instant.");
        }
        if (to is null)
        {
            ModelState.AddModelError(nameof(to), "to is required as an ISO-8601 date or instant.");
        }
        if (from is not null && to is not null)
        {
            if (to <= from)
            {
                ModelState.AddModelError(nameof(to), "to must be later than from.");
            }
            else if (to - from > MaxWindow)
            {
                ModelState.AddModelError(nameof(to), $"The window cannot be longer than {MaxWindow.TotalDays} days.");
            }
        }
        if (!ModelState.IsValid)
        {
            return ValidationProblem(ModelState);
        }

        if (!await db.Areas.AnyAsync(a => a.AreaId == areaId, ct))
        {
            return this.AreaNotFound(areaId);
        }

        return await db.ScheduleEvents.AsNoTracking()
            .Where(e => e.AreaId == areaId && e.StartAt < to && e.EndAt > from)
            .OrderBy(e => e.StartAt)
            .ToListAsync(ct);
    }

    // GET /api/schedules/next?areaId= (anonymous)
    // Inside an event window the state is OFF and the window is the current event, so the client
    // counts down to NextEndAt. Otherwise the state is ON and the window is the next event, so the
    // client counts down to NextStartAt.
    [HttpGet("next")]
    public async Task<ActionResult<NextEvent>> GetNextEvent([FromQuery] string areaId, CancellationToken ct)
    {
        var area = await db.Areas.AsNoTracking().FirstOrDefaultAsync(a => a.AreaId == areaId, ct);
        if (area is null)
        {
            return this.AreaNotFound(areaId);
        }

        var now = time.GetUtcNow();
        var upcoming = await db.ScheduleEvents.AsNoTracking()
            .Where(e => e.AreaId == areaId && e.EndAt > now)
            .OrderBy(e => e.StartAt)
            .FirstOrDefaultAsync(ct);
        if (upcoming is null)
        {
            return Problem(statusCode: StatusCodes.Status404NotFound, title: "No upcoming event",
                detail: $"Area '{areaId}' has no scheduled events after {now:O}.");
        }

        var currentState = upcoming.StartAt <= now ? "OFF" : "ON";
        return new NextEvent(
            area.AreaId,
            area.Name,
            currentState,
            upcoming.Stage,
            upcoming.StartAt,
            upcoming.EndAt,
            now,
            upcoming.UpdatedAt);
    }
}
