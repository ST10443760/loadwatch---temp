namespace LoadWatch.Api.Models;

// One interruption window for an area. EndAt must be later than StartAt.
public class ScheduleEvent
{
    public string ScheduleId { get; set; } = string.Empty;
    public string AreaId { get; set; } = string.Empty;
    public int Stage { get; set; }
    public DateTimeOffset StartAt { get; set; }
    public DateTimeOffset EndAt { get; set; }
    public string Source { get; set; } = string.Empty;

    // Used by the client to detect stale schedule data
    public DateTimeOffset UpdatedAt { get; set; }
}
