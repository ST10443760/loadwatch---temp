using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using Microsoft.EntityFrameworkCore;

namespace LoadWatch.Api.Tests;

/// <summary>
/// Integration tests for POST /api/sync: idempotent reports, duplicates, settings conflicts, favourite order
/// and batch limits.
/// </summary>
public class SyncTests(ApiFactory factory) : IClassFixture<ApiFactory>
{
    private const string Route = "/api/sync";

    [Fact]
    public async Task Queued_reports_are_stored_once_even_when_the_same_batch_is_sent_again()
    {
        var client = factory.CreateClient(ApiFactory.CreateToken("user-sync-reports"));
        var batch = new
        {
            reports = new[]
            {
                Report("sync-r1", "eth-berea", "POWER_OFF", DateTimeOffset.UtcNow.AddHours(-3)),
                Report("sync-r2", "eth-berea", "POWER_BACK_ON", DateTimeOffset.UtcNow.AddHours(-1)),
            },
        };

        var first = await SyncAsync(client, batch);
        var retry = await SyncAsync(client, batch);

        Assert.Equal(["sync-r1", "sync-r2"], Ids(first, "accepted"));
        Assert.Equal(["sync-r1", "sync-r2"], Ids(retry, "accepted"));
        await using var db = factory.CreateDbContext();
        Assert.Equal(2, await db.OutageReports.CountAsync(r => r.UserId == "user-sync-reports"));
    }

    [Fact]
    public async Task A_second_report_of_the_same_event_inside_the_window_is_a_duplicate_and_not_stored()
    {
        var client = factory.CreateClient(ApiFactory.CreateToken("user-sync-duplicate"));
        var reportedAt = DateTimeOffset.UtcNow.AddMinutes(-30);

        var result = await SyncAsync(client, new
        {
            reports = new[]
            {
                Report("sync-d1", "eth-umhlanga", "POWER_OFF", reportedAt),
                Report("sync-d2", "eth-umhlanga", "POWER_OFF", reportedAt.AddMinutes(2)),
            },
        });

        Assert.Equal(["sync-d1"], Ids(result, "accepted"));
        Assert.Equal(["sync-d2"], Ids(result, "duplicates"));
        await using var db = factory.CreateDbContext();
        Assert.Equal(1, await db.OutageReports.CountAsync(r => r.UserId == "user-sync-duplicate"));
    }

    [Fact]
    public async Task An_invalid_report_is_rejected_with_a_reason_without_failing_the_rest()
    {
        var client = factory.CreateClient(ApiFactory.CreateToken("user-sync-invalid"));

        var result = await SyncAsync(client, new
        {
            reports = new[]
            {
                Report("sync-bad-area", "eth-nowhere", "POWER_OFF", DateTimeOffset.UtcNow.AddMinutes(-5)),
                Report("sync-future", "eth-kloof", "POWER_OFF", DateTimeOffset.UtcNow.AddHours(2)),
                Report("sync-good", "eth-kloof", "POWER_BACK_ON", DateTimeOffset.UtcNow.AddMinutes(-5)),
            },
        });

        Assert.Equal(["sync-good"], Ids(result, "accepted"));
        var rejected = result.GetProperty("rejected").EnumerateArray().ToDictionary(
            r => r.GetProperty("id").GetString()!, r => r.GetProperty("reason").GetString()!);
        Assert.Contains("eth-nowhere", rejected["sync-bad-area"]);
        Assert.Contains("future", rejected["sync-future"]);
    }

    [Fact]
    public async Task A_settings_change_newer_than_the_server_copy_is_applied()
    {
        var client = factory.CreateClient(ApiFactory.CreateToken("user-sync-settings-new"));
        await client.GetAsync("/api/users/user-sync-settings-new/settings");

        var result = await SyncAsync(client, new { settings = Settings("change-new", "af", DateTimeOffset.UtcNow) });

        Assert.Equal(["change-new"], Ids(result, "accepted"));
        Assert.Equal("af", result.GetProperty("settings").GetProperty("languageCode").GetString());
    }

    [Fact]
    public async Task A_settings_change_older_than_the_server_copy_loses_and_the_server_copy_comes_back()
    {
        var client = factory.CreateClient(ApiFactory.CreateToken("user-sync-settings-old"));
        var changedOffline = DateTimeOffset.UtcNow.AddMinutes(-10);
        // Saved later from another phone that was online
        await client.PutAsJsonAsync("/api/users/user-sync-settings-old/settings", Settings("ignored", "zu", DateTimeOffset.UtcNow));

        var result = await SyncAsync(client, new { settings = Settings("change-old", "af", changedOffline) });

        Assert.Equal(["change-old"], Ids(result, "superseded"));
        Assert.Equal("zu", result.GetProperty("settings").GetProperty("languageCode").GetString());
    }

    [Fact]
    public async Task An_invalid_settings_change_is_rejected()
    {
        var client = factory.CreateClient(ApiFactory.CreateToken("user-sync-settings-bad"));

        var result = await SyncAsync(client, new { settings = Settings("change-bad", "fr", DateTimeOffset.UtcNow) });

        var rejection = Assert.Single(result.GetProperty("rejected").EnumerateArray());
        Assert.Equal("change-bad", rejection.GetProperty("id").GetString());
        Assert.Contains("languageCode", rejection.GetProperty("reason").GetString());
    }

    [Fact]
    public async Task Favourite_changes_apply_in_order_and_the_final_list_comes_back()
    {
        var client = factory.CreateClient(ApiFactory.CreateToken("user-sync-favourites"));
        var start = DateTimeOffset.UtcNow.AddMinutes(-10);

        var result = await SyncAsync(client, new
        {
            favourites = new object[]
            {
                new { changeId = "f1", areaId = "eth-berea", label = "Home", removed = false, changedAt = start },
                new { changeId = "f2", areaId = "eth-kloof", label = "Work", removed = false, changedAt = start.AddMinutes(1) },
                new { changeId = "f3", areaId = "eth-kloof", label = (string?)null, removed = true, changedAt = start.AddMinutes(2) },
                new { changeId = "f4", areaId = "eth-berea", label = "Mum", removed = false, changedAt = start.AddMinutes(3) },
            },
        });
        // A retried removal of something already gone is still fine
        var retry = await SyncAsync(client, new
        {
            favourites = new[] { new { changeId = "f3", areaId = "eth-kloof", label = (string?)null, removed = true, changedAt = start.AddMinutes(2) } },
        });

        Assert.Equal(["f1", "f2", "f3", "f4"], Ids(result, "accepted"));
        Assert.Equal(["f3"], Ids(retry, "accepted"));
        var favourite = Assert.Single(result.GetProperty("favourites").EnumerateArray());
        Assert.Equal("eth-berea", favourite.GetProperty("areaId").GetString());
        Assert.Equal("Mum", favourite.GetProperty("label").GetString());
    }

    [Fact]
    public async Task A_favourite_for_an_unknown_area_is_rejected()
    {
        var client = factory.CreateClient(ApiFactory.CreateToken("user-sync-favourite-bad"));

        var result = await SyncAsync(client, new
        {
            favourites = new[] { new { changeId = "f-bad", areaId = "eth-nowhere", label = "", removed = false, changedAt = DateTimeOffset.UtcNow } },
        });

        Assert.Equal("f-bad", Assert.Single(result.GetProperty("rejected").EnumerateArray()).GetProperty("id").GetString());
    }

    [Fact]
    public async Task More_than_the_maximum_number_of_reports_is_400()
    {
        var client = factory.CreateClient(ApiFactory.CreateToken("user-sync-too-many"));
        var reports = Enumerable.Range(0, 101)
            .Select(i => Report($"sync-many-{i}", "eth-berea", "POWER_OFF", DateTimeOffset.UtcNow.AddMinutes(-i * 20)));

        var response = await client.PostAsJsonAsync(Route, new { reports });

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Contains("reports", await response.Content.ReadAsStringAsync());
    }

    [Fact]
    public async Task Syncing_without_signing_in_is_401()
    {
        var response = await factory.CreateClient(token: null).PostAsJsonAsync(Route, new { reports = Array.Empty<object>() });

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    private static async Task<JsonElement> SyncAsync(HttpClient client, object batch)
    {
        var response = await client.PostAsJsonAsync(Route, batch);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        return await response.Content.ReadFromJsonAsync<JsonElement>();
    }

    private static string[] Ids(JsonElement result, string list) =>
        result.GetProperty(list).EnumerateArray().Select(e => e.GetString()!).ToArray();

    private static object Report(string clientReportId, string areaId, string eventType, DateTimeOffset reportedAt) =>
        new { clientReportId, areaId, eventType, reportedAt, clientCreatedAt = reportedAt };

    private static object Settings(string changeId, string languageCode, DateTimeOffset changedAt) => new
    {
        changeId,
        changedAt,
        defaultAreaId = (string?)null,
        notificationLeadMinutes = 30,
        notificationsEnabled = true,
        enabledAlertTypes = "POWER_OFF,STAGE_CHANGE",
        languageCode,
        themePreference = "dark",
        biometricEnabled = false,
    };
}
