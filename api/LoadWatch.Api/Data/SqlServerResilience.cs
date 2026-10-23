using Microsoft.Data.SqlClient;
using Microsoft.EntityFrameworkCore.Infrastructure;

namespace LoadWatch.Api.Data;

// Lets every database call ride out a free serverless Azure SQL database waking from a pause, which takes up to
// about a minute and fails connections with error 40613 until it is done.
public static class SqlServerResilience
{
    public const int MaxRetryCount = 6;
    public static readonly TimeSpan MaxRetryDelay = TimeSpan.FromSeconds(30);

    // Long enough for one connection attempt to wait out a resume
    public const int MinimumConnectTimeoutSeconds = 60;

    // EF Core retries each command with an increasing delay when it fails with a transient error
    public static void Configure(SqlServerDbContextOptionsBuilder sql) =>
        sql.EnableRetryOnFailure(MaxRetryCount, MaxRetryDelay, TransientSqlErrors.Numbers);

    // Raises the connect timeout to at least a minute, keeping a longer one if the connection string sets it
    public static string WithResumeTimeout(string connectionString)
    {
        var builder = new SqlConnectionStringBuilder(connectionString);
        if (builder.ConnectTimeout < MinimumConnectTimeoutSeconds)
        {
            builder.ConnectTimeout = MinimumConnectTimeoutSeconds;
        }
        return builder.ConnectionString;
    }
}
