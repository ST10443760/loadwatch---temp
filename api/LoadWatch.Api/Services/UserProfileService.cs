using LoadWatch.Api.Data;
using LoadWatch.Api.Models;
using Microsoft.EntityFrameworkCore;

namespace LoadWatch.Api.Services;

// Until Firebase token validation is wired in, a profile is created the first time a userId is used
// on a user-scoped endpoint. DisplayName and Email stay empty until sign-in supplies them.
public class UserProfileService(LoadWatchDbContext db, TimeProvider time)
{
    // Firebase UIDs are at most 128 characters
    public const int MaxUserIdLength = 128;

    public static bool IsValidUserId(string? userId) =>
        !string.IsNullOrWhiteSpace(userId) && userId.Length <= MaxUserIdLength;

    // Returns the existing profile with LastSeenAt refreshed, or a new one. Changes are saved before returning.
    public async Task<UserProfile> EnsureProfileAsync(string userId, CancellationToken ct = default)
    {
        var now = time.GetUtcNow();
        var profile = await db.UserProfiles.FirstOrDefaultAsync(u => u.UserId == userId, ct);
        if (profile is null)
        {
            profile = new UserProfile { UserId = userId, CreatedAt = now };
            db.UserProfiles.Add(profile);
        }

        profile.LastSeenAt = now;

        try
        {
            await db.SaveChangesAsync(ct);
        }
        catch (DbUpdateException) when (db.Entry(profile).State == EntityState.Added)
        {
            // A concurrent request created the same profile first, so use that one
            db.Entry(profile).State = EntityState.Detached;
            profile = await db.UserProfiles.FirstAsync(u => u.UserId == userId, ct);
        }

        return profile;
    }
}
