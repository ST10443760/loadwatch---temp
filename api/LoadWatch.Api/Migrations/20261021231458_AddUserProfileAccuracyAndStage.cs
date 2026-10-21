using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace LoadWatch.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddUserProfileAccuracyAndStage : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "EnabledAlertTypes",
                table: "UserSettings",
                type: "nvarchar(128)",
                maxLength: 128,
                nullable: false,
                defaultValue: "");

            migrationBuilder.AddColumn<string>(
                name: "ThemePreference",
                table: "UserSettings",
                type: "nvarchar(8)",
                maxLength: 8,
                nullable: false,
                defaultValue: "");

            migrationBuilder.CreateTable(
                name: "AccuracySummaries",
                columns: table => new
                {
                    AreaId = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    AccuracyScore = table.Column<decimal>(type: "decimal(3,2)", precision: 3, scale: 2, nullable: false),
                    SampleSize = table.Column<int>(type: "int", nullable: false),
                    WindowHours = table.Column<int>(type: "int", nullable: false),
                    CalculatedAt = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_AccuracySummaries", x => x.AreaId);
                    table.CheckConstraint("CK_AccuracySummaries_AccuracyScore", "[AccuracyScore] BETWEEN 0 AND 1");
                    table.ForeignKey(
                        name: "FK_AccuracySummaries_Areas_AreaId",
                        column: x => x.AreaId,
                        principalTable: "Areas",
                        principalColumn: "AreaId",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "StageStatus",
                columns: table => new
                {
                    Id = table.Column<int>(type: "int", nullable: false),
                    Stage = table.Column<int>(type: "int", nullable: false),
                    UpdatedAt = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_StageStatus", x => x.Id);
                    table.CheckConstraint("CK_StageStatus_SingleRow", "[Id] = 1");
                    table.CheckConstraint("CK_StageStatus_Stage", "[Stage] BETWEEN 0 AND 8");
                });

            migrationBuilder.CreateTable(
                name: "UserProfiles",
                columns: table => new
                {
                    UserId = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    DisplayName = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    Email = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    CreatedAt = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false),
                    LastSeenAt = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_UserProfiles", x => x.UserId);
                });

            migrationBuilder.AddCheckConstraint(
                name: "CK_UserSettings_ThemePreference",
                table: "UserSettings",
                sql: "[ThemePreference] IN ('system', 'light', 'dark')");

            migrationBuilder.AddForeignKey(
                name: "FK_FavouriteAreas_UserProfiles_UserId",
                table: "FavouriteAreas",
                column: "UserId",
                principalTable: "UserProfiles",
                principalColumn: "UserId",
                onDelete: ReferentialAction.Cascade);

            migrationBuilder.AddForeignKey(
                name: "FK_OutageReports_UserProfiles_UserId",
                table: "OutageReports",
                column: "UserId",
                principalTable: "UserProfiles",
                principalColumn: "UserId",
                onDelete: ReferentialAction.Cascade);

            migrationBuilder.AddForeignKey(
                name: "FK_UserSettings_UserProfiles_UserId",
                table: "UserSettings",
                column: "UserId",
                principalTable: "UserProfiles",
                principalColumn: "UserId",
                onDelete: ReferentialAction.Cascade);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "FK_FavouriteAreas_UserProfiles_UserId",
                table: "FavouriteAreas");

            migrationBuilder.DropForeignKey(
                name: "FK_OutageReports_UserProfiles_UserId",
                table: "OutageReports");

            migrationBuilder.DropForeignKey(
                name: "FK_UserSettings_UserProfiles_UserId",
                table: "UserSettings");

            migrationBuilder.DropTable(
                name: "AccuracySummaries");

            migrationBuilder.DropTable(
                name: "StageStatus");

            migrationBuilder.DropTable(
                name: "UserProfiles");

            migrationBuilder.DropCheckConstraint(
                name: "CK_UserSettings_ThemePreference",
                table: "UserSettings");

            migrationBuilder.DropColumn(
                name: "EnabledAlertTypes",
                table: "UserSettings");

            migrationBuilder.DropColumn(
                name: "ThemePreference",
                table: "UserSettings");
        }
    }
}
