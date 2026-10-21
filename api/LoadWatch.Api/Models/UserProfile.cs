namespace LoadWatch.Api.Models;

// Identity returned by single sign-on. UserId is the Firebase UID, never a local sequence.
public class UserProfile
{
    public string UserId { get; set; } = string.Empty;
    public string DisplayName { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
    public DateTimeOffset CreatedAt { get; set; }
    public DateTimeOffset LastSeenAt { get; set; }
}
