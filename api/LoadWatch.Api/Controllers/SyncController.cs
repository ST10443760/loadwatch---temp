using LoadWatch.Api.Dtos;
using Microsoft.AspNetCore.Mvc;

namespace LoadWatch.Api.Controllers;

[ApiController]
[Route("api/sync")]
public class SyncController : ControllerBase
{
    // POST /api/sync/outage-reports (bearer)
    [HttpPost("outage-reports")]
    public ActionResult<SyncResult> SyncOutageReports([FromBody] SyncBatch batch)
    {
        return this.FinalPoeScope();
    }
}
