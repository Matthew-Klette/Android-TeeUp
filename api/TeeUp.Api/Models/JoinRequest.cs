namespace TeeUp.Api.Models;

public class JoinRequest
{
    public Guid Id { get; set; }
    public Guid TeeTimeId { get; set; }
    public Guid GuestUserId { get; set; }
    public JoinRequestStatus Status { get; set; } = JoinRequestStatus.Pending;
}
