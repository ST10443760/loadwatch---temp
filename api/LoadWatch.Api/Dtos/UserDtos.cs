namespace LoadWatch.Api.Dtos;

public record UserSettingsUpdate(
    string? DefaultAreaId,
    int NotificationLeadMinutes,
    bool NotificationsEnabled,
    string EnabledAlertTypes,
    string LanguageCode,
    string ThemePreference,
    bool BiometricEnabled);

public record FavouriteAreaCreate(
    string AreaId,
    string Label);
