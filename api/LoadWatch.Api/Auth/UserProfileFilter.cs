using LoadWatch.Api.Services;
using Microsoft.AspNetCore.Mvc.Filters;

namespace LoadWatch.Api.Auth;

// Runs before every controller action. When the request carries a valid Firebase token, the user's
// UserProfile is created or refreshed first, so the foreign keys from settings, favourites and reports
// always have a row to point at.
public class UserProfileFilter(UserProfileService profiles) : IAsyncActionFilter
{
    public async Task OnActionExecutionAsync(ActionExecutingContext context, ActionExecutionDelegate next)
    {
        var user = context.HttpContext.User;
        if (user.Identity?.IsAuthenticated == true)
        {
            await profiles.EnsureProfileAsync(user, context.HttpContext.RequestAborted);
        }

        await next();
    }
}
