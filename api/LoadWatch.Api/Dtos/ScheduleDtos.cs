namespace LoadWatch.Api.Dtos;

// ServerTime lets the client correct for device clock drift before running the countdown.
public record NextEvent(
    string AreaId,
    string AreaName,
    string CurrentState,
    int Stage,
    DateTimeOffset NextStartAt,
    DateTimeOffset NextEndAt,
    DateTimeOffset ServerTime,
    DateTimeOffset UpdatedAt);

public record CurrentStage(
    int Stage,
    DateTimeOffset UpdatedAt);
