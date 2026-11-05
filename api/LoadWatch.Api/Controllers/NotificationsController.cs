using LoadWatch.Api.Auth;
using LoadWatch.Api.Data;
using LoadWatch.Api.Dtos;
using LoadWatch.Api.Models;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LoadWatch.Api.Controllers;

[ApiController]
[Authorize]
[Route("api/notifications")]
public class NotificationsController(LoadWatchDbContext db, TimeProvider time, ILogger<NotificationsController> logger)
    : ControllerBase
{
    private static readonly string[] Platforms = ["android"];

    // POST /api/notifications/registrations (bearer) -> 201 for a new token, 200 when it was already registered
    // The phone sends its FCM token after signing in and whenever Firebase rotates it. A token already registered
    // to another account (someone else signed in on this phone before) moves to the caller.
    [HttpPost("registrations")]
    public async Task<ActionResult<DeviceToken>> RegisterDevice(
        [FromBody] NotificationRegistrationCreate request, CancellationToken ct)
    {
        var token = request.FcmToken?.Trim() ?? string.Empty;
        if (token.Length == 0)
        {
            ModelState.AddModelError("fcmToken", "fcmToken is required.");
        }
        else if (token.Length > DeviceToken.MaxTokenLength)
        {
            ModelState.AddModelError("fcmToken", $"fcmToken cannot be longer than {DeviceToken.MaxTokenLength} characters.");
        }
        if (!Platforms.Contains(request.Platform))
        {
            ModelState.AddModelError("platform", $"platform must be one of: {string.Join(", ", Platforms)}.");
        }
        if (!ModelState.IsValid)
        {
            return ValidationProblem(ModelState);
        }

        var userId = User.GetUserId();
        var now = time.GetUtcNow();
        var existing = await db.DeviceTokens.FirstOrDefaultAsync(d => d.Token == token, ct);
        if (existing is not null)
        {
            return await UpdateRegistration(existing, userId, now, ct);
        }

        var created = new DeviceToken
        {
            Token = token,
            UserId = userId,
            Platform = request.Platform,
            CreatedAt = now,
            UpdatedAt = now,
        };
        db.DeviceTokens.Add(created);
        try
        {
            await db.SaveChangesAsync(ct);
        }
        catch (DbUpdateException ex) when (ex.IsUniqueViolation())
        {
            // Lost a race with the same phone registering twice at once; the other request created the row
            db.ChangeTracker.Clear();
            var winner = await db.DeviceTokens.FirstAsync(d => d.Token == token, ct);
            return await UpdateRegistration(winner, userId, now, ct);
        }

        logger.LogInformation("Registered a new {Platform} device for user {UserId}", created.Platform, userId);
        return Created("/api/notifications/registrations", created);
    }

    private async Task<ActionResult<DeviceToken>> UpdateRegistration(
        DeviceToken registration, string userId, DateTimeOffset now, CancellationToken ct)
    {
        if (registration.UserId != userId)
        {
            logger.LogInformation("Moved a device registration to user {UserId}", userId);
        }
        registration.UserId = userId;
        registration.UpdatedAt = now;
        await db.SaveChangesAsync(ct);
        return Ok(registration);
    }
}
