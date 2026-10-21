namespace LoadWatch.Api.Models;

// One row per user, keyed by the Firebase UID.
public class UserSettings
{
    public string UserId { get; set; } = string.Empty;
    public string? DefaultAreaId { get; set; }

    // Constrained to 5 to 120 minutes (FR-SET-02)
    public int NotificationLeadMinutes { get; set; }

    public bool NotificationsEnabled { get; set; }

    // Comma-separated alert types, e.g. "POWER_OFF,POWER_BACK_ON,STAGE_CHANGE"
    public string EnabledAlertTypes { get; set; } = string.Empty;

    // en, zu or af
    public string LanguageCode { get; set; } = "en";

    // system, light or dark
    public string ThemePreference { get; set; } = "system";

    public bool BiometricEnabled { get; set; }
    public DateTimeOffset UpdatedAt { get; set; }
}
