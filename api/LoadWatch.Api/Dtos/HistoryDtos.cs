namespace LoadWatch.Api.Dtos;

// Final POE. Served from the NoSQL store.
public record HistoryDay(
    string AreaId,
    DateOnly Date,
    IReadOnlyList<TimeRange> ScheduledWindows,
    IReadOnlyList<TimeRange> ObservedWindows,
    bool Matched);

public record TimeRange(
    DateTimeOffset StartAt,
    DateTimeOffset EndAt);
