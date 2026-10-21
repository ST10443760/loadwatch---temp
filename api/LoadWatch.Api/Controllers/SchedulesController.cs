using LoadWatch.Api.Dtos;
using LoadWatch.Api.Models;
using Microsoft.AspNetCore.Mvc;

namespace LoadWatch.Api.Controllers;

[ApiController]
[Route("api/schedules")]
public class SchedulesController : ControllerBase
{
    // GET /api/schedules?areaId=&from=&to= (anonymous)
    [HttpGet]
    public ActionResult<IEnumerable<ScheduleEvent>> GetSchedule(
        [FromQuery] string areaId,
        [FromQuery] DateTimeOffset from,
        [FromQuery] DateTimeOffset to)
    {
        return StatusCode(StatusCodes.Status501NotImplemented);
    }

    // GET /api/schedules/next?areaId= (anonymous)
    [HttpGet("next")]
    public ActionResult<NextEvent> GetNextEvent([FromQuery] string areaId)
    {
        return StatusCode(StatusCodes.Status501NotImplemented);
    }
}
