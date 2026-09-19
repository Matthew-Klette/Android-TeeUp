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

    private static TeeTime MakeTeeTime(int openSpots = 1) => new()
    {
        Id = Guid.NewGuid(),
        CourseId = Guid.NewGuid(),
        DateTime = DateTime.UtcNow.AddDays(1),
        OpenSpots = openSpots,
        Price = 0,
        Type = TeeTimeType.OpenRound
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
    public async Task UpdateStatusAsync_AcceptingWithinOpenSpots_Succeeds()
    {
        var (service, teeTimes, _, _) = CreateService();
        var teeTime = MakeTeeTime(openSpots: 1);
        await teeTimes.AddAsync(teeTime);
        var joinRequest = await service.CreateAsync(teeTime.Id, Guid.NewGuid());

        var result = await service.UpdateStatusAsync(joinRequest.Id, JoinRequestStatus.Accepted);

        Assert.Equal(JoinRequestStatus.Accepted, result.Status);
    }

    [Fact]
    public async Task UpdateStatusAsync_AcceptingBeyondOpenSpots_ThrowsDomainValidation()
    {
        var (service, teeTimes, _, _) = CreateService();
        var teeTime = MakeTeeTime(openSpots: 1);
        await teeTimes.AddAsync(teeTime);

        var first = await service.CreateAsync(teeTime.Id, Guid.NewGuid());
        var second = await service.CreateAsync(teeTime.Id, Guid.NewGuid());
        await service.UpdateStatusAsync(first.Id, JoinRequestStatus.Accepted);

        await Assert.ThrowsAsync<DomainValidationException>(
            () => service.UpdateStatusAsync(second.Id, JoinRequestStatus.Accepted));
    }

    [Fact]
    public async Task UpdateStatusAsync_OnAlreadyResolvedRequest_ThrowsDomainValidation()
    {
        var (service, teeTimes, _, _) = CreateService();
        var teeTime = MakeTeeTime(openSpots: 2);
        await teeTimes.AddAsync(teeTime);
        var joinRequest = await service.CreateAsync(teeTime.Id, Guid.NewGuid());
        await service.UpdateStatusAsync(joinRequest.Id, JoinRequestStatus.Declined);

        await Assert.ThrowsAsync<DomainValidationException>(
            () => service.UpdateStatusAsync(joinRequest.Id, JoinRequestStatus.Accepted));
    }

    [Fact]
    public async Task UpdateStatusAsync_ForUnknownRequest_ThrowsNotFound()
    {
        var (service, _, _, _) = CreateService();

        await Assert.ThrowsAsync<NotFoundException>(
            () => service.UpdateStatusAsync(Guid.NewGuid(), JoinRequestStatus.Accepted));
    }

    [Theory]
    [InlineData(JoinRequestStatus.Accepted, NotificationType.RequestAccepted)]
    [InlineData(JoinRequestStatus.Declined, NotificationType.RequestDeclined)]
    public async Task UpdateStatusAsync_NotifiesTheGuestOfTheOutcome(
        JoinRequestStatus status, NotificationType expectedType)
    {
        var (service, teeTimes, _, notifications) = CreateService();
        var teeTime = MakeTeeTime(openSpots: 1);
        await teeTimes.AddAsync(teeTime);
        var guestId = Guid.NewGuid();
        var joinRequest = await service.CreateAsync(teeTime.Id, guestId);

        await service.UpdateStatusAsync(joinRequest.Id, status);

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
