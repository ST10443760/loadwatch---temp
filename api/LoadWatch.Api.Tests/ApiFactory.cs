using System.Security.Cryptography;
using LoadWatch.Api.Data;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Microsoft.IdentityModel.JsonWebTokens;
using Microsoft.IdentityModel.Protocols;
using Microsoft.IdentityModel.Protocols.OpenIdConnect;
using Microsoft.IdentityModel.Tokens;

namespace LoadWatch.Api.Tests;

// Hosts the real API against a throwaway LocalDB database. Firebase token validation runs exactly as configured
// (issuer, audience, lifetime); only the signing key is swapped for a test key, because real tokens can only be
// signed by Google.
public class ApiFactory : WebApplicationFactory<Program>
{
    public const string ProjectId = "loadwatch-ee062";
    public const string Issuer = "https://securetoken.google.com/" + ProjectId;

    private const string ConnectionString =
        @"Server=(localdb)\MSSQLLocalDB;Database=LoadWatchDb_ApiTests;Trusted_Connection=True;MultipleActiveResultSets=true;TrustServerCertificate=True";

    private static readonly RsaSecurityKey SigningKey = new(RSA.Create(2048)) { KeyId = "test-signing-key" };

    public ApiFactory()
    {
        // The API seeds the database on startup, so the schema has to exist before the host is built
        using var db = CreateDbContext();
        db.Database.EnsureDeleted();
        db.Database.Migrate();
    }

    public static LoadWatchDbContext CreateDbContext() =>
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
            services.AddDbContext<LoadWatchDbContext>(options => options.UseSqlServer(ConnectionString));

            services.PostConfigure<JwtBearerOptions>(JwtBearerDefaults.AuthenticationScheme, options =>
            {
                var configuration = new OpenIdConnectConfiguration { Issuer = Issuer };
                configuration.SigningKeys.Add(SigningKey);
                options.Configuration = configuration;
                options.ConfigurationManager = new StaticConfigurationManager<OpenIdConnectConfiguration>(configuration);
            });
        });
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
