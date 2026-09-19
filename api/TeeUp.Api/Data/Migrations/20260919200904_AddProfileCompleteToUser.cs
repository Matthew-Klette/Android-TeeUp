using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace TeeUp.Api.Data.Migrations
{
    /// <inheritdoc />
    public partial class AddProfileCompleteToUser : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<bool>(
                name: "ProfileComplete",
                table: "Users",
                type: "boolean",
                nullable: false,
                defaultValue: false);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "ProfileComplete",
                table: "Users");
        }
    }
}
