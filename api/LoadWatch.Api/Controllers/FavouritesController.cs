using LoadWatch.Api.Data;
using LoadWatch.Api.Dtos;
using LoadWatch.Api.Models;
using LoadWatch.Api.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LoadWatch.Api.Controllers;

[ApiController]
[Route("api/users/{userId}/favourites")]
public class FavouritesController(LoadWatchDbContext db, UserProfileService profiles, TimeProvider time)
    : ControllerBase
{
    private const int MaxLabelLength = 40;

    // GET /api/users/{userId}/favourites (bearer)
    [HttpGet]
    public async Task<ActionResult<IEnumerable<FavouriteArea>>> GetFavourites(string userId, CancellationToken ct)
    {
        if (!UserProfileService.IsValidUserId(userId))
        {
            return this.InvalidUserId();
        }

        await profiles.EnsureProfileAsync(userId, ct);
        return await db.FavouriteAreas.AsNoTracking()
            .Where(f => f.UserId == userId)
            .OrderBy(f => f.SortOrder)
            .ThenBy(f => f.CreatedAt)
            .ToListAsync(ct);
    }

    // POST /api/users/{userId}/favourites (bearer) -> 201 FavouriteArea, or 409 if the area is already saved
    [HttpPost]
    public async Task<ActionResult<FavouriteArea>> AddFavourite(
        string userId, [FromBody] FavouriteAreaCreate request, CancellationToken ct)
    {
        if (!UserProfileService.IsValidUserId(userId))
        {
            return this.InvalidUserId();
        }

        var label = request.Label?.Trim() ?? string.Empty;
        if (label.Length > MaxLabelLength)
        {
            ModelState.AddModelError("label", $"label cannot be longer than {MaxLabelLength} characters.");
        }
        if (!await db.Areas.AnyAsync(a => a.AreaId == request.AreaId, ct))
        {
            ModelState.AddModelError("areaId", $"No area exists with id '{request.AreaId}'.");
        }
        if (!ModelState.IsValid)
        {
            return ValidationProblem(ModelState);
        }

        await profiles.EnsureProfileAsync(userId, ct);
        if (await db.FavouriteAreas.AnyAsync(f => f.UserId == userId && f.AreaId == request.AreaId, ct))
        {
            return FavouriteExists(request.AreaId);
        }

        var lastSortOrder = await db.FavouriteAreas
            .Where(f => f.UserId == userId)
            .MaxAsync(f => (int?)f.SortOrder, ct);
        var favourite = new FavouriteArea
        {
            FavouriteId = Guid.NewGuid().ToString(),
            UserId = userId,
            AreaId = request.AreaId,
            Label = label,
            SortOrder = (lastSortOrder ?? -1) + 1,
            CreatedAt = time.GetUtcNow(),
        };
        db.FavouriteAreas.Add(favourite);

        try
        {
            await db.SaveChangesAsync(ct);
        }
        catch (DbUpdateException ex) when (ex.IsUniqueViolation())
        {
            // Lost a race with a concurrent add of the same area
            return FavouriteExists(request.AreaId);
        }

        return Created($"/api/users/{userId}/favourites", favourite);
    }

    // DELETE /api/users/{userId}/favourites/{areaId} (bearer) -> 204
    // Only the favourite row is removed; the public Area and its schedule are untouched (FR-FAV-03).
    [HttpDelete("{areaId}")]
    public async Task<IActionResult> RemoveFavourite(string userId, string areaId, CancellationToken ct)
    {
        if (!UserProfileService.IsValidUserId(userId))
        {
            return this.InvalidUserId();
        }

        await profiles.EnsureProfileAsync(userId, ct);
        var favourite = await db.FavouriteAreas.FirstOrDefaultAsync(f => f.UserId == userId && f.AreaId == areaId, ct);
        if (favourite is null)
        {
            return Problem(statusCode: StatusCodes.Status404NotFound, title: "Favourite not found",
                detail: $"Area '{areaId}' is not in this user's favourites.");
        }

        db.FavouriteAreas.Remove(favourite);
        await db.SaveChangesAsync(ct);
        return NoContent();
    }

    private ObjectResult FavouriteExists(string areaId) =>
        Problem(statusCode: StatusCodes.Status409Conflict, title: "Favourite already exists",
            detail: $"Area '{areaId}' is already in this user's favourites.");
}
