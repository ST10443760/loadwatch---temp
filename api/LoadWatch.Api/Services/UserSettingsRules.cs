using LoadWatch.Api.Data;
using LoadWatch.Api.Dtos;
using LoadWatch.Api.Models;
using Microsoft.EntityFrameworkCore;

namespace LoadWatch.Api.Services;

// The rules for a user's settings, shared by the settings PUT and the offline sync batch
public static class UserSettingsRules
{
    public const int MinLeadMinutes = 5;
    public const int MaxLeadMinutes = 120;
    public static readonly string[] Languages = ["en", "zu", "af"];
    public static readonly string[] Themes = ["system", "light", "dark"];
    public static readonly string[] AlertTypes = ["POWER_OFF", "POWER_BACK_ON", "STAGE_CHANGE"];

    // Field name -> message for every value that breaks a rule; empty when the update is valid
    public static async Task<Dictionary<string, string>> ValidateAsync(
        UserSettingsUpdate update, LoadWatchDbContext db, CancellationToken ct)
    {
        var errors = new Dictionary<string, string>();
        if (update.NotificationLeadMinutes is < MinLeadMinutes or > MaxLeadMinutes)
        {
            errors["notificationLeadMinutes"] =
                $"notificationLeadMinutes must be between {MinLeadMinutes} and {MaxLeadMinutes}.";
        }
        if (!Languages.Contains(update.LanguageCode))
        {
            errors["languageCode"] = $"languageCode must be one of: {string.Join(", ", Languages)}.";
        }
        if (!Themes.Contains(update.ThemePreference))
        {
            errors["themePreference"] = $"themePreference must be one of: {string.Join(", ", Themes)}.";
        }

        var unknownAlertTypes = ParseAlertTypes(update.EnabledAlertTypes).Except(AlertTypes).ToList();
        if (unknownAlertTypes.Count > 0)
        {
            errors["enabledAlertTypes"] =
                $"Unknown alert types: {string.Join(", ", unknownAlertTypes)}. Allowed: {string.Join(", ", AlertTypes)}.";
        }

        if (update.DefaultAreaId is not null && !await db.Areas.AnyAsync(a => a.AreaId == update.DefaultAreaId, ct))
        {
            errors["defaultAreaId"] = $"No area exists with id '{update.DefaultAreaId}'.";
        }

        return errors;
    }

    // Copies a valid update onto the stored settings
    public static void Apply(UserSettings settings, UserSettingsUpdate update, DateTimeOffset updatedAt)
    {
        settings.DefaultAreaId = update.DefaultAreaId;
        settings.NotificationLeadMinutes = update.NotificationLeadMinutes;
        settings.NotificationsEnabled = update.NotificationsEnabled;
        settings.EnabledAlertTypes = string.Join(',', ParseAlertTypes(update.EnabledAlertTypes));
        settings.LanguageCode = update.LanguageCode;
        settings.ThemePreference = update.ThemePreference;
        settings.BiometricEnabled = update.BiometricEnabled;
        settings.UpdatedAt = updatedAt;
    }

    public static UserSettings Defaults(string userId, DateTimeOffset now) => new()
    {
        UserId = userId,
        DefaultAreaId = null,
        NotificationLeadMinutes = 30,
        NotificationsEnabled = true,
        EnabledAlertTypes = string.Join(',', AlertTypes),
        LanguageCode = "en",
        ThemePreference = "system",
        BiometricEnabled = false,
        UpdatedAt = now,
    };

    private static List<string> ParseAlertTypes(string? value) =>
        (value ?? string.Empty)
            .Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
            .Distinct()
            .ToList();
}
