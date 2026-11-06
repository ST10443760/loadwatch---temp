using LoadWatch.Api.Models;
using Microsoft.AspNetCore.Mvc.ModelBinding.Validation;

namespace LoadWatch.Api.Dtos;

// POST /api/sync: everything a phone queued while it was offline, sent in one request. Each item is checked on its
// own, so one bad item is rejected without failing the rest; that is why the items skip automatic validation.
public record SyncBatch
{
    // Keyed by clientReportId, which makes a retried report safe
    [ValidateNever]
    public IReadOnlyList<OutageReportCreate>? Reports { get; init; }

    // Applied in the order they were made on the phone
    [ValidateNever]
    public IReadOnlyList<FavouriteChange>? Favourites { get; init; }

    // Only the latest settings change is queued, so there is at most one
    [ValidateNever]
    public SettingsChange? Settings { get; init; }
}

// A favourite saved (or relabelled) or removed on the phone
public record FavouriteChange
{
    public string ChangeId { get; init; } = null!;
    public string AreaId { get; init; } = null!;
    public string? Label { get; init; }
    public bool Removed { get; init; }
    public DateTimeOffset? ChangedAt { get; init; }
}

// The whole settings form as it was saved on the phone, and when. Last write wins against the server's copy.
public record SettingsChange : UserSettingsUpdate
{
    public string ChangeId { get; init; } = null!;
    public DateTimeOffset? ChangedAt { get; init; }
}

// The outcome of every item, keyed by the id it was sent with (clientReportId for reports, changeId otherwise).
// The server's copy wins any conflict, so the settings and favourites sent back replace the phone's.
public record SyncResult(
    // Stored, or already stored by an earlier attempt
    IReadOnlyList<string> Accepted,
    // Reports of an event this user already reported for the area inside the duplicate window; nothing was stored
    IReadOnlyList<string> Duplicates,
    // A settings change older than the server's copy, which was kept
    IReadOnlyList<string> Superseded,
    IReadOnlyList<SyncRejection> Rejected,
    // Sent back whenever settings were in the batch
    UserSettings? Settings,
    // Sent back whenever favourites were in the batch
    IReadOnlyList<FavouriteArea>? Favourites,
    DateTimeOffset SyncedAt);

/// <summary>
/// A batch item the API refused, with the item's ID and the reason, so the phone stops retrying it.
/// </summary>
public record SyncRejection(
    string Id,
    string Reason);
