import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';

/// Service for managing HTTP cookies.
///
/// Provides an initialized cookie jar and Dio interceptor for automatic
/// cookie persistence across requests. The cookie jar is initialized
/// asynchronously to ensure it's ready before any requests are made.
class CookieService {
  static CookieJar? _cookieJar;
  static final _initCompleter = _initialize();

  static Future<void> _initialize() async {
    _cookieJar = CookieJar();
  }

  /// Ensures the cookie jar is initialized before use.
  ///
  /// This must be called during app startup before any HTTP requests.
  static Future<void> ensureInitialized() async {
    await _initCompleter;
  }

  /// Returns the initialized cookie jar.
  ///
  /// Throws [StateError] if called before initialization.
  static CookieJar get cookieJar {
    if (_cookieJar == null) {
      throw StateError(
        'CookieService not initialized. Call ensureInitialized() first.',
      );
    }
    return _cookieJar!;
  }

  /// Creates a Dio interceptor for automatic cookie management.
  ///
  /// This interceptor automatically persists cookies from responses
  /// and includes them in subsequent requests.
  static CookieManager createInterceptor() {
    return CookieManager(cookieJar);
  }

  /// Clears all persisted cookies.
  ///
  /// This should be called on logout to ensure complete session cleanup.
  static Future<void> clearAllCookies() async {
    await cookieJar.deleteAll();
  }

  /// Deletes cookies for a specific URI.
  Future<void> deleteCookiesForUri(Uri uri) async {
    await cookieJar.delete(uri);
  }
}
