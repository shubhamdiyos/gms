import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:gms_mobile/services/storage_service.dart';

void main() {
  group('StorageService', () {
    late StorageService storageService;

    setUp(() async {
      SharedPreferences.setMockInitialValues({});
      storageService = StorageService();
    });

    test('saves and retrieves auth state', () async {
      await storageService.saveAuthState(
        role: 'ADMIN',
        userId: 'user-123',
        schoolId: 1,
        requirePasswordChange: false,
      );

      expect(await storageService.isAuthenticated(), isTrue);
      expect(await storageService.getUserRole(), equals('ADMIN'));
      expect(await storageService.getUserId(), equals('user-123'));
      expect(await storageService.getSchoolId(), equals(1));
      expect(await storageService.isPasswordChangeRequired(), isFalse);
    });

    test('saves auth state with password change required', () async {
      await storageService.saveAuthState(
        role: 'TEACHER',
        userId: 'user-456',
        schoolId: 2,
        requirePasswordChange: true,
      );

      expect(await storageService.isPasswordChangeRequired(), isTrue);
    });

    test('handles missing optional school ID', () async {
      await storageService.saveAuthState(role: 'STUDENT', userId: 'user-789');

      expect(await storageService.getSchoolId(), isNull);
    });

    test('clears auth data only', () async {
      await storageService.saveAuthState(
        role: 'ADMIN',
        userId: 'user-123',
        schoolId: 1,
      );

      await storageService.clearAuthData();

      expect(await storageService.isAuthenticated(), isFalse);
      expect(await storageService.getUserRole(), isNull);
      expect(await storageService.getUserId(), isNull);
      expect(await storageService.getSchoolId(), isNull);
    });

    test('clears all data', () async {
      await storageService.saveAuthState(role: 'ADMIN', userId: 'user-123');

      await storageService.clearAll();

      expect(await storageService.isAuthenticated(), isFalse);
    });

    test('returns false for isAuthenticated when no auth state', () async {
      expect(await storageService.isAuthenticated(), isFalse);
    });

    test('returns null for getters when no data stored', () async {
      expect(await storageService.getUserRole(), isNull);
      expect(await storageService.getUserId(), isNull);
      expect(await storageService.getSchoolId(), isNull);
    });

    test('returns false for isPasswordChangeRequired when not set', () async {
      expect(await storageService.isPasswordChangeRequired(), isFalse);
    });
  });
}
