using LoadWatch.Api.Dtos;
using Microsoft.AspNetCore.Mvc;

namespace LoadWatch.Api.Controllers;

[ApiController]
[Route("api/notifications")]
public class NotificationsController : ControllerBase
{
    // POST /api/notifications/registrations (bearer) -> 201
    [HttpPost("registrations")]
    public IActionResult RegisterDevice([FromBody] NotificationRegistrationCreate request)
    {
        return StatusCode(StatusCodes.Status501NotImplemented);
    }
}
