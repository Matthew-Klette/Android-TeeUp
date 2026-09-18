using TeeUp.Api.Data;
using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public class EfEndorsementRepository(TeeUpDbContext context)
    : EfRepository<Endorsement>(context), IEndorsementRepository;
