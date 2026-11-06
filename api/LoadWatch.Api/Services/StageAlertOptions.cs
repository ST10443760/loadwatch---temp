namespace LoadWatch.Api.Services;

/// <summary>
/// Settings for stage-change alerts, bound from the StageAlerts configuration section (StageAlerts__* on
/// Azure).
/// </summary>
public class StageAlertOptions
{
    public const string SectionName = "StageAlerts";

    // How often the background service looks for stage changes
    public int PollSeconds { get; set; } = 30;

    // Shared secret for the demo endpoint that sets an area's stage. Empty turns the endpoint off. Set it on Azure
    // as the app setting StageAlerts__TestKey; it is never committed.
    public string? TestKey { get; set; }
}
