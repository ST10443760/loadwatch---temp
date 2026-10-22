using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Filters;
using Microsoft.AspNetCore.Mvc.Infrastructure;

namespace LoadWatch.Api.Auth;

// Registered globally, so every route with {userId} in its path is covered, including future ones.
// A signed-in user may only act on their own userId; anything else is 403. Requests without a valid token
// never reach this filter on [Authorize] routes, because authentication has already answered 401.
public class RouteUserIdFilter : IAuthorizationFilter
{
    public const string RouteKey = "userId";

    public void OnAuthorization(AuthorizationFilterContext context)
    {
        if (!context.RouteData.Values.TryGetValue(RouteKey, out var value) || value is not string routeUserId)
        {
            return;
        }

        var user = context.HttpContext.User;
        if (user.Identity?.IsAuthenticated == true && string.Equals(routeUserId, user.GetUserId(), StringComparison.Ordinal))
        {
            return;
        }

        var problemDetailsFactory = context.HttpContext.RequestServices.GetRequiredService<ProblemDetailsFactory>();
        var problem = problemDetailsFactory.CreateProblemDetails(
            context.HttpContext,
            statusCode: StatusCodes.Status403Forbidden,
            title: "Forbidden",
            detail: "The userId in the path does not match the signed-in user.");
        context.Result = new ObjectResult(problem) { StatusCode = StatusCodes.Status403Forbidden };
    }
}
