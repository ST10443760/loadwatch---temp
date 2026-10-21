using LoadWatch.Api.Dtos;
using Microsoft.AspNetCore.Mvc;

namespace LoadWatch.Api.Controllers;

[ApiController]
[Route("api/areas/{areaId}/history")]
public class HistoryController : ControllerBase
{
    // GET /api/areas/{areaId}/history?month= (bearer), month as yyyy-MM
    [HttpGet]
    public ActionResult<IEnumerable<HistoryDay>> GetHistory(string areaId, [FromQuery] string month)
    {
        return StatusCode(StatusCodes.Status501NotImplemented);
    }
}
