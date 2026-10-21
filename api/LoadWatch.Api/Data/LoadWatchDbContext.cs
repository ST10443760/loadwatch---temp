using LoadWatch.Api.Models;
using Microsoft.EntityFrameworkCore;

namespace LoadWatch.Api.Data;

public class LoadWatchDbContext(DbContextOptions<LoadWatchDbContext> options) : DbContext(options)
{
    public DbSet<Area> Areas => Set<Area>();
    public DbSet<ScheduleEvent> ScheduleEvents => Set<ScheduleEvent>();
    public DbSet<UserSettings> UserSettings => Set<UserSettings>();
    public DbSet<FavouriteArea> FavouriteAreas => Set<FavouriteArea>();
    public DbSet<OutageReport> OutageReports => Set<OutageReport>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<Area>(entity =>
        {
            entity.HasKey(a => a.AreaId);
            entity.Property(a => a.SupplyType).HasMaxLength(16);
            entity.ToTable(t => t.HasCheckConstraint(
                "CK_Areas_SupplyType", "[SupplyType] IN ('ESKOM', 'MUNICIPAL')"));
        });

        modelBuilder.Entity<ScheduleEvent>(entity =>
        {
            entity.HasKey(s => s.ScheduleId);
            entity.HasOne<Area>()
                .WithMany()
                .HasForeignKey(s => s.AreaId)
                .OnDelete(DeleteBehavior.Restrict);
            entity.HasIndex(s => new { s.AreaId, s.StartAt });
            entity.ToTable(t => t.HasCheckConstraint(
                "CK_ScheduleEvents_EndAfterStart", "[EndAt] > [StartAt]"));
        });

        modelBuilder.Entity<UserSettings>(entity =>
        {
            entity.HasKey(u => u.UserId);
            entity.HasOne<Area>()
                .WithMany()
                .HasForeignKey(u => u.DefaultAreaId)
                .OnDelete(DeleteBehavior.Restrict);
            entity.Property(u => u.LanguageCode).HasMaxLength(2);
            entity.ToTable(t =>
            {
                t.HasCheckConstraint(
                    "CK_UserSettings_NotificationLeadMinutes",
                    "[NotificationLeadMinutes] BETWEEN 5 AND 120");
                t.HasCheckConstraint(
                    "CK_UserSettings_LanguageCode", "[LanguageCode] IN ('en', 'zu', 'af')");
            });
        });

        modelBuilder.Entity<FavouriteArea>(entity =>
        {
            entity.HasKey(f => f.FavouriteId);
            // Removing a favourite must never cascade to the public Area (FR-FAV-03)
            entity.HasOne<Area>()
                .WithMany()
                .HasForeignKey(f => f.AreaId)
                .OnDelete(DeleteBehavior.Restrict);
            entity.HasIndex(f => new { f.UserId, f.AreaId }).IsUnique();
        });

        modelBuilder.Entity<OutageReport>(entity =>
        {
            entity.HasKey(r => r.ReportId);
            entity.HasOne<Area>()
                .WithMany()
                .HasForeignKey(r => r.AreaId)
                .OnDelete(DeleteBehavior.Restrict);
            entity.Property(r => r.EventType).HasConversion<string>().HasMaxLength(16);
            entity.Property(r => r.SyncStatus).HasConversion<string>().HasMaxLength(16);
            entity.HasIndex(r => new { r.UserId, r.ClientReportId }).IsUnique();
            entity.HasIndex(r => new { r.AreaId, r.ReportedAt });
        });
    }
}
