using LoadWatch.Api.Models;
using Microsoft.EntityFrameworkCore;

namespace LoadWatch.Api.Data;

public class LoadWatchDbContext(DbContextOptions<LoadWatchDbContext> options) : DbContext(options)
{
    public DbSet<Area> Areas => Set<Area>();
    public DbSet<ScheduleEvent> ScheduleEvents => Set<ScheduleEvent>();
    public DbSet<UserProfile> UserProfiles => Set<UserProfile>();
    public DbSet<UserSettings> UserSettings => Set<UserSettings>();
    public DbSet<FavouriteArea> FavouriteAreas => Set<FavouriteArea>();
    public DbSet<OutageReport> OutageReports => Set<OutageReport>();
    public DbSet<AccuracySummary> AccuracySummaries => Set<AccuracySummary>();
    public DbSet<StageStatus> StageStatus => Set<StageStatus>();

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

        modelBuilder.Entity<UserProfile>(entity =>
        {
            entity.HasKey(u => u.UserId);
        });

        modelBuilder.Entity<UserSettings>(entity =>
        {
            entity.HasKey(u => u.UserId);
            entity.HasOne<UserProfile>()
                .WithOne()
                .HasForeignKey<UserSettings>(u => u.UserId)
                .OnDelete(DeleteBehavior.Cascade);
            entity.HasOne<Area>()
                .WithMany()
                .HasForeignKey(u => u.DefaultAreaId)
                .OnDelete(DeleteBehavior.Restrict);
            entity.Property(u => u.LanguageCode).HasMaxLength(2);
            entity.Property(u => u.ThemePreference).HasMaxLength(8);
            entity.Property(u => u.EnabledAlertTypes).HasMaxLength(128);
            entity.ToTable(t =>
            {
                t.HasCheckConstraint(
                    "CK_UserSettings_ThemePreference",
                    "[ThemePreference] IN ('system', 'light', 'dark')");
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
            entity.HasOne<UserProfile>()
                .WithMany()
                .HasForeignKey(f => f.UserId)
                .OnDelete(DeleteBehavior.Cascade);
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
            entity.HasOne<UserProfile>()
                .WithMany()
                .HasForeignKey(r => r.UserId)
                .OnDelete(DeleteBehavior.Cascade);
            entity.HasOne<Area>()
                .WithMany()
                .HasForeignKey(r => r.AreaId)
                .OnDelete(DeleteBehavior.Restrict);
            entity.Property(r => r.EventType).HasConversion<string>().HasMaxLength(16);
            entity.Property(r => r.SyncStatus).HasConversion<string>().HasMaxLength(16);
            entity.HasIndex(r => new { r.UserId, r.ClientReportId }).IsUnique();
            entity.HasIndex(r => new { r.AreaId, r.ReportedAt });
        });

        modelBuilder.Entity<AccuracySummary>(entity =>
        {
            entity.HasKey(a => a.AreaId);
            entity.HasOne<Area>()
                .WithOne()
                .HasForeignKey<AccuracySummary>(a => a.AreaId)
                .OnDelete(DeleteBehavior.Cascade);
            entity.Property(a => a.AccuracyScore).HasPrecision(3, 2);
            entity.ToTable(t => t.HasCheckConstraint(
                "CK_AccuracySummaries_AccuracyScore", "[AccuracyScore] BETWEEN 0 AND 1"));
        });

        modelBuilder.Entity<StageStatus>(entity =>
        {
            entity.HasKey(s => s.Id);
            entity.Property(s => s.Id).ValueGeneratedNever();
            entity.ToTable("StageStatus", t =>
            {
                t.HasCheckConstraint("CK_StageStatus_SingleRow", "[Id] = 1");
                t.HasCheckConstraint("CK_StageStatus_Stage", "[Stage] BETWEEN 0 AND 8");
            });
        });
    }
}
