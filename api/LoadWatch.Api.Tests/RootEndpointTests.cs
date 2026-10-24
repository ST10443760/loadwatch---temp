using System.Diagnostics;
using System.Net;
using System.Text.Json;
using LoadWatch.Api.Data;
using LoadWatch.Api.Health;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.TestHost;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;

namespace LoadWatch.Api.Tests;

public class RootEndpointTests(ApiFactory factory) : IClassFixture<ApiFactory>
{
    [Fact]
    public async Task The_root_path_is_anonymous_and_says_the_api_is_running()
    {
        var response = await factory.CreateClient(token: null).GetAsync("/");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("application/json", response.Content.Headers.ContentType?.MediaType);
        var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync()).RootElement;
        Assert.Equal("Running", body.GetProperty("status").GetString());
        Assert.Equal(RootEndpoint.Message, body.GetProperty("message").GetString());
        Assert.Equal("/health", body.GetProperty("health").GetString());
    }

    [Fact]
    public async Task The_root_path_answers_at_once_while_the_database_is_still_unavailable()
    {
        // The API's endpoints and readiness gate on a bare pipeline whose database is never ready and whose
        // server would take a full connect timeout to fail, so any database call from / would show up as a delay
        var builder = WebApplication.CreateBuilder();
        builder.WebHost.UseTestServer();
        builder.Services.AddProblemDetails();
        builder.Services.AddSingleton<DatabaseReadiness>();
        builder.Services.AddDbContext<LoadWatchDbContext>(options =>
            options.UseSqlServer("Server=10.255.255.1,1433;Database=Unreachable;Connect Timeout=30;TrustServerCertificate=True"));
        builder.Services.AddLoadWatchHealthChecks();
        await using var app = builder.Build();
        app.UseDatabaseReadinessGate();
        app.MapLoadWatchRoot();
        app.MapLoadWatchHealth();
        app.MapGet("/api/stages/current", () => "never reached");
        await app.StartAsync();
        var client = app.GetTestClient();

        var stopwatch = Stopwatch.StartNew();
        var root = await client.GetAsync("/");
        stopwatch.Stop();

        Assert.Equal(HttpStatusCode.OK, root.StatusCode);
        Assert.True(stopwatch.Elapsed < TimeSpan.FromSeconds(2), $"/ took {stopwatch.Elapsed}");
        // The database-dependent routes still say they are not ready
        Assert.Equal(HttpStatusCode.ServiceUnavailable, (await client.GetAsync("/health")).StatusCode);
        Assert.Equal(HttpStatusCode.ServiceUnavailable, (await client.GetAsync("/api/stages/current")).StatusCode);
    }
}
