import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:dio/dio.dart';
import 'package:logger/logger.dart';
import '../constants/api_constants.dart';
import '../config/environment.dart';
import '../../services/cookie_service.dart';
import 'browser_credentials.dart';
import 'interceptors/auth_interceptor.dart';
import 'interceptors/csrf_interceptor.dart';

/// Centralized HTTP client for all API requests.
///
/// Configures Dio with:
/// - Base URL from environment configuration
/// - Cookie management for session persistence
/// - CSRF token handling for state-changing requests
/// - Authentication error handling (401)
/// - Request/response logging in development mode
/// - Timeout configuration
class ApiClient {
  late final Dio _dio;
  late final CsrfInterceptor _csrfInterceptor;
  final Logger _logger = Logger(
    printer: PrettyPrinter(
      methodCount: 0,
      errorMethodCount: 5,
      lineLength: 50,
      colors: true,
      printEmojis: true,
      dateTimeFormat: DateTimeFormat.onlyTimeAndSinceStart,
    ),
  );

  ApiClient() {
    _dio = Dio(
      BaseOptions(
        baseUrl: EnvironmentConfig.instance.apiBaseUrl,
        connectTimeout: const Duration(seconds: 30),
        receiveTimeout: const Duration(seconds: 30),
        sendTimeout: const Duration(seconds: 30),
        headers: {
          ApiConstants.headerAccept: ApiConstants.contentTypeJson,
          ApiConstants.headerContentType: ApiConstants.contentTypeJson,
        },
        validateStatus: (status) => status != null && status < 500,
      ),
    );

    _initializeInterceptors();
  }

  void _initializeInterceptors() {
    // On the web the browser owns the HttpOnly auth cookie, so there is no
    // cookie jar to populate; native builds use one for persistence.
    if (kIsWeb) {
      enableBrowserCredentials(_dio);
    } else {
      _dio.interceptors.add(CookieService.createInterceptor());
    }

    // CSRF token management
    _csrfInterceptor = CsrfInterceptor(_dio);
    _dio.interceptors.add(_csrfInterceptor);

    // Authentication error handling
    _dio.interceptors.add(AuthInterceptor(onUnauthorized: _handleUnauthorized));

    // Logging (development only)
    if (EnvironmentConfig.instance.enableLogging) {
      _dio.interceptors.add(
        LogInterceptor(
          requestBody: false,  // SECURITY: Never log request bodies (may contain credentials)
          responseBody: false, // SECURITY: Never log response bodies (may contain sensitive data)
          requestHeader: false, // SECURITY: Never log headers (may contain auth tokens)
          responseHeader: false,
          logPrint: (obj) => _logger.d(obj.toString()),
        ),
      );
    }
  }

  void _handleUnauthorized() {
    _logger.w('Unauthorized access detected, triggering logout');
    // The actual logout logic is handled by the auth provider
    // This callback is just a notification mechanism
  }

  /// Returns the underlying Dio instance for advanced use cases.
  Dio get dio => _dio;

  /// Returns the CSRF interceptor for manual token management.
  CsrfInterceptor get csrfInterceptor => _csrfInterceptor;

  /// Fetches a fresh CSRF token from the backend.
  ///
  /// Must be called before any state-changing requests.
  Future<void> fetchCsrfToken() async {
    await _csrfInterceptor.fetchCsrfToken();
  }

  /// Performs a GET request.
  Future<Response<T>> get<T>(
    String path, {
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
  }) async {
    return await _dio.get<T>(
      path,
      queryParameters: queryParameters,
      options: options,
      cancelToken: cancelToken,
    );
  }

  /// Performs a POST request.
  Future<Response<T>> post<T>(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
  }) async {
    return await _dio.post<T>(
      path,
      data: data,
      queryParameters: queryParameters,
      options: options,
      cancelToken: cancelToken,
    );
  }

  /// Performs a PUT request.
  Future<Response<T>> put<T>(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
  }) async {
    return await _dio.put<T>(
      path,
      data: data,
      queryParameters: queryParameters,
      options: options,
      cancelToken: cancelToken,
    );
  }

  /// Performs a PATCH request.
  Future<Response<T>> patch<T>(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
  }) async {
    return await _dio.patch<T>(
      path,
      data: data,
      queryParameters: queryParameters,
      options: options,
      cancelToken: cancelToken,
    );
  }

  /// Performs a DELETE request.
  Future<Response<T>> delete<T>(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
  }) async {
    return await _dio.delete<T>(
      path,
      data: data,
      queryParameters: queryParameters,
      options: options,
      cancelToken: cancelToken,
    );
  }
}
