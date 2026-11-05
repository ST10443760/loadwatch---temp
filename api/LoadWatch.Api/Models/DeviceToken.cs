namespace LoadWatch.Api.Models;

// A phone's Firebase Cloud Messaging token, registered by the signed-in user. A token belongs to one phone, so if
// another account signs in on that phone the row moves to the new user rather than being duplicated.
public class DeviceToken
{
    public const int MaxTokenLength = 512;

    public string Token { get; set; } = string.Empty;
    public string UserId { get; set; } = string.Empty;

    // Only "android" for now
    public string Platform { get; set; } = string.Empty;

    public DateTimeOffset CreatedAt { get; set; }
    public DateTimeOffset UpdatedAt { get; set; }
}
