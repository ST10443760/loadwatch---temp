namespace LoadWatch.Api.Dtos;

/// <summary>
/// Body of POST /api/notifications/registrations. FcmToken is nullable so a missing token is reported as a
/// validation error that names the field.
/// </summary>
public record NotificationRegistrationCreate
{
    public string? FcmToken { get; init; }
    public string Platform { get; init; } = null!;
}
