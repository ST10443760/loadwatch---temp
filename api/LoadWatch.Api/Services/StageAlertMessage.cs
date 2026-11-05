namespace LoadWatch.Api.Services;

// The FCM message for a stage change. It is data only, with no notification block, so the phone builds the text
// itself: in the user's language, and only if their alert settings allow it.
public static class StageAlertMessage
{
    public const string Type = "STAGE_CHANGE";

    // Each area has its own topic; the phone subscribes to the topic of the user's selected area
    public static string TopicFor(string areaId) => $"area-{areaId}";

    public static IReadOnlyDictionary<string, string> Data(StageChange change, string areaName, DateTimeOffset changedAt) =>
        new Dictionary<string, string>
        {
            ["type"] = Type,
            ["areaId"] = change.AreaId,
            ["areaName"] = areaName,
            ["previousStage"] = change.PreviousStage.ToString(),
            ["stage"] = change.Stage.ToString(),
            ["changedAt"] = changedAt.UtcDateTime.ToString("yyyy-MM-dd'T'HH:mm:ss'Z'"),
        };
}
