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
    IJoinRequestRepository joinRequestRepository,
    ICourseRepository courseRepository,
    IUserRepository userRepository) : IRoundService
{
    public async Task<IReadOnlyList<ScheduledRoundDto>> GetScheduleForUserAsync(Guid userId)
    {
        // Net score/Stableford (EME-304) are always relative to the viewer's own handicap,
        // not whoever hosts/guests a given tee time — "my rounds" is a personal stats view.
        var handicapIndex = (await userRepository.GetByIdAsync(userId))?.HandicapIndex;

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
            RoundDto? dto = null;
            if (round is not null)
            {
                var coursePar = (await courseRepository.GetByIdAsync(teeTime.CourseId))?.Par ?? 72;
                dto = RoundDto.From(round, await scorecardEntryRepository.GetByRoundIdAsync(round.Id), coursePar, handicapIndex);
            }
            result.Add(new ScheduledRoundDto(teeTime.Id, teeTime.CourseId, teeTime.DateTime, teeTime.Holes, dto));
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

        // Belt-and-braces beyond the generic 1-18 check above: this tee time's own round length (9 or 18) caps what's valid here too, since a client bug once let a 9-hole round post spurious hole 10+ entries.
        if (teeTime.Holes is int holeLimit && request.Entries.Any(e => e.HoleNumber > holeLimit))
        {
            throw new DomainValidationException(
                $"Tee time {teeTimeId} is a {holeLimit}-hole round; hole numbers cannot exceed {holeLimit}.");
        }

        // Serializes the round find-or-create and the per-hole upsert below against any other post/delete for this tee time. See RoundEntryLock.
        using var _ = await RoundEntryLock.AcquireAsync(teeTimeId);

        var round = await roundRepository.GetByTeeTimeIdAsync(teeTimeId);
        if (round is null)
        {
            round = new Round { Id = Guid.NewGuid(), TeeTimeId = teeTimeId };
            await roundRepository.AddAsync(round);
        }

        // Upsert per hole rather than always inserting: a hole that already has an entry gets its score replaced in place, so a retried or double-submitted post can't create a duplicate row that double-counts strokes/putts.
        var existingByHole = (await scorecardEntryRepository.GetByRoundIdAsync(round.Id))
            .ToDictionary(e => e.HoleNumber);

        foreach (var entry in request.Entries)
        {
            if (existingByHole.TryGetValue(entry.HoleNumber, out var existing))
            {
                existing.Strokes = entry.Strokes;
                existing.Putts = entry.Putts;
                existing.Synced = true;
                await scorecardEntryRepository.UpdateAsync(existing);
            }
            else
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
        }

        var scorecard = await scorecardEntryRepository.GetByRoundIdAsync(round.Id);
        var coursePar = (await courseRepository.GetByIdAsync(teeTime.CourseId))?.Par ?? 72;
        // No caller id flows into this endpoint (see IRoundService's doc comment on
        // DeleteScorecardEntryAsync re: the lack of a post-time authorization check), so net
        // score/Stableford here use the tee time host's handicap — the closest stand-in for
        // "whoever is scoring this round" without one.
        var handicapIndex = teeTime.HostUserId is Guid hostId
            ? (await userRepository.GetByIdAsync(hostId))?.HandicapIndex
            : null;
        return RoundDto.From(round, scorecard, coursePar, handicapIndex);
    }

    public async Task DeleteScorecardEntryAsync(Guid roundId, int holeNumber, Guid callerId)
    {
        var round = await roundRepository.GetByIdAsync(roundId)
            ?? throw new NotFoundException($"Round {roundId} not found.");

        var teeTime = await teeTimeRepository.GetByIdAsync(round.TeeTimeId)
            ?? throw new NotFoundException($"Tee time {round.TeeTimeId} not found.");

        var isAcceptedGuest = (await joinRequestRepository.GetByTeeTimeIdAsync(teeTime.Id))
            .Any(j => j.GuestUserId == callerId && j.Status == JoinRequestStatus.Accepted);

        if (teeTime.HostUserId != callerId && !isAcceptedGuest)
        {
            throw new ForbiddenException("Only this round's host or an accepted guest can delete a scorecard entry.");
        }

        // Same lock PostScorecardAsync takes, so a delete can't race a concurrent post's read of
        // which holes already exist for this round.
        using var _ = await RoundEntryLock.AcquireAsync(teeTime.Id);

        var entry = (await scorecardEntryRepository.GetByRoundIdAsync(roundId))
            .FirstOrDefault(e => e.HoleNumber == holeNumber)
            ?? throw new NotFoundException($"No scorecard entry for hole {holeNumber} on round {roundId}.");

        await scorecardEntryRepository.DeleteAsync(entry.Id);
    }

    public async Task<IReadOnlyList<RoundDto>> GetRoundsForUserAsync(Guid userId)
    {
        var handicapIndex = (await userRepository.GetByIdAsync(userId))?.HandicapIndex;

        var teeTimes = await teeTimeRepository.GetAllAsync();
        var joinRequests = await joinRequestRepository.GetAllAsync();

        var acceptedTeeTimeIds = joinRequests
            .Where(j => j.GuestUserId == userId && j.Status == JoinRequestStatus.Accepted)
            .Select(j => j.TeeTimeId)
            .ToHashSet();

        var myTeeTimes = teeTimes
            .Where(t => t.HostUserId == userId || acceptedTeeTimeIds.Contains(t.Id))
            .ToList();

        var results = new List<RoundDto>();
        foreach (var teeTime in myTeeTimes)
        {
            var round = await roundRepository.GetByTeeTimeIdAsync(teeTime.Id);
            if (round is null)
            {
                continue;
            }

            var scorecard = await scorecardEntryRepository.GetByRoundIdAsync(round.Id);
            var coursePar = (await courseRepository.GetByIdAsync(teeTime.CourseId))?.Par ?? 72;
            results.Add(RoundDto.From(round, scorecard, coursePar, handicapIndex));
        }

        return results;
    }
}
