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

/// <summary>The national load-shedding stage and when it last changed.</summary>
public record CurrentStage(
    int Stage,
    DateTimeOffset UpdatedAt);

// Body for the demo endpoint that sets an area's stage. A null stage returns the area to the national stage.
public record TestStageChangeRequest
{
    public int? Stage { get; init; }
}

// Alert is Sent, Skipped (Firebase not configured), Failed, or NoChange when the area was already at that stage
public record TestStageChangeResult(
    string AreaId,
    int PreviousStage,
    int Stage,
    string Alert);
