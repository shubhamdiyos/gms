/// Base exception for all API-related errors.
///
/// Provides structured error information for consistent error handling
/// across the application.
class ApiException implements Exception {
  final String message;
  final int? statusCode;
  final String? errorCode;
  final dynamic data;

  const ApiException({
    required this.message,
    this.statusCode,
    this.errorCode,
    this.data,
  });

  @override
  String toString() {
    final buffer = StringBuffer('ApiException: $message');
    if (statusCode != null) buffer.write(' (status: $statusCode)');
    if (errorCode != null) buffer.write(' [code: $errorCode]');
    return buffer.toString();
  }
}

/// Thrown when authentication fails (401).
class UnauthorizedException extends ApiException {
  const UnauthorizedException({
    super.message = 'Unauthorized',
    super.statusCode = 401,
    super.errorCode,
    super.data,
  });
}

/// Thrown when access is forbidden (403).
class ForbiddenException extends ApiException {
  const ForbiddenException({
    super.message = 'Forbidden',
    super.statusCode = 403,
    super.errorCode,
    super.data,
  });
}

/// Thrown when a resource is not found (404).
class NotFoundException extends ApiException {
  const NotFoundException({
    super.message = 'Not found',
    super.statusCode = 404,
    super.errorCode,
    super.data,
  });
}

/// Thrown when the server encounters an error (5xx).
class ServerException extends ApiException {
  const ServerException({
    super.message = 'Server error',
    super.statusCode = 500,
    super.errorCode,
    super.data,
  });
}

/// Thrown when the network is unavailable.
class NetworkException extends ApiException {
  const NetworkException({
    super.message = 'Network error',
    super.errorCode = 'NETWORK_ERROR',
    super.data,
  });
}

/// Thrown when CSRF validation fails.
class CsrfValidationException extends ApiException {
  const CsrfValidationException({
    super.message = 'CSRF validation failed',
    super.statusCode = 403,
    super.errorCode = 'CSRF_VALIDATION_FAILED',
    super.data,
  });
}

/// Thrown when password change is required.
class PasswordChangeRequiredException extends ApiException {
  const PasswordChangeRequiredException({
    super.message = 'Password change required',
    super.statusCode = 403,
    super.errorCode = 'PASSWORD_CHANGE_REQUIRED',
    super.data,
  });
}

/// Thrown when the request is invalid (400).
class BadRequestException extends ApiException {
  const BadRequestException({
    super.message = 'Bad request',
    super.statusCode = 400,
    super.errorCode,
    super.data,
  });
}

/// Thrown when there is a conflict (409).
class ConflictException extends ApiException {
  const ConflictException({
    super.message = 'Conflict',
    super.statusCode = 409,
    super.errorCode,
    super.data,
  });
}
