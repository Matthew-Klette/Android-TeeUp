using TeeUp.Api.Data;
using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public class EfTeeTimeRepository(TeeUpDbContext context) : EfRepository<TeeTime>(context), ITeeTimeRepository;
