using LoadWatch.Api.Data;
using LoadWatch.Api.Models;
using Microsoft.EntityFrameworkCore;

namespace LoadWatch.Api.Services;

public record StageAlertOutcome(StageChange Change, StageAlertSendResult Result);

// Lets only one check run at a time, so the background service and the demo endpoint never alert the same change twice
public sealed class StageAlertGate
{
    public SemaphoreSlim Lock { get; } = new(1, 1);
}

// One pass of stage-change alerting: reads every active area's stage, sends an alert to the topic of each area
// whose stage moved, and records what was announced. A failed send is not recorded, so the next pass retries it.
public class StageAlertChecker(
    LoadWatchDbContext db,
    IStageAlertSender sender,
    StageAlertGate gate,
    TimeProvider time,
    ILogger<StageAlertChecker> logger)
{
    public Task<IReadOnlyList<StageAlertOutcome>> CheckAsync(CancellationToken ct) => ChangeThenCheckAsync(_ => Task.CompletedTask, ct);

    // Makes a stage change and checks for alerts as one step, so the background service cannot announce the change
    // in between and leave the caller thinking nothing changed
    public async Task<IReadOnlyList<StageAlertOutcome>> ChangeThenCheckAsync(Func<CancellationToken, Task> change, CancellationToken ct)
    {
        await gate.Lock.WaitAsync(ct);
        try
        {
            await change(ct);
            return await CheckLockedAsync(ct);
        }
        finally
        {
            gate.Lock.Release();
        }
    }

    private async Task<IReadOnlyList<StageAlertOutcome>> CheckLockedAsync(CancellationToken ct)
    {
        var national = await db.StageStatus.AsNoTracking().FirstOrDefaultAsync(s => s.Id == StageStatus.SingletonId, ct);
        if (national is null)
        {
            return [];
        }

        var areas = await db.Areas.AsNoTracking()
            .Where(a => a.IsActive)
            .ToDictionaryAsync(a => a.AreaId, a => a.Name, ct);
        var stages = await db.AreaStages.ToDictionaryAsync(s => s.AreaId, ct);
        var detection = StageChangeDetector.Detect(
            national.Stage,
            areas.Keys.Select(id => new AreaStageSnapshot(
                id, stages.GetValueOrDefault(id)?.Stage, stages.GetValueOrDefault(id)?.LastAlertedStage)));

        var now = time.GetUtcNow();
        foreach (var (areaId, stage) in detection.Baselines)
        {
            Record(stages, areaId, stage, now);
        }

        var outcomes = new List<StageAlertOutcome>();
        foreach (var change in detection.Changes)
        {
            logger.LogInformation("Stage for {AreaId} changed from {PreviousStage} to {Stage}",
                change.AreaId, change.PreviousStage, change.Stage);
            var result = await sender.SendAsync(
                StageAlertMessage.TopicFor(change.AreaId), StageAlertMessage.Data(change, areas[change.AreaId], now), ct);
            if (result != StageAlertSendResult.Failed)
            {
                Record(stages, change.AreaId, change.Stage, now);
            }
            outcomes.Add(new StageAlertOutcome(change, result));
        }

        await db.SaveChangesAsync(ct);
        return outcomes;
    }

    private void Record(Dictionary<string, AreaStage> stages, string areaId, int alertedStage, DateTimeOffset now)
    {
        if (!stages.TryGetValue(areaId, out var row))
        {
            row = new AreaStage { AreaId = areaId };
            db.AreaStages.Add(row);
            stages[areaId] = row;
        }
        row.LastAlertedStage = alertedStage;
        row.UpdatedAt = now;
    }
}
