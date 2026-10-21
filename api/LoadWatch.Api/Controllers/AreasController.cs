using LoadWatch.Api.Dtos;
using LoadWatch.Api.Models;
using Microsoft.AspNetCore.Mvc;

namespace LoadWatch.Api.Controllers;

[ApiController]
[Route("api/areas")]
public class AreasController : ControllerBase
{
    // GET /api/areas?q={text}&page={n} (anonymous)
    [HttpGet]
    public ActionResult<IEnumerable<AreaSummary>> SearchAreas([FromQuery] string q, [FromQuery] int page = 1)
    {
        return StatusCode(StatusCodes.Status501NotImplemented);
    }

    // GET /api/areas/{areaId} (anonymous)
    [HttpGet("{areaId}")]
    public ActionResult<Area> GetArea(string areaId)
    {
        return StatusCode(StatusCodes.Status501NotImplemented);
    }
}
