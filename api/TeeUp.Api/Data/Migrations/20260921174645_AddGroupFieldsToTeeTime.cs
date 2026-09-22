using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace TeeUp.Api.Data.Migrations
{
    /// <inheritdoc />
    public partial class AddGroupFieldsToTeeTime : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<int>(
                name: "Holes",
                table: "TeeTimes",
                type: "integer",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "Status",
                table: "TeeTimes",
                type: "text",
                nullable: false,
                defaultValue: "Open");

            migrationBuilder.AddColumn<decimal>(
                name: "WantedHandicapMax",
                table: "TeeTimes",
                type: "numeric(4,1)",
                precision: 4,
                scale: 1,
                nullable: true);

            migrationBuilder.AddColumn<decimal>(
                name: "WantedHandicapMin",
                table: "TeeTimes",
                type: "numeric(4,1)",
                precision: 4,
                scale: 1,
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "WantedPace",
                table: "TeeTimes",
                type: "text",
                nullable: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "Holes",
                table: "TeeTimes");

            migrationBuilder.DropColumn(
                name: "Status",
                table: "TeeTimes");

            migrationBuilder.DropColumn(
                name: "WantedHandicapMax",
                table: "TeeTimes");

            migrationBuilder.DropColumn(
                name: "WantedHandicapMin",
                table: "TeeTimes");

            migrationBuilder.DropColumn(
                name: "WantedPace",
                table: "TeeTimes");
        }
    }
}
