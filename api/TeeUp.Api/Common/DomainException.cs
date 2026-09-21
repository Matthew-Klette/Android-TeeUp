namespace TeeUp.Api.Common;

/// <summary>Business-rule violation. Mapped to 400 Bad Request by <see cref="ExceptionHandlingMiddleware"/>.</summary>
public class DomainValidationException(string message) : Exception(message);

/// <summary>Requested entity does not exist. Mapped to 404 Not Found by <see cref="ExceptionHandlingMiddleware"/>.</summary>
public class NotFoundException(string message) : Exception(message);

/// <summary>Caller is authenticated but not allowed to perform this action (EME-313: only a tee
/// time's host may accept/decline its join requests). Mapped to 403 Forbidden.</summary>
public class ForbiddenException(string message) : Exception(message);
