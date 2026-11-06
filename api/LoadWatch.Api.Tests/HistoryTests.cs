using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using LoadWatch.Api.Dtos;
using LoadWatch.Api.Models;
using Microsoft.EntityFrameworkCore;

namespace LoadWatch.Api.Tests;

public class HistoryTests(ApiFactory factory) : IClassFixture<ApiFactory>
{
    private static readonly TimeSpan Sast = TimeSpan.FromHours(2);

    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web)
    {
        Converters = { new JsonStringEnumConverter() },
    };

    private static DateTimeOffset SastNow => DateTimeOffset.UtcNow.ToOffset(Sast);

    private static string MonthOf(DateTimeOffset instant) => instant.ToOffset(Sast).ToString("yyyy-MM");

    [Fact]
    public async Task A_month_lists_only_outages_that_have_started_grouped_by_south_african_day()
    {
        var response = await factory.CreateClient(token: null).GetAsync($"/api/areas/eth-berea/history?month={MonthOf(SastNow)}");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var history = (await response.Content.ReadFromJsonAsync<AreaHistory>(Json))!;
        Assert.Equal("eth-berea", history.AreaId);
        Assert.Equal(MonthOf(SastNow), history.Month);
        var outages = history.Days.SelectMany(d => d.Outages).ToList();
        Assert.All(outages, o => Assert.True(o.StartAt <= DateTimeOffset.UtcNow, $"{o.ScheduleId} has not started"));
        Assert.All(history.Days, day => Assert.All(day.Outages, o =>
            Assert.Equal(day.Date, DateOnly.FromDateTime(o.StartAt.ToOffset(Sast).DateTime))));
        Assert.Equal(history.Days.OrderBy(d => d.Date).Select(d => d.Date), history.Days.Select(d => d.Date));
    }

    [Fact]
    public async Task The_previous_month_has_a_past_schedule_from_the_seeded_history()
    {
        var response = await factory.CreateClient(token: null)
            .GetAsync($"/api/areas/eth-berea/history?month={MonthOf(SastNow.AddMonths(-1))}");

        var history = (await response.Content.ReadFromJsonAsync<AreaHistory>(Json))!;
        Assert.NotEmpty(history.Days);
        Assert.All(history.Days, day => Assert.NotEmpty(day.Outages));
    }

    [Fact]
    public async Task A_community_report_appears_on_its_day_and_counts_towards_the_outage_it_falls_in()
    {
        var outage = await PastOutageAsync("eth-musgrave");
        var client = factory.CreateClient(ApiFactory.CreateToken("user-history"));
        var reportedAt = outage.StartAt.AddMinutes(30);
        var posted = await client.PostAsJsonAsync("/api/outage-reports", new
        {
            areaId = "eth-musgrave",
            eventType = "POWER_OFF",
            reportedAt,
            clientCreatedAt = reportedAt,
            clientReportId = "history-report-1",
        });
        Assert.Equal(HttpStatusCode.Created, posted.StatusCode);

        var response = await client.GetAsync($"/api/areas/eth-musgrave/history?month={MonthOf(outage.StartAt)}");

        var body = await response.Content.ReadAsStringAsync();
        Assert.DoesNotContain("user-history", body);
        var history = JsonSerializer.Deserialize<AreaHistory>(body, Json)!;
        var day = history.Days.Single(d => d.Date == DateOnly.FromDateTime(outage.StartAt.ToOffset(Sast).DateTime));
        var report = Assert.Single(day.Reports);
        Assert.Equal(OutageEventType.POWER_OFF, report.EventType);
        Assert.Equal(1, day.Outages.Single(o => o.ScheduleId == outage.ScheduleId).ReportCount);
    }

    [Fact]
    public async Task Reports_for_another_area_are_not_included()
    {
        var outage = await PastOutageAsync("eth-glenwood");
        var reportedAt = outage.StartAt.AddMinutes(10);
        await factory.CreateClient(ApiFactory.CreateToken("user-history-other")).PostAsJsonAsync("/api/outage-reports", new
        {
            areaId = "eth-glenwood",
            eventType = "POWER_OFF",
            reportedAt,
            clientCreatedAt = reportedAt,
            clientReportId = "history-report-other",
        });

        var response = await factory.CreateClient(token: null)
            .GetAsync($"/api/areas/eth-kloof/history?month={MonthOf(outage.StartAt)}");

        var history = (await response.Content.ReadFromJsonAsync<AreaHistory>(Json))!;
        Assert.Empty(history.Days.SelectMany(d => d.Reports));
    }

    [Theory]
    [InlineData("2026-13")]
    [InlineData("november")]
    [InlineData("")]
    public async Task A_malformed_month_is_400_naming_the_field(string month)
    {
        var response = await factory.CreateClient(token: null).GetAsync($"/api/areas/eth-berea/history?month={month}");

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Contains("month", await response.Content.ReadAsStringAsync());
    }

    [Fact]
    public async Task A_future_month_is_400()
    {
        var response = await factory.CreateClient(token: null)
            .GetAsync($"/api/areas/eth-berea/history?month={MonthOf(SastNow.AddMonths(1))}");

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    [Fact]
    public async Task An_unknown_area_is_404()
    {
        var response = await factory.CreateClient(token: null)
            .GetAsync($"/api/areas/eth-nowhere/history?month={MonthOf(SastNow)}");

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }

    // A window from the seeded past schedule that ended at least a day ago
    private async Task<ScheduleEvent> PastOutageAsync(string areaId)
    {
        factory.CreateClient(token: null);
        await using var db = factory.CreateDbContext();
        var cutoff = DateTimeOffset.UtcNow.AddDays(-1);
        return await db.ScheduleEvents.AsNoTracking()
            .Where(e => e.AreaId == areaId && e.EndAt < cutoff)
            .OrderByDescending(e => e.StartAt)
            .FirstAsync();
    }
}
