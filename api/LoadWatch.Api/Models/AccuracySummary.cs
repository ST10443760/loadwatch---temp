namespace LoadWatch.Api.Models;

// One row per area. AccuracyScore is between 0.00 and 1.00, recomputed on report ingestion.
public class AccuracySummary
{
    public string AreaId { get; set; } = string.Empty;
    public decimal AccuracyScore { get; set; }
    public int SampleSize { get; set; }
    public int WindowHours { get; set; }
    public DateTimeOffset CalculatedAt { get; set; }
}
