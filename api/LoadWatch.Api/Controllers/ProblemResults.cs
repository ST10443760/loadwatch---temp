using Microsoft.AspNetCore.Mvc;

namespace LoadWatch.Api.Controllers;

// Shared ProblemDetails responses so every controller reports the same failure the same way.
public static class ProblemResults
{
    public static ObjectResult AreaNotFound(this ControllerBase controller, string areaId) =>
        controller.Problem(
            statusCode: StatusCodes.Status404NotFound,
            title: "Area not found",
            detail: $"No area exists with id '{areaId}'.");
}
