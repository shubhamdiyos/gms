import 'package:shared_preferences/shared_preferences.dart';

/// Service for persistent storage of application data.
///
/// Uses SharedPreferences for cross-platform storage. In production environments
/// with full native toolchain support, this can be replaced with flutter_secure_storage
/// for encrypted keychain/keystore access.
///
/// Current implementation uses SharedPreferences which is:
/// - Pure Dart (no native build dependencies)
/// - Works on all platforms
/// - Suitable for non-sensitive configuration data
///
/// SECURITY NOTE: For production deployment with sensitive auth tokens,
/// replace with flutter_secure_storage when native build environment is available.
///
/// IMPORTANT: CSRF tokens are NOT persisted here. They are held in-memory only
/// by CsrfInterceptor and must be re-fetched on each app session.
class StorageService {
  static const _keyAuthState = 'auth_state';
  static const _keyUserRole = 'user_role';
  static const _keyUserId = 'user_id';
  static const _keySchoolId = 'school_id';
  static const _keyRequirePasswordChange = 'require_password_change';

  SharedPreferences? _prefs;

  Future<SharedPreferences> _getPrefs() async {
    _prefs ??= await SharedPreferences.getInstance();
    return _prefs!;
  }

  /// Saves authentication state to storage.
  Future<void> saveAuthState({
    required String role,
    required String userId,
    int? schoolId,
    bool requirePasswordChange = false,
  }) async {
    final prefs = await _getPrefs();
    await prefs.setString(_keyAuthState, 'authenticated');
    await prefs.setString(_keyUserRole, role);
    await prefs.setString(_keyUserId, userId);
    if (schoolId != null) {
      await prefs.setInt(_keySchoolId, schoolId);
    }
    await prefs.setBool(_keyRequirePasswordChange, requirePasswordChange);
  }

  /// Retrieves the user role from storage.
  Future<String?> getUserRole() async {
    final prefs = await _getPrefs();
    return prefs.getString(_keyUserRole);
  }

  /// Retrieves the user ID from storage.
  Future<String?> getUserId() async {
    final prefs = await _getPrefs();
    return prefs.getString(_keyUserId);
  }

  /// Retrieves the school ID from storage.
  Future<int?> getSchoolId() async {
    final prefs = await _getPrefs();
    return prefs.getInt(_keySchoolId);
  }

  /// Checks if password change is required.
  Future<bool> isPasswordChangeRequired() async {
    final prefs = await _getPrefs();
    return prefs.getBool(_keyRequirePasswordChange) ?? false;
  }

  /// Checks if the user is authenticated.
  ///
  /// NOTE: This only checks local metadata. Actual authentication requires
  /// backend session validation via /api/v1/users/me endpoint.
  Future<bool> isAuthenticated() async {
    final prefs = await _getPrefs();
    return prefs.getString(_keyAuthState) == 'authenticated';
  }

  /// Clears all authentication data from storage.
  ///
  /// This must be called on logout to ensure no sensitive data persists.
  Future<void> clearAuthData() async {
    final prefs = await _getPrefs();
    await prefs.remove(_keyAuthState);
    await prefs.remove(_keyUserRole);
    await prefs.remove(_keyUserId);
    await prefs.remove(_keySchoolId);
    await prefs.remove(_keyRequirePasswordChange);
  }

  /// Clears all data from storage.
  ///
  /// Use with caution - this removes all persisted application data.
  Future<void> clearAll() async {
    final prefs = await _getPrefs();
    await prefs.clear();
  }
}
