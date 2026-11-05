namespace LoadWatch.Api.Models;

// Per-area stage tracking for stage-change alerts. Stage, when set, overrides the national stage for this area
// (for example a municipality running its own stage). LastAlertedStage is the stage the area's subscribers were
// last told about, so a change is only announced once.
public class AreaStage
{
    public string AreaId { get; set; } = string.Empty;

    // Null means the area follows the national stage
    public int? Stage { get; set; }

    public int? LastAlertedStage { get; set; }
    public DateTimeOffset UpdatedAt { get; set; }
}
