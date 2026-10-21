namespace LoadWatch.Api.Dtos;

public record NotificationRegistrationCreate(
    string FcmToken,
    string Platform);
