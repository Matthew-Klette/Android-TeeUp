using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace TeeUp.Api.Data.Migrations
{
    /// <inheritdoc />
    public partial class RemoveWeatherAlertsFromUser : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "WeatherAlerts",
                table: "Users");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<bool>(
                name: "WeatherAlerts",
                table: "Users",
                type: "boolean",
                nullable: false,
                defaultValue: true);
        }
    }
}
