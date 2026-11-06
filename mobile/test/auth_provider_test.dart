import 'package:flutter_test/flutter_test.dart';
import 'package:gms_mobile/features/auth/data/models/login_request.dart';
import 'package:gms_mobile/features/auth/data/models/login_response.dart';
import 'package:gms_mobile/features/auth/data/repositories/auth_repository.dart';
import 'package:gms_mobile/features/auth/domain/entities/user.dart';
import 'package:gms_mobile/features/auth/domain/enums/user_role.dart';
import 'package:gms_mobile/features/auth/presentation/providers/auth_provider.dart';
import 'package:gms_mobile/services/cookie_service.dart';
import 'package:gms_mobile/services/storage_service.dart';
import 'package:mocktail/mocktail.dart';

class MockAuthRepository extends Mock implements AuthRepository {}

class MockStorageService extends Mock implements StorageService {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late MockAuthRepository repository;
  late MockStorageService storage;

  setUpAll(() {
    registerFallbackValue(const LoginRequest(username: '', password: ''));
    registerFallbackValue('');
    registerFallbackValue(0);
    registerFallbackValue(false);
  });

  setUp(() async {
    repository = MockAuthRepository();
    storage = MockStorageService();
    await CookieService.ensureInitialized();
    when(() => repository.logout()).thenAnswer((_) async {});
    when(() => storage.clearAuthData()).thenAnswer((_) async {});
    when(
      () => storage.saveAuthState(
        role: any(named: 'role'),
        userId: any(named: 'userId'),
        schoolId: any(named: 'schoolId'),
        requirePasswordChange: any(named: 'requirePasswordChange'),
      ),
    ).thenAnswer((_) async {});
  });

  test('login rejects the platform owner before a session is persisted',
      () async {
    when(() => repository.login(any())).thenAnswer(
      (_) async => const LoginResponse(
        user: User(
          id: '1',
          username: 'owner',
          fullName: 'Platform Owner',
          role: UserRole.superadmin,
        ),
      ),
    );

    final notifier = AuthNotifier(
      authRepository: repository,
      storageService: storage,
    );
    await notifier.login('owner', 'secret');

    expect(notifier.state.status, AuthStatus.error);
    expect(notifier.state.errorMessage, 'This role is not supported on mobile');
    expect(notifier.state.isAuthenticated, isFalse);
    verify(() => storage.clearAuthData()).called(1);
    verifyNever(
      () => storage.saveAuthState(
        role: any(named: 'role'),
        userId: any(named: 'userId'),
        schoolId: any(named: 'schoolId'),
        requirePasswordChange: any(named: 'requirePasswordChange'),
      ),
    );
  });

  test('login persists a role the mobile app serves', () async {
    when(() => repository.login(any())).thenAnswer(
      (_) async => const LoginResponse(
        user: User(
          id: '2',
          username: 'demo.teacher',
          fullName: 'Demo Teacher',
          role: UserRole.teacher,
          schoolId: 2,
        ),
      ),
    );

    final notifier = AuthNotifier(
      authRepository: repository,
      storageService: storage,
    );
    await notifier.login('demo.teacher', 'secret');

    expect(notifier.state.status, AuthStatus.authenticated);
    expect(notifier.state.role, UserRole.teacher);
    verify(
      () => storage.saveAuthState(
        role: 'TEACHER',
        userId: '2',
        schoolId: 2,
        requirePasswordChange: false,
      ),
    ).called(1);
  });
}
