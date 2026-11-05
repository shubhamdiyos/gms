import 'package:flutter_test/flutter_test.dart';
import 'package:gms_mobile/features/auth/domain/enums/user_role.dart';
import 'package:gms_mobile/features/auth/domain/entities/user.dart';
import 'package:gms_mobile/features/auth/data/models/login_request.dart';
import 'package:gms_mobile/features/auth/data/models/login_response.dart';
import 'package:gms_mobile/features/auth/data/models/csrf_token_response.dart';

void main() {
  group('UserRole', () {
    test('fromCode parses valid role codes', () {
      expect(UserRole.fromCode('ADMIN'), UserRole.admin);
      expect(UserRole.fromCode('TEACHER'), UserRole.teacher);
      expect(UserRole.fromCode('STUDENT'), UserRole.student);
      expect(UserRole.fromCode('PARENT'), UserRole.parent);
      expect(UserRole.fromCode('ACCOUNTANT'), UserRole.accountant);
      expect(UserRole.fromCode('SUPERADMIN'), UserRole.superadmin);
    });

    test('fromCode is case-insensitive', () {
      expect(UserRole.fromCode('admin'), UserRole.admin);
      expect(UserRole.fromCode('Admin'), UserRole.admin);
    });

    test('fromCode throws on unknown role', () {
      expect(() => UserRole.fromCode('UNKNOWN'), throwsArgumentError);
    });

    test('isMobileAccessible returns false for SUPERADMIN', () {
      expect(UserRole.superadmin.isMobileAccessible, false);
      expect(UserRole.admin.isMobileAccessible, true);
      expect(UserRole.teacher.isMobileAccessible, true);
      expect(UserRole.student.isMobileAccessible, true);
      expect(UserRole.parent.isMobileAccessible, true);
      expect(UserRole.accountant.isMobileAccessible, true);
    });
  });

  group('User', () {
    test('fromJson creates User from valid JSON', () {
      final json = {
        'id': 1,
        'username': 'testuser',
        'fullName': 'Test User',
        'email': 'test@example.com',
        'roles': ['TEACHER'],
        'schoolId': 1,
        'requirePasswordChange': false,
      };

      final user = User.fromJson(json);

      expect(user.id, '1');
      expect(user.username, 'testuser');
      expect(user.fullName, 'Test User');
      expect(user.email, 'test@example.com');
      expect(user.role, UserRole.teacher);
      expect(user.schoolId, 1);
      expect(user.requirePasswordChange, false);
    });

    test('fromJson handles missing optional fields', () {
      final json = {
        'username': 'testuser',
        'fullName': 'Test User',
        'roles': ['STUDENT'],
      };

      final user = User.fromJson(json);

      expect(user.id, '');
      expect(user.email, null);
      expect(user.schoolId, null);
      expect(user.requirePasswordChange, false);
    });

    test('toJson converts User to JSON', () {
      const user = User(
        id: '1',
        username: 'testuser',
        fullName: 'Test User',
        email: 'test@example.com',
        role: UserRole.admin,
        schoolId: 1,
        requirePasswordChange: false,
      );

      final json = user.toJson();

      expect(json['id'], '1');
      expect(json['username'], 'testuser');
      expect(json['fullName'], 'Test User');
      expect(json['email'], 'test@example.com');
      expect(json['role'], 'ADMIN');
      expect(json['schoolId'], 1);
      expect(json['requirePasswordChange'], false);
    });

    test('copyWith creates modified copy', () {
      const user = User(
        id: '1',
        username: 'testuser',
        fullName: 'Test User',
        role: UserRole.student,
      );

      final modified = user.copyWith(
        fullName: 'New Name',
        requirePasswordChange: true,
      );

      expect(modified.id, '1');
      expect(modified.username, 'testuser');
      expect(modified.fullName, 'New Name');
      expect(modified.requirePasswordChange, true);
    });

    test('equality works correctly', () {
      const user1 = User(
        id: '1',
        username: 'testuser',
        fullName: 'Test User',
        role: UserRole.student,
      );

      const user2 = User(
        id: '1',
        username: 'testuser',
        fullName: 'Test User',
        role: UserRole.student,
      );

      const user3 = User(
        id: '2',
        username: 'testuser',
        fullName: 'Test User',
        role: UserRole.student,
      );

      expect(user1, equals(user2));
      expect(user1, isNot(equals(user3)));
    });
  });

  group('LoginRequest', () {
    test('toJson converts to JSON', () {
      const request = LoginRequest(
        username: 'testuser',
        password: 'password123',
      );

      final json = request.toJson();

      expect(json['username'], 'testuser');
      expect(json['password'], 'password123');
    });

    test('equality is based on username only', () {
      const request1 = LoginRequest(username: 'user', password: 'pass1');
      const request2 = LoginRequest(username: 'user', password: 'pass2');
      const request3 = LoginRequest(username: 'other', password: 'pass1');

      expect(request1, equals(request2));
      expect(request1, isNot(equals(request3)));
    });
  });

  group('LoginResponse', () {
    test('fromJson creates response from valid JSON', () {
      final json = {
        'user': {
          'id': 1,
          'username': 'testuser',
          'fullName': 'Test User',
          'roles': ['ADMIN'],
        },
        'expiresIn': 3600,
        'requirePasswordChange': true,
      };

      final response = LoginResponse.fromJson(json);

      expect(response.user, isNotNull);
      expect(response.user!.username, 'testuser');
      expect(response.expiresIn, 3600);
      expect(response.requirePasswordChange, true);
    });

    test('fromJson handles null user', () {
      final json = {'expiresIn': 3600, 'requirePasswordChange': false};

      final response = LoginResponse.fromJson(json);

      expect(response.user, null);
      expect(response.expiresIn, 3600);
      expect(response.requirePasswordChange, false);
    });
  });

  group('CsrfTokenResponse', () {
    test('fromJson creates response from valid JSON', () {
      final json = {
        'token': 'abc123',
        'headerName': 'X-XSRF-TOKEN',
        'parameterName': '_csrf',
      };

      final response = CsrfTokenResponse.fromJson(json);

      expect(response.token, 'abc123');
      expect(response.headerName, 'X-XSRF-TOKEN');
      expect(response.parameterName, '_csrf');
    });

    test('fromJson uses defaults for missing fields', () {
      final json = {'token': 'abc123'};

      final response = CsrfTokenResponse.fromJson(json);

      expect(response.token, 'abc123');
      expect(response.headerName, 'X-XSRF-TOKEN');
      expect(response.parameterName, '_csrf');
    });

    test('toJson converts to JSON', () {
      const response = CsrfTokenResponse(
        token: 'abc123',
        headerName: 'X-XSRF-TOKEN',
        parameterName: '_csrf',
      );

      final json = response.toJson();

      expect(json['token'], 'abc123');
      expect(json['headerName'], 'X-XSRF-TOKEN');
      expect(json['parameterName'], '_csrf');
    });
  });
}
