using LoadWatch.Api.Services;

namespace LoadWatch.Api.Tests;

public class StageChangeDetectorTests
{
    [Fact]
    public void An_area_with_its_own_stage_uses_it_instead_of_the_national_stage()
    {
        Assert.Equal(6, StageChangeDetector.EffectiveStage(nationalStage: 2, ownStage: 6));
        Assert.Equal(0, StageChangeDetector.EffectiveStage(nationalStage: 2, ownStage: 0));
        Assert.Equal(2, StageChangeDetector.EffectiveStage(nationalStage: 2, ownStage: null));
    }

    [Fact]
    public void A_stage_that_differs_from_the_last_alert_is_a_change()
    {
        var detection = StageChangeDetector.Detect(2, [new AreaStageSnapshot("eth-berea", OwnStage: 4, LastAlertedStage: 2)]);

        Assert.Equal([new StageChange("eth-berea", 2, 4)], detection.Changes);
        Assert.Empty(detection.Baselines);
    }

    [Fact]
    public void A_stage_that_matches_the_last_alert_is_not_announced_again()
    {
        var detection = StageChangeDetector.Detect(2, [new AreaStageSnapshot("eth-berea", OwnStage: null, LastAlertedStage: 2)]);

        Assert.Empty(detection.Changes);
        Assert.Empty(detection.Baselines);
    }

    [Fact]
    public void An_area_never_alerted_before_is_recorded_without_an_alert()
    {
        var detection = StageChangeDetector.Detect(3, [new AreaStageSnapshot("eth-kloof", OwnStage: null, LastAlertedStage: null)]);

        Assert.Empty(detection.Changes);
        Assert.Equal([("eth-kloof", 3)], detection.Baselines);
    }

    [Fact]
    public void A_national_stage_change_reaches_every_area_that_follows_it_but_not_one_with_its_own_stage()
    {
        var detection = StageChangeDetector.Detect(5,
        [
            new AreaStageSnapshot("eth-berea", OwnStage: null, LastAlertedStage: 2),
            new AreaStageSnapshot("eth-umhlanga", OwnStage: null, LastAlertedStage: 2),
            new AreaStageSnapshot("eth-kloof", OwnStage: 2, LastAlertedStage: 2),
        ]);

        Assert.Equal(
            [new StageChange("eth-berea", 2, 5), new StageChange("eth-umhlanga", 2, 5)],
            detection.Changes);
    }

    [Fact]
    public void Load_shedding_ending_is_a_change_to_stage_0()
    {
        var detection = StageChangeDetector.Detect(0, [new AreaStageSnapshot("eth-berea", OwnStage: null, LastAlertedStage: 4)]);

        Assert.Equal([new StageChange("eth-berea", 4, 0)], detection.Changes);
    }

    [Fact]
    public void Clearing_an_area_stage_returns_it_to_the_national_stage_and_announces_that()
    {
        var detection = StageChangeDetector.Detect(2, [new AreaStageSnapshot("eth-berea", OwnStage: null, LastAlertedStage: 6)]);

        Assert.Equal([new StageChange("eth-berea", 6, 2)], detection.Changes);
    }
}
