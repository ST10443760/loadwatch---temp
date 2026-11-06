namespace LoadWatch.Api.Services;

// What one area looks like to the detector: its own stage if it has one, and the stage last announced for it
public record AreaStageSnapshot(string AreaId, int? OwnStage, int? LastAlertedStage);

/// <summary>An area whose stage differs from the one last announced, and needs an alert.</summary>
public record StageChange(string AreaId, int PreviousStage, int Stage);

/// <summary>
/// The detector's result: the changes to announce, and the areas seen for the first time that only need a
/// baseline recorded.
/// </summary>
public record StageDetection(
    // Areas whose stage moved since subscribers were last told; each needs an alert
    IReadOnlyList<StageChange> Changes,
    // Areas seen for the first time: their stage is recorded without an alert, so a fresh deploy or a newly
    // added area does not notify everyone at once
    IReadOnlyList<(string AreaId, int Stage)> Baselines);

// Pure logic, kept apart from the database and Firebase so it can be unit tested
public static class StageChangeDetector
{
    // An area's stage is its own when set, otherwise the national stage
    public static int EffectiveStage(int nationalStage, int? ownStage) => ownStage ?? nationalStage;

    /// <summary>
    /// Compares each area's effective stage, its own or the national one, with the stage last announced for it.
    /// </summary>
    public static StageDetection Detect(int nationalStage, IEnumerable<AreaStageSnapshot> areas)
    {
        var changes = new List<StageChange>();
        var baselines = new List<(string AreaId, int Stage)>();

        foreach (var area in areas)
        {
            var stage = EffectiveStage(nationalStage, area.OwnStage);
            if (area.LastAlertedStage is not { } previous)
            {
                baselines.Add((area.AreaId, stage));
            }
            else if (previous != stage)
            {
                changes.Add(new StageChange(area.AreaId, previous, stage));
            }
        }

        return new StageDetection(changes, baselines);
    }
}
