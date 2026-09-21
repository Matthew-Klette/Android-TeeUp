using TeeUp.Api.Dtos;
using TeeUp.Api.Models;
using TeeUp.Api.Repositories;
using TeeUp.Api.Common;

namespace TeeUp.Api.Services;

public class TeeTimeService(ITeeTimeRepository teeTimeRepository, IUserRepository userRepository) : ITeeTimeService
{
    public async Task<IReadOnlyList<TeeTimeDto>> GetAllAsync(decimal? maxHandicap = null, PaceOfPlay? pace = null)
    {
        if (maxHandicap is decimal handicapFilter &&
            (handicapFilter < 0 || handicapFilter > 54 || decimal.Round(handicapFilter, 1) != handicapFilter))
            throw new DomainValidationException("Handicap must be between 0 and 54, with at most one decimal place.");
        if (pace is { } paceFilter && !Enum.IsDefined(paceFilter))
            throw new DomainValidationException("Select a valid pace of play.");
        var teeTimes = await teeTimeRepository.GetAllAsync();

        if (maxHandicap is null && pace is null)
        {
            return teeTimes.Select(TeeTimeDto.From).ToList();
        }

        var usersById = (await userRepository.GetAllAsync()).ToDictionary(u => u.Id);

        return teeTimes
            .Where(t => t.HostUserId is Guid hostId
                && usersById.TryGetValue(hostId, out var host)
                && (maxHandicap is null || (host.HandicapIndex is decimal handicap && handicap <= maxHandicap))
                && (pace is null || host.PaceOfPlay == pace))
            .Select(TeeTimeDto.From)
            .ToList();
    }
}
