namespace LoadWatch.Api.Dtos;

// Request bodies use init properties rather than positional records so validation errors
// are reported under their JSON field names.
public record UserSettingsUpdate
{
    public string? DefaultAreaId { get; init; }
    public int NotificationLeadMinutes { get; init; }
    public bool NotificationsEnabled { get; init; }
    public string EnabledAlertTypes { get; init; } = null!;
    public string LanguageCode { get; init; } = null!;
    public string ThemePreference { get; init; } = null!;
    public bool BiometricEnabled { get; init; }
}

public record FavouriteAreaCreate
{
    public string AreaId { get; init; } = null!;
    public string Label { get; init; } = null!;
}
