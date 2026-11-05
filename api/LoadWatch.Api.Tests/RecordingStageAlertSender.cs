using System.Collections.Concurrent;
using LoadWatch.Api.Services;

namespace LoadWatch.Api.Tests;

// Stands in for Firebase in the tests: records each alert instead of sending it
public class RecordingStageAlertSender : IStageAlertSender
{
    public ConcurrentQueue<(string Topic, IReadOnlyDictionary<string, string> Data)> Sent { get; } = new();

    public StageAlertSendResult NextResult { get; set; } = StageAlertSendResult.Sent;

    public Task<StageAlertSendResult> SendAsync(string topic, IReadOnlyDictionary<string, string> data, CancellationToken ct)
    {
        if (NextResult == StageAlertSendResult.Sent)
        {
            Sent.Enqueue((topic, data));
        }
        return Task.FromResult(NextResult);
    }
}
