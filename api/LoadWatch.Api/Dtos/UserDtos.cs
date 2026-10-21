namespace LoadWatch.Api.Dtos;

public record UserSettingsUpdate(
    string? DefaultAreaId,
    int NotificationLeadMinutes,
    bool NotificationsEnabled,
    string LanguageCode,
    bool BiometricEnabled);

public record FavouriteAreaCreate(
    string AreaId,
    string Label);
