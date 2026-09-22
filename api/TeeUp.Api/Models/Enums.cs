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
    WeatherAlert,
    /// <summary>A group's host cancelled it (EME-321) while this user had a pending/accepted join request against it.</summary>
    TeeTimeCancelled
}
