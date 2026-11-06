import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// [Storage] backed by SharedPreferences so the session cookie survives app
/// restarts without pulling in a path/file plugin.
class _PreferencesCookieStorage implements Storage {
  _PreferencesCookieStorage(this._preferences);

  static const _keyPrefix = 'gms.cookie.';
  final SharedPreferences _preferences;

  String _key(String key) => '$_keyPrefix$key';

  @override
  Future<void> init(bool persistSession, bool ignoreExpires) async {}

  @override
  Future<String?> read(String key) async => _preferences.getString(_key(key));

  @override
  Future<void> write(String key, String value) async {
    await _preferences.setString(_key(key), value);
  }

  @override
  Future<void> delete(String key) async {
    await _preferences.remove(_key(key));
  }

  @override
  Future<void> deleteAll(List<String> keys) async {
    for (final key in keys) {
      await _preferences.remove(_key(key));
    }
  }
}

/// Service for managing HTTP cookies.
///
/// Provides an initialized cookie jar and Dio interceptor for automatic
/// cookie persistence across requests. The cookie jar is initialized
/// asynchronously to ensure it's ready before any requests are made.
class CookieService {
  static CookieJar? _cookieJar;
  static final _initCompleter = _initialize();

  static Future<void> _initialize() async {
    if (kIsWeb) {
      // The browser owns the cookie store; an in-memory jar is enough for the
      // request/response plumbing here.
      _cookieJar = CookieJar();
      return;
    }
    try {
      final preferences = await SharedPreferences.getInstance();
      _cookieJar = PersistCookieJar(
        storage: _PreferencesCookieStorage(preferences),
      );
    } catch (_) {
      // Plugin-free environments (widget tests) keep working in memory.
      _cookieJar = CookieJar();
    }
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
