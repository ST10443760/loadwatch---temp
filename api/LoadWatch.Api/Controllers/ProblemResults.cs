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

    public static ObjectResult FinalPoeScope(this ControllerBase controller) =>
        controller.Problem(
            statusCode: StatusCodes.Status501NotImplemented,
            title: "Not implemented",
            detail: "This endpoint is part of the Final POE scope and is not available yet.");
}
