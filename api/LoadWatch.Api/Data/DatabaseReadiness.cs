namespace LoadWatch.Api.Data;

// Whether startup has finished migrating and seeding the database. Until it has, /health reports 503 and API
// routes answer 503 instead of failing against a schema that may not exist yet.
public class DatabaseReadiness
{
    private readonly TaskCompletionSource _ready = new(TaskCreationOptions.RunContinuationsAsynchronously);

    public bool IsReady => _ready.Task.IsCompletedSuccessfully;

    public bool HasFailed => _ready.Task.IsFaulted;

    // Completes when the database is ready, or throws the startup error
    public Task WhenReady => _ready.Task;

    public void MarkReady() => _ready.TrySetResult();

    public void MarkFailed(Exception error) => _ready.TrySetException(error);
}
