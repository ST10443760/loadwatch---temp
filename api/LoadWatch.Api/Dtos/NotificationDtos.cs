namespace LoadWatch.Api.Dtos;

public record NotificationRegistrationCreate
{
    public string? FcmToken { get; init; }
    public string Platform { get; init; } = null!;
}
