using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace TeeUp.Api.Data.Migrations
{
    /// <inheritdoc />
    public partial class AddProfileNotificationPreferences : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<bool>(
                name: "JoinRequestNotifications",
                table: "Users",
                type: "boolean",
                nullable: false,
                defaultValue: true);

            migrationBuilder.AddColumn<bool>(
                name: "TeeTimeReminders",
                table: "Users",
                type: "boolean",
                nullable: false,
                defaultValue: true);

            migrationBuilder.AddColumn<bool>(
                name: "WeatherAlerts",
                table: "Users",
                type: "boolean",
                nullable: false,
                defaultValue: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "JoinRequestNotifications",
                table: "Users");

            migrationBuilder.DropColumn(
                name: "TeeTimeReminders",
                table: "Users");

            migrationBuilder.DropColumn(
                name: "WeatherAlerts",
                table: "Users");
        }
    }
}
