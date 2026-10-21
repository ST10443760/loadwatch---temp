using LoadWatch.Api.Data;
using LoadWatch.Api.Dtos;
using LoadWatch.Api.Models;
using LoadWatch.Api.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LoadWatch.Api.Controllers;

[ApiController]
[Route("api/users/{userId}/settings")]
public class UserSettingsController(LoadWatchDbContext db, UserProfileService profiles, TimeProvider time)
    : ControllerBase
{
    private const int MinLeadMinutes = 5;
    private const int MaxLeadMinutes = 120;
    private static readonly string[] Languages = ["en", "zu", "af"];
    private static readonly string[] Themes = ["system", "light", "dark"];
    private static readonly string[] AlertTypes = ["POWER_OFF", "POWER_BACK_ON", "STAGE_CHANGE"];

    // GET /api/users/{userId}/settings (bearer)
    // A user without a settings row gets the defaults, which are saved so later reads match.
    [HttpGet]
    public async Task<ActionResult<UserSettings>> GetSettings(string userId, CancellationToken ct)
    {
        if (!UserProfileService.IsValidUserId(userId))
        {
            return this.InvalidUserId();
        }

        await profiles.EnsureProfileAsync(userId, ct);
        var settings = await db.UserSettings.AsNoTracking().FirstOrDefaultAsync(s => s.UserId == userId, ct);
        if (settings is null)
        {
            settings = CreateDefaults(userId);
            db.UserSettings.Add(settings);
            await db.SaveChangesAsync(ct);
        }

        return settings;
    }

    // PUT /api/users/{userId}/settings (bearer)
    [HttpPut]
    public async Task<ActionResult<UserSettings>> UpdateSettings(
        string userId, [FromBody] UserSettingsUpdate update, CancellationToken ct)
    {
        if (!UserProfileService.IsValidUserId(userId))
        {
            return this.InvalidUserId();
        }

        if (update.NotificationLeadMinutes is < MinLeadMinutes or > MaxLeadMinutes)
        {
            ModelState.AddModelError("notificationLeadMinutes",
                $"notificationLeadMinutes must be between {MinLeadMinutes} and {MaxLeadMinutes}.");
        }
        if (!Languages.Contains(update.LanguageCode))
        {
            ModelState.AddModelError("languageCode", $"languageCode must be one of: {string.Join(", ", Languages)}.");
        }
        if (!Themes.Contains(update.ThemePreference))
        {
            ModelState.AddModelError("themePreference", $"themePreference must be one of: {string.Join(", ", Themes)}.");
        }

        var alertTypes = update.EnabledAlertTypes
            .Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
            .Distinct()
            .ToList();
        var unknownAlertTypes = alertTypes.Except(AlertTypes).ToList();
        if (unknownAlertTypes.Count > 0)
        {
            ModelState.AddModelError("enabledAlertTypes",
                $"Unknown alert types: {string.Join(", ", unknownAlertTypes)}. Allowed: {string.Join(", ", AlertTypes)}.");
        }

        if (update.DefaultAreaId is not null && !await db.Areas.AnyAsync(a => a.AreaId == update.DefaultAreaId, ct))
        {
            ModelState.AddModelError("defaultAreaId", $"No area exists with id '{update.DefaultAreaId}'.");
        }

        if (!ModelState.IsValid)
        {
            return ValidationProblem(ModelState);
        }

        await profiles.EnsureProfileAsync(userId, ct);
        var settings = await db.UserSettings.FirstOrDefaultAsync(s => s.UserId == userId, ct);
        if (settings is null)
        {
            settings = new UserSettings { UserId = userId };
            db.UserSettings.Add(settings);
        }

        settings.DefaultAreaId = update.DefaultAreaId;
        settings.NotificationLeadMinutes = update.NotificationLeadMinutes;
        settings.NotificationsEnabled = update.NotificationsEnabled;
        settings.EnabledAlertTypes = string.Join(',', alertTypes);
        settings.LanguageCode = update.LanguageCode;
        settings.ThemePreference = update.ThemePreference;
        settings.BiometricEnabled = update.BiometricEnabled;
        settings.UpdatedAt = time.GetUtcNow();
        await db.SaveChangesAsync(ct);

        return settings;
    }

    private UserSettings CreateDefaults(string userId) => new()
    {
        UserId = userId,
        DefaultAreaId = null,
        NotificationLeadMinutes = 30,
        NotificationsEnabled = true,
        EnabledAlertTypes = string.Join(',', AlertTypes),
        LanguageCode = "en",
        ThemePreference = "system",
        BiometricEnabled = false,
        UpdatedAt = time.GetUtcNow(),
    };
}
