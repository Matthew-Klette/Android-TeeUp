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

/// <summary>
/// What a <see cref="HoleCoordinate"/> marks. Values are golfapi.io's coordinate codes (GolfAPI, n.d.),
/// the same scheme the Wedge golf app's course data uses, so a dataset from that provider can be
/// imported later without remapping. Stored as the int value, never reordered.
/// </summary>
public enum PointOfInterest
{
    Green = 1,
    GreenBunker = 2,
    FairwayBunker = 3,
    Water = 4,
    Trees = 5,
    Marker100 = 6,
    Marker150 = 7,
    Marker200 = 8,
    Dogleg = 9,
    Road = 10,
    FrontTee = 11,
    BackTee = 12
}

/// <summary>Where on the <see cref="PointOfInterest"/> a point sits, along the line of play.</summary>
public enum PoiLocation
{
    Front = 1,
    Middle = 2,
    Back = 3
}

/// <summary>Which side of the fairway a point is on.</summary>
public enum FairwaySide
{
    Left = 1,
    Center = 2,
    Right = 3
}

/* References:

GolfAPI (n.d.). GolfAPI documentation: GET /coordinates/{id}. [online] Postman. Available at: <https://documenter.getpostman.com/view/1756312/UVeDsT2b> [Accessed 23 Oct. 2026].

*/
