using LoadWatch.Api.Dtos;
using LoadWatch.Api.Models;
using Microsoft.AspNetCore.Mvc;

namespace LoadWatch.Api.Controllers;

[ApiController]
[Route("api/users/{userId}/settings")]
public class UserSettingsController : ControllerBase
{
    // GET /api/users/{userId}/settings (bearer)
    [HttpGet]
    public ActionResult<UserSettings> GetSettings(string userId)
    {
        return StatusCode(StatusCodes.Status501NotImplemented);
    }

    // PUT /api/users/{userId}/settings (bearer)
    [HttpPut]
    public ActionResult<UserSettings> UpdateSettings(string userId, [FromBody] UserSettingsUpdate update)
    {
        return StatusCode(StatusCodes.Status501NotImplemented);
    }
}
