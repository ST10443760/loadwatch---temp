namespace LoadWatch.Api.Dtos;

public record NotificationRegistrationCreate
{
    public string FcmToken { get; init; } = null!;
    public string Platform { get; init; } = null!;
}
