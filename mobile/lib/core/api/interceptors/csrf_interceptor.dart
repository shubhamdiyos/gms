import 'package:dio/dio.dart';
import 'package:logger/logger.dart';
import '../api_exceptions.dart';
import '../../constants/api_constants.dart';

/// Dio interceptor that adds CSRF token to state-changing requests.
///
/// The backend requires CSRF protection for POST, PUT, PATCH, and DELETE
/// requests. This interceptor automatically retrieves the CSRF token and
/// adds it to the X-XSRF-TOKEN header.
class CsrfInterceptor extends Interceptor {
  final Logger _logger = Logger();
  String? _csrfToken;
  bool _isLoadingToken = false;
  final Dio _dio;

  CsrfInterceptor(this._dio);

  /// Sets the CSRF token manually (e.g., after retrieval from backend).
  void setCsrfToken(String token) {
    _csrfToken = token;
  }

  /// Retrieves the current CSRF token.
  String? get csrfToken => _csrfToken;

  /// Fetches a fresh CSRF token from the backend.
  ///
  /// This must be called before any state-changing requests.
  Future<void> fetchCsrfToken() async {
    if (_isLoadingToken) return;

    _isLoadingToken = true;
    try {
      final response = await _dio.get(
        ApiConstants.csrfToken,
        options: Options(
          headers: {ApiConstants.headerAccept: ApiConstants.contentTypeJson},
        ),
      );

      if (response.statusCode == 200 && response.data != null) {
        _csrfToken = response.data['token'] as String?;
        _logger.d('CSRF token retrieved successfully');
      } else {
        _logger.e('Failed to retrieve CSRF token: ${response.statusCode}');
        throw const CsrfValidationException(
          message: 'Failed to retrieve CSRF token',
        );
      }
    } catch (e) {
      _logger.e('Error fetching CSRF token', error: e);
      rethrow;
    } finally {
      _isLoadingToken = false;
    }
  }

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    // Only add CSRF token to state-changing methods
    if (_requiresCsrf(options.method)) {
      if (_csrfToken != null) {
        options.headers[ApiConstants.headerCsrfToken] = _csrfToken;
        _logger.d('CSRF token added to ${options.method} ${options.path}');
      } else {
        // FAIL-CLOSED: Prevent request from being sent without CSRF token
        _logger.e(
          'CSRF token required but unavailable for ${options.method} ${options.path}. '
          'Request blocked to prevent CSRF vulnerability.',
        );
        handler.reject(
          DioException(
            requestOptions: options,
            error: const CsrfValidationException(
              message: 'CSRF token unavailable. Cannot proceed with state-changing request.',
            ),
            type: DioExceptionType.unknown,
          ),
        );
        return;
      }
    }
    handler.next(options);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    // If CSRF validation fails, try to refresh the token
    if (err.response?.statusCode == 403 &&
        err.response?.data?['errorCode'] == 'CSRF_VALIDATION_FAILED') {
      _logger.w('CSRF validation failed, refreshing token');
      fetchCsrfToken()
          .then((_) {
            // Retry the request with the new token
            _retryRequest(err, handler);
          })
          .catchError((e) {
            handler.next(err);
          });
    } else {
      handler.next(err);
    }
  }

  bool _requiresCsrf(String method) {
    return method == 'POST' ||
        method == 'PUT' ||
        method == 'PATCH' ||
        method == 'DELETE';
  }

  Future<void> _retryRequest(
    DioException err,
    ErrorInterceptorHandler handler,
  ) async {
    try {
      final options = err.requestOptions;
      if (_csrfToken != null) {
        options.headers[ApiConstants.headerCsrfToken] = _csrfToken;
      }

      final response = await _dio.fetch(options);
      handler.resolve(response);
    } catch (e) {
      handler.next(err);
    }
  }

  /// Clears the CSRF token (e.g., on logout).
  void clearCsrfToken() {
    _csrfToken = null;
  }
}
