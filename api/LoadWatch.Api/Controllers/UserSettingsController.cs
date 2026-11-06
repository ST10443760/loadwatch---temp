using LoadWatch.Api.Data;
using LoadWatch.Api.Dtos;
using LoadWatch.Api.Models;
using LoadWatch.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LoadWatch.Api.Controllers;

/// <summary>
/// Reads and replaces the signed-in user's settings. The same rules apply as in the offline sync batch.
/// </summary>
[ApiController]
[Authorize]
[Route("api/users/{userId}/settings")]
public class UserSettingsController(LoadWatchDbContext db, TimeProvider time)
    : ControllerBase
{
    // GET /api/users/{userId}/settings (bearer)
    // A user without a settings row gets the defaults, which are saved so later reads match.
    [HttpGet]
    public async Task<ActionResult<UserSettings>> GetSettings(string userId, CancellationToken ct)
    {
        var settings = await db.UserSettings.AsNoTracking().FirstOrDefaultAsync(s => s.UserId == userId, ct);
        if (settings is null)
        {
            settings = UserSettingsRules.Defaults(userId, time.GetUtcNow());
            db.UserSettings.Add(settings);
            await db.SaveChangesAsync(ct);
        }

        return settings;
    }

    // PUT /api/users/{userId}/settings (bearer)
    // Stamped with the server's time, so an older change queued offline on another phone cannot overwrite it.
    [HttpPut]
    public async Task<ActionResult<UserSettings>> UpdateSettings(
        string userId, [FromBody] UserSettingsUpdate update, CancellationToken ct)
    {
        foreach (var (field, message) in await UserSettingsRules.ValidateAsync(update, db, ct))
        {
            ModelState.AddModelError(field, message);
        }
        if (!ModelState.IsValid)
        {
            return ValidationProblem(ModelState);
        }

        var settings = await db.UserSettings.FirstOrDefaultAsync(s => s.UserId == userId, ct);
        if (settings is null)
        {
            settings = new UserSettings { UserId = userId };
            db.UserSettings.Add(settings);
        }

        UserSettingsRules.Apply(settings, update, time.GetUtcNow());
        await db.SaveChangesAsync(ct);

        return settings;
    }
}
