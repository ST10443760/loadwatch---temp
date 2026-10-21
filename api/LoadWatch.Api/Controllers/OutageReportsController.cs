using LoadWatch.Api.Dtos;
using LoadWatch.Api.Models;
using Microsoft.AspNetCore.Mvc;

namespace LoadWatch.Api.Controllers;

[ApiController]
[Route("api")]
public class OutageReportsController : ControllerBase
{
    // POST /api/outage-reports (bearer) -> 201 OutageReport, or 409 on a duplicate inside the window
    [HttpPost("outage-reports")]
    public ActionResult<OutageReport> CreateReport([FromBody] OutageReportCreate request)
    {
        return StatusCode(StatusCodes.Status501NotImplemented);
    }

    // GET /api/areas/{areaId}/outage-reports?since= (anonymous)
    [HttpGet("areas/{areaId}/outage-reports")]
    public ActionResult<IEnumerable<OutageReport>> GetAreaReports(string areaId, [FromQuery] DateTimeOffset since)
    {
        return StatusCode(StatusCodes.Status501NotImplemented);
    }

    // GET /api/areas/{areaId}/accuracy (anonymous)
    [HttpGet("areas/{areaId}/accuracy")]
    public ActionResult<AccuracySummary> GetAreaAccuracy(string areaId)
    {
        return StatusCode(StatusCodes.Status501NotImplemented);
    }
}
