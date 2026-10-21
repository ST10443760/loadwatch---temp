using LoadWatch.Api.Data;
using LoadWatch.Api.Dtos;
using LoadWatch.Api.Models;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LoadWatch.Api.Controllers;

[ApiController]
[Route("api/areas")]
public class AreasController(LoadWatchDbContext db) : ControllerBase
{
    private const int PageSize = 20;

    // GET /api/areas?q={text}&page={n} (anonymous)
    // Matches suburb name or municipality. An empty q pages through the whole catalogue.
    [HttpGet]
    public async Task<ActionResult<IEnumerable<AreaSummary>>> SearchAreas(
        [FromQuery] string? q, [FromQuery] int page = 1, CancellationToken ct = default)
    {
        if (page < 1)
        {
            ModelState.AddModelError(nameof(page), "page must be 1 or greater.");
            return ValidationProblem(ModelState);
        }

        var query = db.Areas.AsNoTracking().Where(a => a.IsActive);
        if (!string.IsNullOrWhiteSpace(q))
        {
            var text = q.Trim();
            query = query.Where(a => a.Name.Contains(text) || a.Municipality.Contains(text));
        }

        return await query
            .OrderBy(a => a.Name)
            .ThenBy(a => a.AreaId)
            .Skip((page - 1) * PageSize)
            .Take(PageSize)
            .Select(a => new AreaSummary(a.AreaId, a.Name, a.Municipality, a.Province, a.SupplyType))
            .ToListAsync(ct);
    }

    // GET /api/areas/{areaId} (anonymous)
    [HttpGet("{areaId}")]
    public ActionResult<Area> GetArea(string areaId)
    {
        return StatusCode(StatusCodes.Status501NotImplemented);
    }
}
