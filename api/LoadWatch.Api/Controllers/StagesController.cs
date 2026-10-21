using LoadWatch.Api.Dtos;
using Microsoft.AspNetCore.Mvc;

namespace LoadWatch.Api.Controllers;

[ApiController]
[Route("api/stages")]
public class StagesController : ControllerBase
{
    // GET /api/stages/current (anonymous)
    [HttpGet("current")]
    public ActionResult<CurrentStage> GetCurrentStage()
    {
        return StatusCode(StatusCodes.Status501NotImplemented);
    }
}
