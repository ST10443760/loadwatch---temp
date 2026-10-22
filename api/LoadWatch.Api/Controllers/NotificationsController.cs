using LoadWatch.Api.Dtos;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace LoadWatch.Api.Controllers;

[ApiController]
[Authorize]
[Route("api/notifications")]
public class NotificationsController : ControllerBase
{
    // POST /api/notifications/registrations (bearer) -> 201
    [HttpPost("registrations")]
    public IActionResult RegisterDevice([FromBody] NotificationRegistrationCreate request)
    {
        return this.FinalPoeScope();
    }
}
