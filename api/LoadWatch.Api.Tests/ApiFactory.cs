using System.Security.Cryptography;
using LoadWatch.Api.Data;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Data.SqlClient;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Microsoft.Extensions.Hosting;
using Microsoft.IdentityModel.JsonWebTokens;
using Microsoft.IdentityModel.Protocols;
using Microsoft.IdentityModel.Protocols.OpenIdConnect;
using Microsoft.IdentityModel.Tokens;

namespace LoadWatch.Api.Tests;

// Hosts the real API against a throwaway SQL Server database. Firebase token validation runs exactly as configured
// (issuer, audience, lifetime); only the signing key is swapped for a test key, because real tokens can only be
// signed by Google.
public class ApiFactory : WebApplicationFactory<Program>
{
    public const string ProjectId = "loadwatch-ee062";
    public const string Issuer = "https://securetoken.google.com/" + ProjectId;

    // CI points this at its SQL Server container; locally the tests fall back to LocalDB
    public const string ServerVariable = "LOADWATCH_TEST_SQL";

    private const string LocalDb =
        @"Server=(localdb)\MSSQLLocalDB;Trusted_Connection=True;MultipleActiveResultSets=true;TrustServerCertificate=True";

    private static readonly RsaSecurityKey SigningKey = new(RSA.Create(2048)) { KeyId = "test-signing-key" };

    // The test SQL Server without a database name
    public static string ServerConnectionString =>
        Environment.GetEnvironmentVariable(ServerVariable) is { Length: > 0 } server ? server : LocalDb;

    // Each fixture gets its own database, so test classes can run in parallel
    public string ConnectionString { get; } = new SqlConnectionStringBuilder(ServerConnectionString)
    {
        InitialCatalog = $"LoadWatchDb_ApiTests_{Guid.NewGuid():N}",
    }.ConnectionString;

    public ApiFactory()
    {
        // Start empty: the API applies its migrations and seeds when the host starts, as it does on Azure
        using var db = CreateDbContext();
        db.Database.EnsureDeleted();
    }

    public LoadWatchDbContext CreateDbContext() =>
        new(new DbContextOptionsBuilder<LoadWatchDbContext>().UseSqlServer(ConnectionString).Options);

    public HttpClient CreateClient(string? token)
    {
        var client = CreateClient();
        if (token is not null)
        {
            client.DefaultRequestHeaders.Authorization = new("Bearer", token);
        }
        return client;
    }

    public static string CreateToken(
        string userId,
        string? name = null,
        string? email = null,
        DateTime? expires = null,
        string audience = ProjectId)
    {
        var claims = new Dictionary<string, object> { ["sub"] = userId };
        if (name is not null)
        {
            claims["name"] = name;
        }
        if (email is not null)
        {
            claims["email"] = email;
        }

        var expiresAt = expires ?? DateTime.UtcNow.AddHours(1);
        return new JsonWebTokenHandler().CreateToken(new SecurityTokenDescriptor
        {
            Issuer = Issuer,
            Audience = audience,
            Claims = claims,
            IssuedAt = expiresAt.AddHours(-1),
            NotBefore = expiresAt.AddHours(-1),
            Expires = expiresAt,
            SigningCredentials = new SigningCredentials(SigningKey, SecurityAlgorithms.RsaSha256),
        });
    }

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.UseEnvironment("Development");
        builder.ConfigureTestServices(services =>
        {
            services.RemoveAll<DbContextOptions<LoadWatchDbContext>>();
            // The same retry settings as production, so the tests run through them too
            services.AddDbContext<LoadWatchDbContext>(options => options.UseSqlServer(ConnectionString, SqlServerResilience.Configure));

            services.PostConfigure<JwtBearerOptions>(JwtBearerDefaults.AuthenticationScheme, options =>
            {
                var configuration = new OpenIdConnectConfiguration { Issuer = Issuer };
                configuration.SigningKeys.Add(SigningKey);
                options.Configuration = configuration;
                options.ConfigurationManager = new StaticConfigurationManager<OpenIdConnectConfiguration>(configuration);
            });
        });
    }

    // The API prepares its database in the background after starting; wait for that so every test sees a seeded
    // database instead of the 503 the API gives while it is still getting ready
    protected override IHost CreateHost(IHostBuilder builder)
    {
        var host = base.CreateHost(builder);
        host.Services.GetRequiredService<DatabaseReadiness>().WhenReady.WaitAsync(TimeSpan.FromMinutes(2)).GetAwaiter().GetResult();
        return host;
    }

    protected override void Dispose(bool disposing)
    {
        base.Dispose(disposing);
        if (disposing)
        {
            using var db = CreateDbContext();
            db.Database.EnsureDeleted();
        }
    }
}
