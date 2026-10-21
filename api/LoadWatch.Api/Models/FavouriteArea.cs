namespace LoadWatch.Api.Models;

// Joins a user to an area. Unique on (UserId, AreaId), and deleting a favourite never deletes the Area.
public class FavouriteArea
{
    public string FavouriteId { get; set; } = string.Empty;
    public string UserId { get; set; } = string.Empty;
    public string AreaId { get; set; } = string.Empty;

    // Optional display label such as Home or Work
    public string Label { get; set; } = string.Empty;

    public int SortOrder { get; set; }
    public DateTimeOffset CreatedAt { get; set; }
}
