import 'package:dio/dio.dart';
import 'package:logger/logger.dart';

/// Dio interceptor that handles authentication errors (401).
///
/// Prevents recursive logout calls and provides consistent error handling
/// for authentication failures. When a 401 is received, this interceptor
/// triggers a logout flow without causing infinite loops.
class AuthInterceptor extends Interceptor {
  final Logger _logger = Logger();
  final void Function() _onUnauthorized;
  bool _isHandlingUnauthorized = false;

  AuthInterceptor({required void Function() onUnauthorized})
    : _onUnauthorized = onUnauthorized;

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    if (err.response?.statusCode == 401) {
      _handleUnauthorized(err, handler);
    } else {
      handler.next(err);
    }
  }

  void _handleUnauthorized(DioException err, ErrorInterceptorHandler handler) {
    // Prevent recursive calls
    if (_isHandlingUnauthorized) {
      _logger.w('Already handling unauthorized, skipping recursive call');
      handler.next(err);
      return;
    }

    _isHandlingUnauthorized = true;
    _logger.w('Unauthorized (401) received for ${err.requestOptions.path}');

    try {
      _onUnauthorized();
      handler.next(err);
    } finally {
      _isHandlingUnauthorized = false;
    }
  }
}
