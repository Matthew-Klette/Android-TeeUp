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
    WeatherAlert
}
