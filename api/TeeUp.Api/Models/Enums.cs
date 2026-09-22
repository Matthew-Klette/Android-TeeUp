namespace TeeUp.Api.Models;

public enum PaceOfPlay
{
    Relaxed,
    Standard,
    Brisk
}

public enum Language
{
    En,
    Af,
    Zu
}

public enum TeeTimeType
{
    Booking,
    OpenRound
}

public enum TeeTimeStatus
{
    Open,
    Full,
    Cancelled
}

public enum JoinRequestStatus
{
    Pending,
    Accepted,
    Declined
}

public enum NotificationType
{
    JoinRequestReceived,
    RequestAccepted,
    RequestDeclined,
    TeeTimeReminder,
    SyncPending,
    // Kept as an unused placeholder (never actually constructed anywhere) rather than removed:
    // this enum serializes to JSON by ordinal, not by name, so deleting a case here would shift
    // every later case's wire value and silently break the Android client's own NotificationType
    // constants, which mirror these ordinals by hand.
    WeatherAlert,
    /// <summary>A group's host cancelled it (EME-321) while this user had a pending/accepted join request against it.</summary>
    TeeTimeCancelled
}
