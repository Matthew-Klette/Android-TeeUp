using TeeUp.Api.Common;
using TeeUp.Api.Models;
using TeeUp.Api.Repositories;
using TeeUp.Api.Services;

namespace TeeUp.Api.Tests;

public class JoinRequestServiceTests
{
    private static (
        JoinRequestService Service,
        InMemoryTeeTimeRepository TeeTimes,
        InMemoryJoinRequestRepository JoinRequests,
        InMemoryNotificationRepository Notifications) CreateService()
    {
        var teeTimes = new InMemoryTeeTimeRepository();
        var joinRequests = new InMemoryJoinRequestRepository();
        var notifications = new InMemoryNotificationRepository();
        return (new JoinRequestService(joinRequests, teeTimes, notifications), teeTimes, joinRequests, notifications);
    }

    private static TeeTime MakeTeeTime(
        int openSpots = 1, Guid? hostUserId = null, TeeTimeStatus status = TeeTimeStatus.Open, DateTime? dateTime = null) => new()
    {
        Id = Guid.NewGuid(),
        HostUserId = hostUserId,
        CourseId = Guid.NewGuid(),
        DateTime = dateTime ?? DateTime.UtcNow.AddDays(1),
        OpenSpots = openSpots,
        Price = 0,
        Type = TeeTimeType.OpenRound,
        Status = status
    };

    [Fact]
    public async Task CreateAsync_ForExistingTeeTime_CreatesPendingRequest()
    {
        var (service, teeTimes, _, _) = CreateService();
        var teeTime = MakeTeeTime();
        await teeTimes.AddAsync(teeTime);

        var result = await service.CreateAsync(teeTime.Id, Guid.NewGuid());

        Assert.Equal(JoinRequestStatus.Pending, result.Status);
        Assert.Equal(teeTime.Id, result.TeeTimeId);
    }

    [Fact]
    public async Task CreateAsync_ForUnknownTeeTime_ThrowsNotFound()
    {
        var (service, _, _, _) = CreateService();

        await Assert.ThrowsAsync<NotFoundException>(
            () => service.CreateAsync(Guid.NewGuid(), Guid.NewGuid()));
    }

    [Fact]
    public async Task CreateAsync_ForOwnTeeTime_ThrowsDomainValidation()
    {
        var (service, teeTimes, _, _) = CreateService();
        var hostId = Guid.NewGuid();
        var teeTime = MakeTeeTime(hostUserId: hostId);
        await teeTimes.AddAsync(teeTime);

        await Assert.ThrowsAsync<DomainValidationException>(
            () => service.CreateAsync(teeTime.Id, hostId));
    }

    [Fact]
    public async Task CreateAsync_WithExistingPendingRequest_ThrowsDomainValidation()
    {
        var (service, teeTimes, _, _) = CreateService();
        var teeTime = MakeTeeTime(openSpots: 2);
        await teeTimes.AddAsync(teeTime);
        var guestId = Guid.NewGuid();
        await service.CreateAsync(teeTime.Id, guestId);

        await Assert.ThrowsAsync<DomainValidationException>(
            () => service.CreateAsync(teeTime.Id, guestId));
    }

    [Fact]
    public async Task CreateAsync_AfterBeingDeclined_CanRequestAgain()
    {
        var (service, teeTimes, _, _) = CreateService();
        var hostId = Guid.NewGuid();
        var teeTime = MakeTeeTime(openSpots: 2, hostUserId: hostId);
        await teeTimes.AddAsync(teeTime);
        var guestId = Guid.NewGuid();
        var first = await service.CreateAsync(teeTime.Id, guestId);
        await service.UpdateStatusAsync(first.Id, JoinRequestStatus.Declined, hostId);

        var second = await service.CreateAsync(teeTime.Id, guestId);

        Assert.Equal(JoinRequestStatus.Pending, second.Status);
    }

    [Fact]
    public async Task CreateAsync_ForCancelledTeeTime_ThrowsDomainValidation()
    {
        var (service, teeTimes, _, _) = CreateService();
        var teeTime = MakeTeeTime(status: TeeTimeStatus.Cancelled);
        await teeTimes.AddAsync(teeTime);

        await Assert.ThrowsAsync<DomainValidationException>(
            () => service.CreateAsync(teeTime.Id, Guid.NewGuid()));
    }

    [Fact]
    public async Task CreateAsync_ForPastTeeTime_ThrowsDomainValidation()
    {
        var (service, teeTimes, _, _) = CreateService();
        var teeTime = MakeTeeTime(dateTime: DateTime.UtcNow.AddDays(-1));
        await teeTimes.AddAsync(teeTime);

        await Assert.ThrowsAsync<DomainValidationException>(
            () => service.CreateAsync(teeTime.Id, Guid.NewGuid()));
    }

    [Fact]
    public async Task UpdateStatusAsync_ByHost_Succeeds()
    {
        var (service, teeTimes, _, _) = CreateService();
        var hostId = Guid.NewGuid();
        var teeTime = MakeTeeTime(openSpots: 1, hostUserId: hostId);
        await teeTimes.AddAsync(teeTime);
        var joinRequest = await service.CreateAsync(teeTime.Id, Guid.NewGuid());

        var result = await service.UpdateStatusAsync(joinRequest.Id, JoinRequestStatus.Accepted, hostId);

        Assert.Equal(JoinRequestStatus.Accepted, result.Status);
    }

    [Fact]
    public async Task UpdateStatusAsync_ByNonHost_ThrowsForbidden()
    {
        var (service, teeTimes, _, _) = CreateService();
        var hostId = Guid.NewGuid();
        var teeTime = MakeTeeTime(openSpots: 1, hostUserId: hostId);
        await teeTimes.AddAsync(teeTime);
        var joinRequest = await service.CreateAsync(teeTime.Id, Guid.NewGuid());

        await Assert.ThrowsAsync<ForbiddenException>(
            () => service.UpdateStatusAsync(joinRequest.Id, JoinRequestStatus.Accepted, Guid.NewGuid()));
    }

    [Fact]
    public async Task UpdateStatusAsync_AcceptingTheLastOpenSpot_MarksTeeTimeFull()
    {
        var (service, teeTimes, _, _) = CreateService();
        var hostId = Guid.NewGuid();
        var teeTime = MakeTeeTime(openSpots: 2, hostUserId: hostId);
        await teeTimes.AddAsync(teeTime);
        var first = await service.CreateAsync(teeTime.Id, Guid.NewGuid());
        var second = await service.CreateAsync(teeTime.Id, Guid.NewGuid());

        await service.UpdateStatusAsync(first.Id, JoinRequestStatus.Accepted, hostId);
        Assert.Equal(TeeTimeStatus.Open, (await teeTimes.GetByIdAsync(teeTime.Id))!.Status);

        await service.UpdateStatusAsync(second.Id, JoinRequestStatus.Accepted, hostId);
        Assert.Equal(TeeTimeStatus.Full, (await teeTimes.GetByIdAsync(teeTime.Id))!.Status);
    }

    [Fact]
    public async Task UpdateStatusAsync_AcceptingBeyondOpenSpots_ThrowsDomainValidation()
    {
        var (service, teeTimes, _, _) = CreateService();
        var hostId = Guid.NewGuid();
        var teeTime = MakeTeeTime(openSpots: 1, hostUserId: hostId);
        await teeTimes.AddAsync(teeTime);

        var first = await service.CreateAsync(teeTime.Id, Guid.NewGuid());
        var second = await service.CreateAsync(teeTime.Id, Guid.NewGuid());
        await service.UpdateStatusAsync(first.Id, JoinRequestStatus.Accepted, hostId);

        await Assert.ThrowsAsync<DomainValidationException>(
            () => service.UpdateStatusAsync(second.Id, JoinRequestStatus.Accepted, hostId));
    }

    [Fact]
    public async Task UpdateStatusAsync_AcceptingIntoCancelledTeeTime_ThrowsDomainValidation()
    {
        var (service, teeTimes, joinRequests, _) = CreateService();
        var hostId = Guid.NewGuid();
        var teeTime = MakeTeeTime(openSpots: 1, hostUserId: hostId);
        await teeTimes.AddAsync(teeTime);
        var joinRequest = await service.CreateAsync(teeTime.Id, Guid.NewGuid());

        // Cancelled after the request was made — accept must still re-check the tee time's
        // current state, not just the request's.
        teeTime.Status = TeeTimeStatus.Cancelled;
        await teeTimes.UpdateAsync(teeTime);

        await Assert.ThrowsAsync<DomainValidationException>(
            () => service.UpdateStatusAsync(joinRequest.Id, JoinRequestStatus.Accepted, hostId));
    }

    [Fact]
    public async Task UpdateStatusAsync_OnAlreadyResolvedRequest_ThrowsDomainValidation()
    {
        var (service, teeTimes, _, _) = CreateService();
        var hostId = Guid.NewGuid();
        var teeTime = MakeTeeTime(openSpots: 2, hostUserId: hostId);
        await teeTimes.AddAsync(teeTime);
        var joinRequest = await service.CreateAsync(teeTime.Id, Guid.NewGuid());
        await service.UpdateStatusAsync(joinRequest.Id, JoinRequestStatus.Declined, hostId);

        await Assert.ThrowsAsync<DomainValidationException>(
            () => service.UpdateStatusAsync(joinRequest.Id, JoinRequestStatus.Accepted, hostId));
    }

    [Fact]
    public async Task UpdateStatusAsync_ForUnknownRequest_ThrowsNotFound()
    {
        var (service, _, _, _) = CreateService();

        await Assert.ThrowsAsync<NotFoundException>(
            () => service.UpdateStatusAsync(Guid.NewGuid(), JoinRequestStatus.Accepted, Guid.NewGuid()));
    }

    [Fact]
    public async Task UpdateStatusAsync_ConcurrentAcceptsForLastOpenSpot_ExactlyOneSucceeds()
    {
        var (service, teeTimes, _, _) = CreateService();
        var hostId = Guid.NewGuid();
        var teeTime = MakeTeeTime(openSpots: 1, hostUserId: hostId);
        await teeTimes.AddAsync(teeTime);
        var first = await service.CreateAsync(teeTime.Id, Guid.NewGuid());
        var second = await service.CreateAsync(teeTime.Id, Guid.NewGuid());

        async Task<bool> TryAccept(Guid joinRequestId)
        {
            try
            {
                await service.UpdateStatusAsync(joinRequestId, JoinRequestStatus.Accepted, hostId);
                return true;
            }
            catch (DomainValidationException)
            {
                return false;
            }
        }

        var results = await Task.WhenAll(TryAccept(first.Id), TryAccept(second.Id));

        Assert.Single(results, succeeded => succeeded);
        Assert.Equal(TeeTimeStatus.Full, (await teeTimes.GetByIdAsync(teeTime.Id))!.Status);
    }

    [Theory]
    [InlineData(JoinRequestStatus.Accepted, NotificationType.RequestAccepted)]
    [InlineData(JoinRequestStatus.Declined, NotificationType.RequestDeclined)]
    public async Task UpdateStatusAsync_NotifiesTheGuestOfTheOutcome(
        JoinRequestStatus status, NotificationType expectedType)
    {
        var (service, teeTimes, _, notifications) = CreateService();
        var hostId = Guid.NewGuid();
        var teeTime = MakeTeeTime(openSpots: 1, hostUserId: hostId);
        await teeTimes.AddAsync(teeTime);
        var guestId = Guid.NewGuid();
        var joinRequest = await service.CreateAsync(teeTime.Id, guestId);

        await service.UpdateStatusAsync(joinRequest.Id, status, hostId);

        var notification = Assert.Single(await notifications.GetByUserIdAsync(guestId));
        Assert.Equal(expectedType, notification.Type);
        Assert.Equal(teeTime.Id, notification.RelatedEntityId);
    }

    [Fact]
    public async Task GetForTeeTimeAsync_ReturnsOnlyRequestsForThatTeeTime()
    {
        var (service, teeTimes, _, _) = CreateService();
        var teeTime = MakeTeeTime(openSpots: 2);
        var otherTeeTime = MakeTeeTime(openSpots: 2);
        await teeTimes.AddAsync(teeTime);
        await teeTimes.AddAsync(otherTeeTime);

        await service.CreateAsync(teeTime.Id, Guid.NewGuid());
        await service.CreateAsync(teeTime.Id, Guid.NewGuid());
        await service.CreateAsync(otherTeeTime.Id, Guid.NewGuid());

        var result = await service.GetForTeeTimeAsync(teeTime.Id);

        Assert.Equal(2, result.Count);
        Assert.All(result, r => Assert.Equal(teeTime.Id, r.TeeTimeId));
    }
}
