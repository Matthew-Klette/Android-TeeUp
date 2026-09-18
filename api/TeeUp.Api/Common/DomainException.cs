namespace TeeUp.Api.Common;

/// <summary>Business-rule violation. Mapped to 400 Bad Request by <see cref="ExceptionHandlingMiddleware"/>.</summary>
public class DomainValidationException(string message) : Exception(message);

/// <summary>Requested entity does not exist. Mapped to 404 Not Found by <see cref="ExceptionHandlingMiddleware"/>.</summary>
public class NotFoundException(string message) : Exception(message);
