using Microsoft.EntityFrameworkCore;
using TeeUp.Api.Models;

namespace TeeUp.Api.Data;

public class TeeUpDbContext(DbContextOptions<TeeUpDbContext> options) : DbContext(options)
{
    public DbSet<User> Users => Set<User>();
    public DbSet<Course> Courses => Set<Course>();
    public DbSet<TeeTime> TeeTimes => Set<TeeTime>();
    public DbSet<JoinRequest> JoinRequests => Set<JoinRequest>();
    public DbSet<Round> Rounds => Set<Round>();
    public DbSet<ScorecardEntry> ScorecardEntries => Set<ScorecardEntry>();
    public DbSet<Endorsement> Endorsements => Set<Endorsement>();
    public DbSet<Notification> Notifications => Set<Notification>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        // All entities use client-generatable UUID primary keys so IDs can
        // be created offline on the Android client (see Planning & Design,
        // "Data Models and Schema Definitions").
        modelBuilder.Entity<User>(b =>
        {
            b.HasKey(u => u.Id);
            b.Property(u => u.FirebaseUid).IsRequired();
            b.HasIndex(u => u.FirebaseUid).IsUnique();
            b.Property(u => u.DisplayName).IsRequired();
            b.Property(u => u.HandicapIndex).HasPrecision(4, 1);
            b.Property(u => u.PaceOfPlay).HasConversion<string>();
            b.Property(u => u.Language).HasConversion<string>();
            // Existing and new profiles start with notification preferences enabled.
            b.Property(u => u.JoinRequestNotifications).HasDefaultValue(true);
            b.Property(u => u.TeeTimeReminders).HasDefaultValue(true);
            b.Property(u => u.WeatherAlerts).HasDefaultValue(true);
            b.HasOne<Course>()
                .WithMany()
                .HasForeignKey(u => u.HomeCourseId)
                .OnDelete(DeleteBehavior.SetNull);
        });

        modelBuilder.Entity<Course>(b =>
        {
            b.HasKey(c => c.Id);
            b.Property(c => c.Name).IsRequired();
            b.Property(c => c.Rating).HasPrecision(3, 1);
        });

        modelBuilder.Entity<TeeTime>(b =>
        {
            b.HasKey(t => t.Id);
            b.Property(t => t.Price).HasPrecision(8, 2);
            b.Property(t => t.Type).HasConversion<string>();
            b.HasOne<User>()
                .WithMany()
                .HasForeignKey(t => t.HostUserId)
                .OnDelete(DeleteBehavior.SetNull);
            b.HasOne<Course>()
                .WithMany()
                .HasForeignKey(t => t.CourseId)
                .OnDelete(DeleteBehavior.Restrict);
        });

        modelBuilder.Entity<JoinRequest>(b =>
        {
            b.HasKey(j => j.Id);
            b.Property(j => j.Status).HasConversion<string>();
            b.HasOne<TeeTime>()
                .WithMany()
                .HasForeignKey(j => j.TeeTimeId)
                .OnDelete(DeleteBehavior.Cascade);
            b.HasOne<User>()
                .WithMany()
                .HasForeignKey(j => j.GuestUserId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        modelBuilder.Entity<Round>(b =>
        {
            b.HasKey(r => r.Id);
            b.HasOne<TeeTime>()
                .WithMany()
                .HasForeignKey(r => r.TeeTimeId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        modelBuilder.Entity<ScorecardEntry>(b =>
        {
            b.HasKey(s => s.Id);
            b.HasOne<Round>()
                .WithMany()
                .HasForeignKey(s => s.RoundId)
                .OnDelete(DeleteBehavior.Cascade);
            b.HasIndex(s => new { s.RoundId, s.HoleNumber }).IsUnique();
        });

        modelBuilder.Entity<Endorsement>(b =>
        {
            b.HasKey(e => e.Id);
            b.HasOne<Round>()
                .WithMany()
                .HasForeignKey(e => e.RoundId)
                .OnDelete(DeleteBehavior.Cascade);
            b.HasOne<User>()
                .WithMany()
                .HasForeignKey(e => e.FromUserId)
                .OnDelete(DeleteBehavior.Restrict);
            b.HasOne<User>()
                .WithMany()
                .HasForeignKey(e => e.ToUserId)
                .OnDelete(DeleteBehavior.Restrict);
        });

        modelBuilder.Entity<Notification>(b =>
        {
            b.HasKey(n => n.Id);
            b.Property(n => n.Type).HasConversion<string>();
            b.Property(n => n.Message).IsRequired();
            b.HasOne<User>()
                .WithMany()
                .HasForeignKey(n => n.UserId)
                .OnDelete(DeleteBehavior.Cascade);
        });
    }
}
