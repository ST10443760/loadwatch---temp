namespace LoadWatch.Api.Services;

// Bound from the "OutageReports" section of appsettings.json
public class OutageReportOptions
{
    public const string SectionName = "OutageReports";

    // A second report of the same event by the same user for the same area inside this window returns 409
    public int DuplicateWindowMinutes { get; set; } = 10;
}
