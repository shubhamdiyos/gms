import 'package:dio/dio.dart';
import 'package:logger/logger.dart';
import '../../../../core/api/api_client.dart';
import '../../../../core/api/api_exceptions.dart';
import '../../../../core/constants/api_constants.dart';
import '../../domain/entities/user.dart';
import '../models/login_request.dart';
import '../models/login_response.dart';
import '../models/csrf_token_response.dart';

/// Repository for authentication operations.
///
/// Handles all authentication-related API calls including login, logout,
/// CSRF token retrieval, password change, and session validation.
///
/// Security notes:
/// - Never logs sensitive data (passwords, tokens, cookies)
/// - Uses HttpOnly cookies for authentication (not Bearer tokens)
/// - CSRF token must be fetched before any state-changing requests
class AuthRepository {
  final ApiClient _apiClient;
  final Logger _logger = Logger();

  AuthRepository({required ApiClient apiClient}) : _apiClient = apiClient;

  /// Fetches a CSRF token from the backend.
  ///
  /// This must be called before login and before any state-changing requests.
  /// The token is automatically stored in the XSRF-TOKEN cookie and also
  /// returned for client-side use.
  Future<CsrfTokenResponse> fetchCsrfToken() async {
    try {
      _logger.d('Fetching CSRF token');
      final response = await _apiClient.get(
        ApiConstants.csrfToken,
        options: Options(
          headers: {ApiConstants.headerAccept: ApiConstants.contentTypeJson},
        ),
      );

      if (response.statusCode == 200 && response.data != null) {
        final csrfToken = CsrfTokenResponse.fromJson(response.data);
        _logger.d('CSRF token retrieved successfully');
        return csrfToken;
      } else {
        throw const CsrfValidationException(
          message: 'Failed to retrieve CSRF token',
        );
      }
    } catch (e) {
      _logger.e('Error fetching CSRF token', error: e);
      rethrow;
    }
  }

  /// Authenticates a user with username and password.
  ///
  /// On success, the backend sets an HttpOnly cookie (gms_auth) containing
  /// the JWT token. The response body contains user information but NOT
  /// the token itself (for security).
  ///
  /// Returns a [LoginResponse] with user details and session information.
  /// Throws [UnauthorizedException] if credentials are invalid.
  /// Throws [PasswordChangeRequiredException] if the user must change their password.
  Future<LoginResponse> login(LoginRequest request) async {
    try {
      _logger.d('Attempting login for user: ${request.username}');

      // Ensure CSRF token is available before login
      await _apiClient.fetchCsrfToken();

      final response = await _apiClient.post(
        ApiConstants.login,
        data: request.toJson(),
        options: Options(
          headers: {
            ApiConstants.headerContentType: ApiConstants.contentTypeJson,
            ApiConstants.headerAccept: ApiConstants.contentTypeJson,
          },
        ),
      );

      if (response.statusCode == 200) {
        final loginResponse = LoginResponse.fromJson(response.data);
        _logger.d('Login successful for user: ${request.username}');
        return loginResponse;
      } else if (response.statusCode == 401) {
        _logger.w('Login failed: Invalid credentials for ${request.username}');
        throw const UnauthorizedException(
          message: 'Invalid username or password',
        );
      } else if (response.statusCode == 403) {
        final errorCode = response.data?['errorCode'];
        if (errorCode == 'PASSWORD_CHANGE_REQUIRED') {
          _logger.d('Login requires password change for ${request.username}');
          throw const PasswordChangeRequiredException();
        }
        throw ForbiddenException(
          message: response.data?['message'] ?? 'Access denied',
          data: response.data,
        );
      } else {
        throw ApiException(
          message: 'Login failed with status ${response.statusCode}',
          statusCode: response.statusCode,
          data: response.data,
        );
      }
    } catch (e) {
      if (e is ApiException) rethrow;
      _logger.e('Login error', error: e);
      throw ApiException(message: 'Login failed: ${e.toString()}', data: e);
    }
  }

  /// Logs out the current user.
  ///
  /// Clears the authentication cookie on the backend. The client should
  /// also clear local storage and cached data.
  Future<void> logout() async {
    try {
      _logger.d('Logging out');
      final response = await _apiClient.post(
        ApiConstants.logout,
        options: Options(
          headers: {
            ApiConstants.headerContentType: ApiConstants.contentTypeJson,
            ApiConstants.headerAccept: ApiConstants.contentTypeJson,
          },
        ),
      );

      if (response.statusCode == 200 || response.statusCode == 204) {
        _logger.d('Logout successful');
      } else {
        _logger.w('Logout returned status ${response.statusCode}');
      }
    } catch (e) {
      // Logout should not throw errors to the user
      _logger.e('Logout error', error: e);
    }
  }

  /// Changes the user's password.
  ///
  /// This is required for first-time login when [requirePasswordChange] is true.
  /// Throws [BadRequestException] if the old password is incorrect or the new
  /// password doesn't meet requirements.
  Future<void> changePassword({
    required String oldPassword,
    required String newPassword,
  }) async {
    try {
      _logger.d('Changing password');
      final response = await _apiClient.post(
        ApiConstants.changePassword,
        data: {'oldPassword': oldPassword, 'newPassword': newPassword},
        options: Options(
          headers: {
            ApiConstants.headerContentType: ApiConstants.contentTypeJson,
            ApiConstants.headerAccept: ApiConstants.contentTypeJson,
          },
        ),
      );

      if (response.statusCode == 200) {
        _logger.d('Password changed successfully');
      } else if (response.statusCode == 400) {
        throw BadRequestException(
          message: response.data?['message'] ?? 'Invalid password',
          data: response.data,
        );
      } else {
        throw ApiException(
          message: 'Password change failed',
          statusCode: response.statusCode,
          data: response.data,
        );
      }
    } catch (e) {
      if (e is ApiException) rethrow;
      _logger.e('Password change error', error: e);
      throw ApiException(
        message: 'Password change failed: ${e.toString()}',
        data: e,
      );
    }
  }

  /// Validates the current session by fetching the authenticated user.
  ///
  /// Returns the current [User] if the session is valid.
  /// Throws [UnauthorizedException] if the session has expired.
  Future<User> validateSession() async {
    try {
      _logger.d('Validating session');
      final response = await _apiClient.get(
        ApiConstants.currentUser,
        options: Options(
          headers: {ApiConstants.headerAccept: ApiConstants.contentTypeJson},
        ),
      );

      if (response.statusCode == 200 && response.data != null) {
        final user = User.fromJson(response.data);
        _logger.d('Session valid for user: ${user.username}');
        return user;
      } else if (response.statusCode == 401) {
        _logger.w('Session expired');
        throw const UnauthorizedException(message: 'Session expired');
      } else {
        throw ApiException(
          message: 'Session validation failed',
          statusCode: response.statusCode,
          data: response.data,
        );
      }
    } catch (e) {
      if (e is ApiException) rethrow;
      _logger.e('Session validation error', error: e);
      throw ApiException(
        message: 'Session validation failed: ${e.toString()}',
        data: e,
      );
    }
  }
}
