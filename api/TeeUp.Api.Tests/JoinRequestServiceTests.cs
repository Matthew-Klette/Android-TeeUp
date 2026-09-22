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
        var users = new InMemoryUserRepository();
        return (new JoinRequestService(joinRequests, teeTimes, notifications, users), teeTimes, joinRequests, notifications);
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
    public async Task CreateAsync_WithExistingPendingRequest_ThrowsConflict()
    {
        var (service, teeTimes, _, _) = CreateService();
        var teeTime = MakeTeeTime(openSpots: 2);
        await teeTimes.AddAsync(teeTime);
        var guestId = Guid.NewGuid();
        await service.CreateAsync(teeTime.Id, guestId);

        await Assert.ThrowsAsync<ConflictException>(
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

        // Cancelled after the request was made. Accept must still re-check the
        // tee time's current state, not just the request's.
        teeTime.Status = TeeTimeStatus.Cancelled;
        await teeTimes.UpdateAsync(teeTime);

        await Assert.ThrowsAsync<DomainValidationException>(
            () => service.UpdateStatusAsync(joinRequest.Id, JoinRequestStatus.Accepted, hostId));
    }

    [Fact]
    public async Task UpdateStatusAsync_AcceptingIntoFullTeeTime_ThrowsDomainValidation()
    {
        var (service, teeTimes, _, _) = CreateService();
        var hostId = Guid.NewGuid();
        var teeTime = MakeTeeTime(openSpots: 2, hostUserId: hostId);
        await teeTimes.AddAsync(teeTime);
        var joinRequest = await service.CreateAsync(teeTime.Id, Guid.NewGuid());

        // Marked Full without any accepted request. The explicit status check
        // must reject this on its own, since the accepted-count comparison
        // alone wouldn't catch it (0 of 2 spots are actually accepted).
        teeTime.Status = TeeTimeStatus.Full;
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

    /// <summary>
    /// A fast sanity check that the lock rejects a second accept once the first
    /// has run. The in-memory repositories complete synchronously though, so this
    /// can't prove the lock itself is enforcing it. <see cref="JoinRequestConcurrencyTests"/>
    /// is the authoritative version, against real Postgres.
    /// </summary>
    [Fact]
    public async Task UpdateStatusAsync_ConcurrentAcceptsForLastOpenSpot_ExactlyOneSucceeds()
    {
        var (service, teeTimes, _, _) = CreateService();
        var hostId = Guid.NewGuid();
        var teeTime = MakeTeeTime(openSpots: 1, hostUserId: hostId);
        await teeTimes.AddAsync(teeTime);
        var first = await service.CreateAsync(teeTime.Id, Guid.NewGuid());
        var second = await service.CreateAsync(teeTime.Id, Guid.NewGuid());

        // Both racers run on separate thread-pool threads and are released together.
        // Calling them inline as Task.WhenAll's arguments would let a synchronous
        // first call finish before the second is even constructed.
        var gate = new TaskCompletionSource();

        async Task<bool> TryAccept(Guid joinRequestId)
        {
            await gate.Task;
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

        var taskA = Task.Run(() => TryAccept(first.Id));
        var taskB = Task.Run(() => TryAccept(second.Id));
        gate.SetResult();
        var results = await Task.WhenAll(taskA, taskB);

        Assert.Single(results, succeeded => succeeded);
        Assert.Equal(TeeTimeStatus.Full, (await teeTimes.GetByIdAsync(teeTime.Id))!.Status);
    }

    [Fact]
    public async Task UpdateStatusAsync_AcceptAndDeclineOnSameRequest_ExactlyOneSucceeds()
    {
        var (service, teeTimes, _, _) = CreateService();
        var hostId = Guid.NewGuid();
        var teeTime = MakeTeeTime(openSpots: 1, hostUserId: hostId);
        await teeTimes.AddAsync(teeTime);
        var joinRequest = await service.CreateAsync(teeTime.Id, Guid.NewGuid());

        var gate = new TaskCompletionSource();

        async Task<bool> TryUpdate(JoinRequestStatus status)
        {
            await gate.Task;
            try
            {
                await service.UpdateStatusAsync(joinRequest.Id, status, hostId);
                return true;
            }
            catch (DomainValidationException)
            {
                return false;
            }
        }

        var acceptTask = Task.Run(() => TryUpdate(JoinRequestStatus.Accepted));
        var declineTask = Task.Run(() => TryUpdate(JoinRequestStatus.Declined));
        gate.SetResult();
        var results = await Task.WhenAll(acceptTask, declineTask);

        Assert.Single(results, succeeded => succeeded);
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
    public async Task UpdateStatusAsync_AcceptingAGuest_AutoDeclinesTheirOtherPendingRequests()
    {
        var (service, teeTimes, _, _) = CreateService();
        var hostA = Guid.NewGuid();
        var teeTimeA = MakeTeeTime(openSpots: 1, hostUserId: hostA);
        await teeTimes.AddAsync(teeTimeA);
        var hostB = Guid.NewGuid();
        var teeTimeB = MakeTeeTime(openSpots: 1, hostUserId: hostB);
        await teeTimes.AddAsync(teeTimeB);
        var guestId = Guid.NewGuid();
        var requestA = await service.CreateAsync(teeTimeA.Id, guestId);
        var requestB = await service.CreateAsync(teeTimeB.Id, guestId);

        await service.UpdateStatusAsync(requestA.Id, JoinRequestStatus.Accepted, hostA);

        var refreshedB = Assert.Single(await service.GetForTeeTimeAsync(teeTimeB.Id));
        Assert.Equal(JoinRequestStatus.Declined, refreshedB.Status);
        Assert.Equal(requestB.Id, refreshedB.Id);
    }

    [Fact]
    public async Task WithdrawAsync_ByRequestingGuest_RemovesThePendingRequest()
    {
        var (service, teeTimes, _, _) = CreateService();
        var teeTime = MakeTeeTime(openSpots: 1);
        await teeTimes.AddAsync(teeTime);
        var guestId = Guid.NewGuid();
        var request = await service.CreateAsync(teeTime.Id, guestId);

        await service.WithdrawAsync(request.Id, guestId);

        Assert.Empty(await service.GetForTeeTimeAsync(teeTime.Id));
    }

    [Fact]
    public async Task WithdrawAsync_ByAnotherUser_ThrowsForbidden()
    {
        var (service, teeTimes, _, _) = CreateService();
        var teeTime = MakeTeeTime(openSpots: 1);
        await teeTimes.AddAsync(teeTime);
        var request = await service.CreateAsync(teeTime.Id, Guid.NewGuid());

        await Assert.ThrowsAsync<ForbiddenException>(() => service.WithdrawAsync(request.Id, Guid.NewGuid()));
    }

    [Fact]
    public async Task WithdrawAsync_OnAlreadyDecidedRequest_ThrowsDomainValidation()
    {
        var (service, teeTimes, _, _) = CreateService();
        var hostId = Guid.NewGuid();
        var teeTime = MakeTeeTime(openSpots: 1, hostUserId: hostId);
        await teeTimes.AddAsync(teeTime);
        var guestId = Guid.NewGuid();
        var request = await service.CreateAsync(teeTime.Id, guestId);
        await service.UpdateStatusAsync(request.Id, JoinRequestStatus.Accepted, hostId);

        await Assert.ThrowsAsync<DomainValidationException>(() => service.WithdrawAsync(request.Id, guestId));
    }

    [Fact]
    public async Task WithdrawAsync_ForUnknownRequest_ThrowsNotFound()
    {
        var (service, _, _, _) = CreateService();

        await Assert.ThrowsAsync<NotFoundException>(() => service.WithdrawAsync(Guid.NewGuid(), Guid.NewGuid()));
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
