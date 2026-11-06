using LoadWatch.Api.Auth;
using LoadWatch.Api.Data;
using LoadWatch.Api.Dtos;
using LoadWatch.Api.Models;
using LoadWatch.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LoadWatch.Api.Controllers;

/// <summary>
/// Applies a batch of changes the phone made offline: community reports, favourite changes and the latest
/// settings. Each item gets its own outcome, so one bad item never fails the batch.
/// </summary>
[ApiController]
[Authorize]
[Route("api/sync")]
public class SyncController(
    LoadWatchDbContext db,
    OutageReportService reports,
    AccuracyService accuracy,
    TimeProvider time,
    ILogger<SyncController> logger) : ControllerBase
{
    public const int MaxItems = 100;

    // POST /api/sync (bearer) -> 200 SyncResult, or 400 when the batch is too large
    // Safe to send again: a report is keyed by its clientReportId, a favourite change sets the final state, and a
    // settings change only applies if it is newer than the server's copy.
    [HttpPost]
    public async Task<ActionResult<SyncResult>> Sync([FromBody] SyncBatch batch, CancellationToken ct)
    {
        var userId = User.GetUserId();
        var reportItems = batch.Reports ?? [];
        var favouriteItems = batch.Favourites ?? [];
        if (reportItems.Count > MaxItems)
        {
            ModelState.AddModelError("reports", $"Send at most {MaxItems} reports at a time.");
        }
        if (favouriteItems.Count > MaxItems)
        {
            ModelState.AddModelError("favourites", $"Send at most {MaxItems} favourite changes at a time.");
        }
        if (!ModelState.IsValid)
        {
            return ValidationProblem(ModelState);
        }

        var outcome = new Outcome();
        await SyncReportsAsync(userId, reportItems, outcome, ct);
        await SyncFavouritesAsync(userId, favouriteItems, outcome, ct);
        if (batch.Settings is not null)
        {
            await SyncSettingsAsync(userId, batch.Settings, outcome, ct);
        }

        var settings = batch.Settings is null
            ? null
            : await db.UserSettings.AsNoTracking().FirstOrDefaultAsync(s => s.UserId == userId, ct);
        var favourites = favouriteItems.Count == 0
            ? null
            : await db.FavouriteAreas.AsNoTracking()
                .Where(f => f.UserId == userId)
                .OrderBy(f => f.SortOrder)
                .ThenBy(f => f.CreatedAt)
                .ToListAsync(ct);

        logger.LogInformation(
            "Sync for {UserId}: {Accepted} accepted, {Duplicates} duplicates, {Superseded} superseded, {Rejected} rejected",
            userId, outcome.Accepted.Count, outcome.Duplicates.Count, outcome.Superseded.Count, outcome.Rejected.Count);
        return new SyncResult(
            outcome.Accepted, outcome.Duplicates, outcome.Superseded, outcome.Rejected, settings, favourites, time.GetUtcNow());
    }

    /// <summary>
    /// Stores each queued report through the shared report rules and sorts it into accepted, duplicate or
    /// rejected.
    /// </summary>
    private async Task SyncReportsAsync(
        string userId, IReadOnlyList<OutageReportCreate> items, Outcome outcome, CancellationToken ct)
    {
        var changedAreas = new HashSet<string>();
        foreach (var item in items)
        {
            var id = item.ClientReportId ?? string.Empty;
            var result = await reports.SubmitAsync(userId, item, ct);
            switch (result.Outcome)
            {
                case ReportSubmissionOutcome.Created:
                    changedAreas.Add(result.Report!.AreaId);
                    outcome.Accepted.Add(id);
                    break;
                case ReportSubmissionOutcome.AlreadyStored:
                    outcome.Accepted.Add(id);
                    break;
                case ReportSubmissionOutcome.Duplicate:
                    outcome.Duplicates.Add(id);
                    break;
                default:
                    outcome.Rejected.Add(new SyncRejection(id, string.Join(" ", result.Errors!.Values)));
                    break;
            }
        }

        foreach (var areaId in changedAreas)
        {
            await accuracy.RecomputeAsync(areaId, ct);
        }
    }

    /// <summary>
    /// Applies favourite changes in the order they were made on the phone. An unknown area or an invalid label
    /// is rejected.
    /// </summary>
    private async Task SyncFavouritesAsync(
        string userId, IReadOnlyList<FavouriteChange> items, Outcome outcome, CancellationToken ct)
    {
        foreach (var change in items.OrderBy(c => c.ChangedAt ?? DateTimeOffset.MaxValue))
        {
            var id = change.ChangeId ?? string.Empty;
            var existing = await db.FavouriteAreas.FirstOrDefaultAsync(f => f.UserId == userId && f.AreaId == change.AreaId, ct);
            if (change.Removed)
            {
                // Already gone counts as done, so a retried removal is harmless
                if (existing is not null)
                {
                    db.FavouriteAreas.Remove(existing);
                    await db.SaveChangesAsync(ct);
                }
                outcome.Accepted.Add(id);
                continue;
            }

            var label = change.Label?.Trim() ?? string.Empty;
            if (label.Length > FavouritesController.MaxLabelLength)
            {
                outcome.Rejected.Add(new SyncRejection(id, $"label cannot be longer than {FavouritesController.MaxLabelLength} characters."));
                continue;
            }
            if (string.IsNullOrEmpty(change.AreaId) || !await db.Areas.AnyAsync(a => a.AreaId == change.AreaId, ct))
            {
                outcome.Rejected.Add(new SyncRejection(id, $"No area exists with id '{change.AreaId}'."));
                continue;
            }

            // Saving an area that is already a favourite sets its label, which is how a relabel made offline arrives
            if (existing is not null)
            {
                existing.Label = label;
            }
            else
            {
                var lastSortOrder = await db.FavouriteAreas.Where(f => f.UserId == userId).MaxAsync(f => (int?)f.SortOrder, ct);
                db.FavouriteAreas.Add(new FavouriteArea
                {
                    FavouriteId = Guid.NewGuid().ToString(),
                    UserId = userId,
                    AreaId = change.AreaId,
                    Label = label,
                    SortOrder = (lastSortOrder ?? -1) + 1,
                    CreatedAt = time.GetUtcNow(),
                });
            }

            try
            {
                await db.SaveChangesAsync(ct);
            }
            catch (DbUpdateException ex) when (ex.IsUniqueViolation())
            {
                // A concurrent request saved the same area first, which is the state this change wanted
                db.ChangeTracker.Clear();
            }
            outcome.Accepted.Add(id);
        }
    }

    // Last write wins: the change applies only if it was made after the server's copy was last written
    private async Task SyncSettingsAsync(string userId, SettingsChange change, Outcome outcome, CancellationToken ct)
    {
        var id = change.ChangeId ?? string.Empty;
        var errors = await UserSettingsRules.ValidateAsync(change, db, ct);
        if (errors.Count > 0)
        {
            outcome.Rejected.Add(new SyncRejection(id, string.Join(" ", errors.Values)));
            return;
        }

        // A phone clock running ahead must not make its change win forever
        var now = time.GetUtcNow();
        var changedAt = change.ChangedAt is { } at && at < now ? at : now;
        var settings = await db.UserSettings.FirstOrDefaultAsync(s => s.UserId == userId, ct);
        if (settings is not null && settings.UpdatedAt > changedAt)
        {
            logger.LogInformation("Settings change {ChangeId} from {ChangedAt} is older than the server's copy from {UpdatedAt}",
                id, changedAt, settings.UpdatedAt);
            outcome.Superseded.Add(id);
            return;
        }

        if (settings is null)
        {
            settings = new UserSettings { UserId = userId };
            db.UserSettings.Add(settings);
        }
        UserSettingsRules.Apply(settings, change, changedAt);
        await db.SaveChangesAsync(ct);
        outcome.Accepted.Add(id);
    }

    /// <summary>
    /// Collects the per-item results while the batch is applied, before they become the SyncResult.
    /// </summary>
    private sealed class Outcome
    {
        public List<string> Accepted { get; } = [];
        public List<string> Duplicates { get; } = [];
        public List<string> Superseded { get; } = [];
        public List<SyncRejection> Rejected { get; } = [];
    }
}
