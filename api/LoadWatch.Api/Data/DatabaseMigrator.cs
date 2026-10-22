using Microsoft.EntityFrameworkCore;

namespace LoadWatch.Api.Data;

// Brings the database schema up to date when the API starts, in every environment, so a fresh Azure SQL
// database needs no manual "dotnet ef database update". A failure is logged and rethrown: the API must not
// start serving against a schema it doesn't match.
public static class DatabaseMigrator
{
    public static async Task MigrateAsync(LoadWatchDbContext db, ILogger logger, CancellationToken ct = default)
    {
        try
        {
            var pending = (await db.Database.GetPendingMigrationsAsync(ct)).ToList();
            if (pending.Count == 0)
            {
                logger.LogInformation("Database schema is up to date; no migrations to apply");
                return;
            }

            logger.LogInformation("Applying {Count} database migration(s): {Migrations}", pending.Count, string.Join(", ", pending));
            await db.Database.MigrateAsync(ct);

            var applied = (await db.Database.GetAppliedMigrationsAsync(ct)).ToList();
            logger.LogInformation("Database migrations applied; the schema is now at {Latest}", applied.LastOrDefault());
        }
        catch (Exception ex)
        {
            logger.LogCritical(ex, "Database migration failed; the API will not start");
            throw;
        }
    }
}
