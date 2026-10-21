namespace LoadWatch.Api.Models;

// One row per user, keyed by the Firebase UID.
public class UserSettings
{
    public string UserId { get; set; } = string.Empty;
    public string? DefaultAreaId { get; set; }

    // Constrained to 5 to 120 minutes (FR-SET-02)
    public int NotificationLeadMinutes { get; set; }

    public bool NotificationsEnabled { get; set; }

    // en, zu or af
    public string LanguageCode { get; set; } = "en";

    public bool BiometricEnabled { get; set; }
    public DateTimeOffset UpdatedAt { get; set; }
}
