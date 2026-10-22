using System.Security.Claims;
using LoadWatch.Api.Auth;
using LoadWatch.Api.Data;
using LoadWatch.Api.Models;
using Microsoft.EntityFrameworkCore;

namespace LoadWatch.Api.Services;

// Keeps a UserProfile row in step with the signed-in Firebase user. The profile is created from the token's
// sub, name and email claims on the user's first authenticated request; later requests refresh lastSeenAt
// and pick up a changed name or email.
public class UserProfileService(LoadWatchDbContext db, TimeProvider time)
{
    public async Task<UserProfile> EnsureProfileAsync(ClaimsPrincipal user, CancellationToken ct = default)
    {
        var userId = user.GetUserId();
        var displayName = user.FindFirstValue(FirebaseClaims.Name);
        var email = user.FindFirstValue(FirebaseClaims.Email);
        var now = time.GetUtcNow();

        var profile = await db.UserProfiles.FirstOrDefaultAsync(u => u.UserId == userId, ct);
        if (profile is null)
        {
            profile = new UserProfile { UserId = userId, CreatedAt = now };
            db.UserProfiles.Add(profile);
        }

        // Phone and anonymous sign-ins carry no name or email, so only overwrite with values that are present
        if (!string.IsNullOrWhiteSpace(displayName))
        {
            profile.DisplayName = displayName;
        }
        if (!string.IsNullOrWhiteSpace(email))
        {
            profile.Email = email;
        }
        profile.LastSeenAt = now;

        try
        {
            await db.SaveChangesAsync(ct);
        }
        catch (DbUpdateException ex) when (db.Entry(profile).State == EntityState.Added && ex.IsUniqueViolation())
        {
            // A concurrent first request from the same user created the profile first, so use that one
            db.Entry(profile).State = EntityState.Detached;
            profile = await db.UserProfiles.FirstAsync(u => u.UserId == userId, ct);
        }

        return profile;
    }
}
