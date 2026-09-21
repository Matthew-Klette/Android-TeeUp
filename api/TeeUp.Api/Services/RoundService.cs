using TeeUp.Api.Common;
using TeeUp.Api.Dtos;
using TeeUp.Api.Models;
using TeeUp.Api.Repositories;

namespace TeeUp.Api.Services;

/// <summary>
/// Enforces the "no posting rounds at unscheduled slots" rule: scores can
/// only be posted once the tee time's scheduled moment has arrived.
/// </summary>
public class RoundService(
    IRoundRepository roundRepository,
    IScorecardEntryRepository scorecardEntryRepository,
    ITeeTimeRepository teeTimeRepository,
    IJoinRequestRepository joinRequestRepository) : IRoundService
{
    public async Task<IReadOnlyList<ScheduledRoundDto>> GetScheduleForUserAsync(Guid userId)
    {
        var accepted = (await joinRequestRepository.GetAllAsync())
            .Where(j => j.GuestUserId == userId && j.Status == JoinRequestStatus.Accepted)
            .Select(j => j.TeeTimeId).ToHashSet();
        var mine = (await teeTimeRepository.GetAllAsync())
            .Where(t => t.HostUserId == userId || accepted.Contains(t.Id))
            .OrderBy(t => t.DateTime);
        var result = new List<ScheduledRoundDto>();
        foreach (var teeTime in mine)
        {
            var round = await roundRepository.GetByTeeTimeIdAsync(teeTime.Id);
            var dto = round is null ? null : RoundDto.From(round,
                await scorecardEntryRepository.GetByRoundIdAsync(round.Id));
            result.Add(new ScheduledRoundDto(teeTime.Id, teeTime.CourseId, teeTime.DateTime, dto));
        }
        return result;
    }

    public async Task<RoundDto> PostScorecardAsync(Guid teeTimeId, PostScorecardRequest request)
    {
        // Validate the whole payload before creating a round or writing any holes.
        if (request.Entries is null || request.Entries.Count is < 1 or > 18 ||
            request.Entries.Any(e => e is null || e.HoleNumber is < 1 or > 18 ||
                e.Strokes < 1 || e.Putts < 0 || e.Putts > e.Strokes) ||
            request.Entries.Select(e => e.HoleNumber).Distinct().Count() != request.Entries.Count)
        {
            throw new DomainValidationException(
                "Provide 1 to 18 unique holes, numbered 1 to 18, with positive strokes and putts between zero and strokes.");
        }

        var teeTime = await teeTimeRepository.GetByIdAsync(teeTimeId)
            ?? throw new NotFoundException($"Tee time {teeTimeId} not found.");

        if (teeTime.DateTime > DateTime.UtcNow)
        {
            throw new DomainValidationException(
                $"Tee time {teeTimeId} is scheduled for {teeTime.DateTime:u}; scores cannot be posted before it starts.");
        }

        var round = await roundRepository.GetByTeeTimeIdAsync(teeTimeId);
        if (round is null)
        {
            round = new Round { Id = Guid.NewGuid(), TeeTimeId = teeTimeId };
            await roundRepository.AddAsync(round);
        }

        foreach (var entry in request.Entries)
        {
            await scorecardEntryRepository.AddAsync(new ScorecardEntry
            {
                Id = Guid.NewGuid(),
                RoundId = round.Id,
                HoleNumber = entry.HoleNumber,
                Strokes = entry.Strokes,
                Putts = entry.Putts,
                Synced = true
            });
        }

        var scorecard = await scorecardEntryRepository.GetByRoundIdAsync(round.Id);
        return RoundDto.From(round, scorecard);
    }

    public async Task<IReadOnlyList<RoundDto>> GetRoundsForUserAsync(Guid userId)
    {
        var teeTimes = await teeTimeRepository.GetAllAsync();
        var joinRequests = await joinRequestRepository.GetAllAsync();

        var acceptedTeeTimeIds = joinRequests
            .Where(j => j.GuestUserId == userId && j.Status == JoinRequestStatus.Accepted)
            .Select(j => j.TeeTimeId)
            .ToHashSet();

        var myTeeTimeIds = teeTimes
            .Where(t => t.HostUserId == userId || acceptedTeeTimeIds.Contains(t.Id))
            .Select(t => t.Id)
            .ToHashSet();

        var results = new List<RoundDto>();
        foreach (var teeTimeId in myTeeTimeIds)
        {
            var round = await roundRepository.GetByTeeTimeIdAsync(teeTimeId);
            if (round is null)
            {
                continue;
            }

            var scorecard = await scorecardEntryRepository.GetByRoundIdAsync(round.Id);
            results.Add(RoundDto.From(round, scorecard));
        }

        return results;
    }
}
