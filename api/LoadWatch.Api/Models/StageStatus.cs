namespace LoadWatch.Api.Models;

// Single-row table holding the national load-shedding stage. Id is always 1.
public class StageStatus
{
    public const int SingletonId = 1;

    public int Id { get; set; } = SingletonId;
    public int Stage { get; set; }
    public DateTimeOffset UpdatedAt { get; set; }
}
