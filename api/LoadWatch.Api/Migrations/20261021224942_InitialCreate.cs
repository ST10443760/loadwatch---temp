using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace LoadWatch.Api.Migrations
{
    /// <inheritdoc />
    public partial class InitialCreate : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "Areas",
                columns: table => new
                {
                    AreaId = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    Name = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    Municipality = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    Province = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    SupplyType = table.Column<string>(type: "nvarchar(16)", maxLength: 16, nullable: false),
                    Latitude = table.Column<double>(type: "float", nullable: true),
                    Longitude = table.Column<double>(type: "float", nullable: true),
                    IsActive = table.Column<bool>(type: "bit", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_Areas", x => x.AreaId);
                    table.CheckConstraint("CK_Areas_SupplyType", "[SupplyType] IN ('ESKOM', 'MUNICIPAL')");
                });

            migrationBuilder.CreateTable(
                name: "FavouriteAreas",
                columns: table => new
                {
                    FavouriteId = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    UserId = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    AreaId = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    Label = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    SortOrder = table.Column<int>(type: "int", nullable: false),
                    CreatedAt = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_FavouriteAreas", x => x.FavouriteId);
                    table.ForeignKey(
                        name: "FK_FavouriteAreas_Areas_AreaId",
                        column: x => x.AreaId,
                        principalTable: "Areas",
                        principalColumn: "AreaId",
                        onDelete: ReferentialAction.Restrict);
                });

            migrationBuilder.CreateTable(
                name: "OutageReports",
                columns: table => new
                {
                    ReportId = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    ClientReportId = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    UserId = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    AreaId = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    EventType = table.Column<string>(type: "nvarchar(16)", maxLength: 16, nullable: false),
                    ReportedAt = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false),
                    ClientCreatedAt = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false),
                    SyncStatus = table.Column<string>(type: "nvarchar(16)", maxLength: 16, nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_OutageReports", x => x.ReportId);
                    table.ForeignKey(
                        name: "FK_OutageReports_Areas_AreaId",
                        column: x => x.AreaId,
                        principalTable: "Areas",
                        principalColumn: "AreaId",
                        onDelete: ReferentialAction.Restrict);
                });

            migrationBuilder.CreateTable(
                name: "ScheduleEvents",
                columns: table => new
                {
                    ScheduleId = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    AreaId = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    Stage = table.Column<int>(type: "int", nullable: false),
                    StartAt = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false),
                    EndAt = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false),
                    Source = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    UpdatedAt = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_ScheduleEvents", x => x.ScheduleId);
                    table.CheckConstraint("CK_ScheduleEvents_EndAfterStart", "[EndAt] > [StartAt]");
                    table.ForeignKey(
                        name: "FK_ScheduleEvents_Areas_AreaId",
                        column: x => x.AreaId,
                        principalTable: "Areas",
                        principalColumn: "AreaId",
                        onDelete: ReferentialAction.Restrict);
                });

            migrationBuilder.CreateTable(
                name: "UserSettings",
                columns: table => new
                {
                    UserId = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    DefaultAreaId = table.Column<string>(type: "nvarchar(450)", nullable: true),
                    NotificationLeadMinutes = table.Column<int>(type: "int", nullable: false),
                    NotificationsEnabled = table.Column<bool>(type: "bit", nullable: false),
                    LanguageCode = table.Column<string>(type: "nvarchar(2)", maxLength: 2, nullable: false),
                    BiometricEnabled = table.Column<bool>(type: "bit", nullable: false),
                    UpdatedAt = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_UserSettings", x => x.UserId);
                    table.CheckConstraint("CK_UserSettings_LanguageCode", "[LanguageCode] IN ('en', 'zu', 'af')");
                    table.CheckConstraint("CK_UserSettings_NotificationLeadMinutes", "[NotificationLeadMinutes] BETWEEN 5 AND 120");
                    table.ForeignKey(
                        name: "FK_UserSettings_Areas_DefaultAreaId",
                        column: x => x.DefaultAreaId,
                        principalTable: "Areas",
                        principalColumn: "AreaId",
                        onDelete: ReferentialAction.Restrict);
                });

            migrationBuilder.CreateIndex(
                name: "IX_FavouriteAreas_AreaId",
                table: "FavouriteAreas",
                column: "AreaId");

            migrationBuilder.CreateIndex(
                name: "IX_FavouriteAreas_UserId_AreaId",
                table: "FavouriteAreas",
                columns: new[] { "UserId", "AreaId" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_OutageReports_AreaId_ReportedAt",
                table: "OutageReports",
                columns: new[] { "AreaId", "ReportedAt" });

            migrationBuilder.CreateIndex(
                name: "IX_OutageReports_UserId_ClientReportId",
                table: "OutageReports",
                columns: new[] { "UserId", "ClientReportId" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_ScheduleEvents_AreaId_StartAt",
                table: "ScheduleEvents",
                columns: new[] { "AreaId", "StartAt" });

            migrationBuilder.CreateIndex(
                name: "IX_UserSettings_DefaultAreaId",
                table: "UserSettings",
                column: "DefaultAreaId");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "FavouriteAreas");

            migrationBuilder.DropTable(
                name: "OutageReports");

            migrationBuilder.DropTable(
                name: "ScheduleEvents");

            migrationBuilder.DropTable(
                name: "UserSettings");

            migrationBuilder.DropTable(
                name: "Areas");
        }
    }
}
