using System.Net;
using System.Text.Json;
using LoadWatch.Api.Data;
using LoadWatch.Api.Health;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Diagnostics.HealthChecks;

namespace LoadWatch.Api.Tests;

public class HealthTests(ApiFactory factory) : IClassFixture<ApiFactory>
{
    [Fact]
    public async Task Health_is_anonymous_and_reports_the_database_as_healthy()
    {
        var response = await factory.CreateClient(token: null).GetAsync("/health");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("application/json", response.Content.Headers.ContentType?.MediaType);
        var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync()).RootElement;
        Assert.Equal("Healthy", body.GetProperty("status").GetString());
        Assert.Equal("Healthy", body.GetProperty("database").GetString());
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
        // Nothing listens on port 1, so the connection is refused straight away
        var options = new DbContextOptionsBuilder<LoadWatchDbContext>()
            .UseSqlServer("Server=127.0.0.1,1;Database=Unreachable;Connect Timeout=2;TrustServerCertificate=True")
            .Options;
        await using var db = new LoadWatchDbContext(options);

        var result = await new DatabaseHealthCheck(db).CheckHealthAsync(new HealthCheckContext());

        Assert.Equal(HealthStatus.Unhealthy, result.Status);
    }
}
