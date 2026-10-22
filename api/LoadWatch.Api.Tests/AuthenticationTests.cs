using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using Microsoft.EntityFrameworkCore;

namespace LoadWatch.Api.Tests;

public class AuthenticationTests(ApiFactory factory) : IClassFixture<ApiFactory>
{
    private const string AreaId = "eth-berea";

    [Fact]
    public async Task Anonymous_routes_work_without_a_token()
    {
        var client = factory.CreateClient(token: null);

        Assert.Equal(HttpStatusCode.OK, (await client.GetAsync("/api/stages/current")).StatusCode);
        Assert.Equal(HttpStatusCode.OK, (await client.GetAsync($"/api/schedules/next?areaId={AreaId}")).StatusCode);
        Assert.Equal(HttpStatusCode.OK, (await client.GetAsync($"/api/areas/{AreaId}/accuracy")).StatusCode);
    }

    [Fact]
    public async Task A_bearer_route_without_a_token_is_401_problem_details()
    {
        var response = await factory.CreateClient(token: null).GetAsync("/api/users/user-a/settings");

        await AssertProblemAsync(response, HttpStatusCode.Unauthorized);
    }

    [Fact]
    public async Task An_expired_token_is_401_and_says_it_expired()
    {
        var token = ApiFactory.CreateToken("user-a", expires: DateTime.UtcNow.AddMinutes(-10));

        var response = await factory.CreateClient(token).GetAsync("/api/users/user-a/settings");

        var problem = await AssertProblemAsync(response, HttpStatusCode.Unauthorized);
        Assert.Contains("expired", problem.GetProperty("detail").GetString());
    }

    [Fact]
    public async Task A_token_for_another_firebase_project_is_401()
    {
        var token = ApiFactory.CreateToken("user-a", audience: "some-other-project");

        var response = await factory.CreateClient(token).GetAsync("/api/users/user-a/settings");

        await AssertProblemAsync(response, HttpStatusCode.Unauthorized);
    }

    [Fact]
    public async Task The_first_request_creates_the_profile_from_the_token_and_later_requests_update_last_seen()
    {
        var client = factory.CreateClient(ApiFactory.CreateToken("user-profile", "Thandi Mokoena", "thandi@example.com"));

        Assert.Equal(HttpStatusCode.OK, (await client.GetAsync("/api/users/user-profile/settings")).StatusCode);
        await using var db = factory.CreateDbContext();
        var created = await db.UserProfiles.AsNoTracking().SingleAsync(u => u.UserId == "user-profile");
        Assert.Equal("Thandi Mokoena", created.DisplayName);
        Assert.Equal("thandi@example.com", created.Email);

        await Task.Delay(50);
        Assert.Equal(HttpStatusCode.OK, (await client.GetAsync("/api/users/user-profile/favourites")).StatusCode);
        var updated = await db.UserProfiles.AsNoTracking().SingleAsync(u => u.UserId == "user-profile");
        Assert.Equal(created.CreatedAt, updated.CreatedAt);
        Assert.True(updated.LastSeenAt > created.LastSeenAt);
    }

    [Theory]
    [InlineData("GET", "/api/users/someone-else/settings")]
    [InlineData("PUT", "/api/users/someone-else/settings")]
    [InlineData("GET", "/api/users/someone-else/favourites")]
    [InlineData("POST", "/api/users/someone-else/favourites")]
    [InlineData("DELETE", "/api/users/someone-else/favourites/eth-berea")]
    public async Task A_userId_in_the_path_that_is_not_the_signed_in_user_is_403(string method, string path)
    {
        var client = factory.CreateClient(ApiFactory.CreateToken("user-a"));
        var request = new HttpRequestMessage(new HttpMethod(method), path) { Content = JsonContent.Create(new { }) };

        var response = await client.SendAsync(request);

        await AssertProblemAsync(response, HttpStatusCode.Forbidden);
    }

    [Fact]
    public async Task An_outage_report_belongs_to_the_token_user_and_ignores_the_old_header()
    {
        var client = factory.CreateClient(ApiFactory.CreateToken("user-reporter"));
        client.DefaultRequestHeaders.Add("X-User-Id", "someone-else");

        var response = await client.PostAsJsonAsync("/api/outage-reports", NewReport("report-1"));

        Assert.Equal(HttpStatusCode.Created, response.StatusCode);
        var report = await response.Content.ReadFromJsonAsync<JsonElement>();
        Assert.Equal("user-reporter", report.GetProperty("userId").GetString());
    }

    [Fact]
    public async Task The_public_reports_list_does_not_expose_user_ids()
    {
        var client = factory.CreateClient(ApiFactory.CreateToken("user-public"));
        Assert.Equal(HttpStatusCode.Created, (await client.PostAsJsonAsync("/api/outage-reports", NewReport("report-public"))).StatusCode);

        var reports = await factory.CreateClient(token: null)
            .GetFromJsonAsync<JsonElement>($"/api/areas/{AreaId}/outage-reports?since=2000-01-01");

        Assert.NotEmpty(reports.EnumerateArray());
        Assert.All(reports.EnumerateArray(), report => Assert.False(report.TryGetProperty("userId", out _)));
    }

    [Fact]
    public async Task An_invalid_eventType_is_a_clean_400()
    {
        var client = factory.CreateClient(ApiFactory.CreateToken("user-a"));
        var body = new
        {
            areaId = AreaId,
            eventType = "POWER_MAYBE",
            reportedAt = DateTime.UtcNow,
            clientCreatedAt = DateTime.UtcNow,
            clientReportId = "bad-event",
        };

        var errors = await AssertValidationErrorsAsync(await client.PostAsJsonAsync("/api/outage-reports", body));

        Assert.Equal(["eventType must be POWER_OFF or POWER_BACK_ON"], ErrorsFor(errors, "eventType"));
        Assert.Single(errors.EnumerateObject());
    }

    [Fact]
    public async Task An_invalid_reportedAt_is_a_clean_400()
    {
        var client = factory.CreateClient(ApiFactory.CreateToken("user-a"));
        var body = new
        {
            areaId = AreaId,
            eventType = "POWER_OFF",
            reportedAt = "yesterday",
            clientCreatedAt = DateTime.UtcNow,
            clientReportId = "bad-date",
        };

        var errors = await AssertValidationErrorsAsync(await client.PostAsJsonAsync("/api/outage-reports", body));

        Assert.Equal(["reportedAt must be an ISO-8601 date and time"], ErrorsFor(errors, "reportedAt"));
    }

    [Fact]
    public async Task An_invalid_eventType_inside_a_sync_batch_names_the_item()
    {
        var client = factory.CreateClient(ApiFactory.CreateToken("user-a"));
        var body = new { reports = new[] { new { areaId = AreaId, eventType = "power_off" } } };

        var errors = await AssertValidationErrorsAsync(await client.PostAsJsonAsync("/api/sync/outage-reports", body));

        Assert.Equal(["eventType must be POWER_OFF or POWER_BACK_ON"], ErrorsFor(errors, "reports[0].eventType"));
    }

    private static object NewReport(string clientReportId) => new
    {
        areaId = AreaId,
        eventType = "POWER_OFF",
        reportedAt = DateTime.UtcNow,
        clientCreatedAt = DateTime.UtcNow,
        clientReportId,
    };

    private static async Task<JsonElement> AssertProblemAsync(HttpResponseMessage response, HttpStatusCode status)
    {
        Assert.Equal(status, response.StatusCode);
        Assert.Equal("application/problem+json", response.Content.Headers.ContentType?.MediaType);
        var problem = await response.Content.ReadFromJsonAsync<JsonElement>();
        Assert.Equal((int)status, problem.GetProperty("status").GetInt32());
        return problem;
    }

    private static async Task<JsonElement> AssertValidationErrorsAsync(HttpResponseMessage response)
    {
        var problem = await AssertProblemAsync(response, HttpStatusCode.BadRequest);
        var errors = problem.GetProperty("errors");
        // No System.Text.Json paths or .NET type names leak into the response
        Assert.DoesNotContain("$", errors.GetRawText());
        Assert.DoesNotContain("System.", errors.GetRawText());
        return errors;
    }

    private static string[] ErrorsFor(JsonElement errors, string field) =>
        errors.GetProperty(field).EnumerateArray().Select(e => e.GetString()!).ToArray();
}
