using System.Net;
using System.Text.Json;
using LoadWatch.Api.Data;
using LoadWatch.Api.Health;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.TestHost;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Diagnostics.HealthChecks;

namespace LoadWatch.Api.Tests;

public class HealthTests(ApiFactory factory) : IClassFixture<ApiFactory>
{
    // Nothing listens on port 1, so the connection is refused straight away
    private const string UnreachableServer = "Server=127.0.0.1,1;Database=Unreachable;Connect Timeout=2;TrustServerCertificate=True";

    [Fact]
    public async Task Health_is_anonymous_and_reports_the_database_as_healthy()
    {
        var response = await factory.CreateClient(token: null).GetAsync("/health");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("application/json", response.Content.Headers.ContentType?.MediaType);
        var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync()).RootElement;
        Assert.Equal("Healthy", body.GetProperty("status").GetString());
        Assert.Equal("Healthy", body.GetProperty("database").GetString());
        Assert.False(body.TryGetProperty("detail", out _));
        Assert.True(body.TryGetProperty("checkedAt", out _));
    }

    [Fact]
    public async Task Startup_applies_every_migration_to_an_empty_database()
    {
        // The factory deletes the database before the host starts, so the schema can only come from startup
        factory.CreateClient(token: null);

        await using var db = factory.CreateDbContext();
        Assert.Empty(await db.Database.GetPendingMigrationsAsync());
        Assert.True(await db.Areas.AnyAsync());
    }

    [Fact]
    public async Task The_database_check_is_unhealthy_when_the_server_cannot_be_reached()
    {
        var readiness = new DatabaseReadiness();
        readiness.MarkReady();
        await using var db = Unreachable();

        var result = await new DatabaseHealthCheck(db, readiness).CheckHealthAsync(new HealthCheckContext());

        Assert.Equal(HealthStatus.Unhealthy, result.Status);
        Assert.Equal(DatabaseHealthCheck.UnreachableMessage, result.Description);
    }

    [Fact]
    public async Task The_database_check_is_unhealthy_with_a_clear_message_until_startup_has_prepared_the_database()
    {
        await using var db = Unreachable();

        var result = await new DatabaseHealthCheck(db, new DatabaseReadiness()).CheckHealthAsync(new HealthCheckContext());

        Assert.Equal(HealthStatus.Unhealthy, result.Status);
        Assert.Equal(DatabaseHealthCheck.NotReadyMessage, result.Description);
    }

    [Fact]
    public async Task While_the_database_is_not_ready_health_and_api_routes_answer_503_with_a_reason()
    {
        // The API's health endpoint and readiness gate on a bare pipeline whose database is never ready
        var builder = WebApplication.CreateBuilder();
        builder.WebHost.UseTestServer();
        builder.Services.AddProblemDetails();
        builder.Services.AddSingleton<DatabaseReadiness>();
        builder.Services.AddDbContext<LoadWatchDbContext>(options => options.UseSqlServer(UnreachableServer));
        builder.Services.AddLoadWatchHealthChecks();
        await using var app = builder.Build();
        app.UseDatabaseReadinessGate();
        app.MapLoadWatchHealth();
        app.MapGet("/api/stages/current", () => "never reached");
        await app.StartAsync();
        var client = app.GetTestClient();

        var health = await client.GetAsync("/health");
        Assert.Equal(HttpStatusCode.ServiceUnavailable, health.StatusCode);
        var body = JsonDocument.Parse(await health.Content.ReadAsStringAsync()).RootElement;
        Assert.Equal("Unhealthy", body.GetProperty("status").GetString());
        Assert.Equal(DatabaseHealthCheck.NotReadyMessage, body.GetProperty("detail").GetString());

        var api = await client.GetAsync("/api/stages/current");
        Assert.Equal(HttpStatusCode.ServiceUnavailable, api.StatusCode);
        Assert.Equal("application/problem+json", api.Content.Headers.ContentType?.MediaType);
        Assert.True(api.Headers.Contains("Retry-After"));
    }

    private static LoadWatchDbContext Unreachable() =>
        new(new DbContextOptionsBuilder<LoadWatchDbContext>().UseSqlServer(UnreachableServer).Options);
}
