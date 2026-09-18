using TeeUp.Api.Data;
using TeeUp.Api.Models;

namespace TeeUp.Api.Repositories;

public class EfCourseRepository(TeeUpDbContext context) : EfRepository<Course>(context), ICourseRepository;
