using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace LoadWatch.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddDeviceTokensAndAreaStages : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "AreaStages",
                columns: table => new
                {
                    AreaId = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    Stage = table.Column<int>(type: "int", nullable: true),
                    LastAlertedStage = table.Column<int>(type: "int", nullable: true),
                    UpdatedAt = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_AreaStages", x => x.AreaId);
                    table.CheckConstraint("CK_AreaStages_LastAlertedStage", "[LastAlertedStage] IS NULL OR [LastAlertedStage] BETWEEN 0 AND 8");
                    table.CheckConstraint("CK_AreaStages_Stage", "[Stage] IS NULL OR [Stage] BETWEEN 0 AND 8");
                    table.ForeignKey(
                        name: "FK_AreaStages_Areas_AreaId",
                        column: x => x.AreaId,
                        principalTable: "Areas",
                        principalColumn: "AreaId",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "DeviceTokens",
                columns: table => new
                {
                    Token = table.Column<string>(type: "nvarchar(512)", maxLength: 512, nullable: false),
                    UserId = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    Platform = table.Column<string>(type: "nvarchar(16)", maxLength: 16, nullable: false),
                    CreatedAt = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false),
                    UpdatedAt = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_DeviceTokens", x => x.Token);
                    table.CheckConstraint("CK_DeviceTokens_Platform", "[Platform] IN ('android')");
                    table.ForeignKey(
                        name: "FK_DeviceTokens_UserProfiles_UserId",
                        column: x => x.UserId,
                        principalTable: "UserProfiles",
                        principalColumn: "UserId",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_DeviceTokens_UserId",
                table: "DeviceTokens",
                column: "UserId");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "AreaStages");

            migrationBuilder.DropTable(
                name: "DeviceTokens");
        }
    }
}
