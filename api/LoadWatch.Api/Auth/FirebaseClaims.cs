using System.Security.Claims;

namespace LoadWatch.Api.Auth;

// Claim names in a Firebase ID token
public static class FirebaseClaims
{
    // The Firebase UID
    public const string UserId = "sub";
    public const string Name = "name";
    public const string Email = "email";

    // The signed-in user's Firebase UID. Only call this on endpoints that require authentication.
    public static string GetUserId(this ClaimsPrincipal user) =>
        user.FindFirstValue(UserId)
        ?? throw new InvalidOperationException("The authenticated user has no sub claim.");
}
