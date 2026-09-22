using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace TeeUp.Api.Data.Migrations
{
    /// <inheritdoc />
    public partial class AddParToCourse : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<int>(
                name: "Par",
                table: "Courses",
                type: "integer",
                nullable: false,
                defaultValue: 72);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "Par",
                table: "Courses");
        }
    }
}
