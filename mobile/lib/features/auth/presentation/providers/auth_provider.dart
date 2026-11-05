import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/api/api_client.dart';
import '../../../../services/cookie_service.dart';
import '../../../../services/storage_service.dart';
import '../../data/models/login_request.dart';
import '../../data/repositories/auth_repository.dart';
import '../../domain/entities/user.dart';
import '../../domain/enums/user_role.dart';

/// Authentication state enum.
enum AuthStatus {
  initial,
  loading,
  authenticated,
  unauthenticated,
  passwordChangeRequired,
  error,
}

/// Authentication state holder.
class AuthState {
  final AuthStatus status;
  final User? user;
  final String? errorMessage;
  final bool isLoading;

  const AuthState({
    this.status = AuthStatus.initial,
    this.user,
    this.errorMessage,
    this.isLoading = false,
  });

  AuthState copyWith({
    AuthStatus? status,
    User? user,
    String? errorMessage,
    bool? isLoading,
  }) {
    return AuthState(
      status: status ?? this.status,
      user: user ?? this.user,
      errorMessage: errorMessage ?? this.errorMessage,
      isLoading: isLoading ?? this.isLoading,
    );
  }

  bool get isAuthenticated => status == AuthStatus.authenticated;
  bool get isPasswordChangeRequired =>
      status == AuthStatus.passwordChangeRequired;
  UserRole? get role => user?.role;
}

/// Provider for the API client singleton.
final apiClientProvider = Provider<ApiClient>((ref) {
  return ApiClient();
});

/// Provider for the storage service singleton.
final storageServiceProvider = Provider<StorageService>((ref) {
  return StorageService();
});

/// Provider for the auth repository.
final authRepositoryProvider = Provider<AuthRepository>((ref) {
  final apiClient = ref.watch(apiClientProvider);
  return AuthRepository(apiClient: apiClient);
});

/// Main authentication state notifier.
///
/// Manages the complete authentication lifecycle including:
/// - Session restoration on app startup
/// - Login/logout flows
/// - Password change enforcement
/// - Role-based access control
class AuthNotifier extends StateNotifier<AuthState> {
  final AuthRepository _authRepository;
  final StorageService _storageService;

  AuthNotifier({
    required AuthRepository authRepository,
    required StorageService storageService,
  }) : _authRepository = authRepository,
       _storageService = storageService,
       super(const AuthState());

  /// Initializes authentication state on app startup.
  ///
  /// Attempts to restore a previous session by validating the stored
  /// authentication cookie with the backend.
  Future<void> initialize() async {
    state = state.copyWith(isLoading: true);

    try {
      // Check if we have stored auth state
      final isAuthenticated = await _storageService.isAuthenticated();
      if (!isAuthenticated) {
        state = state.copyWith(
          status: AuthStatus.unauthenticated,
          isLoading: false,
        );
        return;
      }

      // Validate session with backend
      final user = await _authRepository.validateSession();

      // Check if role is accessible from mobile
      if (!user.role.isMobileAccessible) {
        await logout();
        state = state.copyWith(
          status: AuthStatus.error,
          errorMessage: 'This role is not supported on mobile',
          isLoading: false,
        );
        return;
      }

      // Check if password change is required
      if (user.requirePasswordChange) {
        state = state.copyWith(
          status: AuthStatus.passwordChangeRequired,
          user: user,
          isLoading: false,
        );
        return;
      }

      state = state.copyWith(
        status: AuthStatus.authenticated,
        user: user,
        isLoading: false,
      );
    } catch (e) {
      // Session validation failed, clear state
      await _storageService.clearAuthData();
      await CookieService.clearAllCookies();

      state = state.copyWith(
        status: AuthStatus.unauthenticated,
        isLoading: false,
      );
    }
  }

  /// Logs in with username and password.
  Future<void> login(String username, String password) async {
    state = state.copyWith(isLoading: true, errorMessage: null);

    try {
      final request = LoginRequest(username: username, password: password);
      final response = await _authRepository.login(request);

      // Extract user from response or fetch it
      User user;
      if (response.user != null) {
        user = response.user!;
      } else {
        // If user not in response, validate session to get it
        user = await _authRepository.validateSession();
      }

      // Check if password change is required
      if (response.requirePasswordChange || user.requirePasswordChange) {
        await _storageService.saveAuthState(
          role: user.role.code,
          userId: user.id,
          schoolId: user.schoolId,
          requirePasswordChange: true,
        );

        state = state.copyWith(
          status: AuthStatus.passwordChangeRequired,
          user: user,
          isLoading: false,
        );
        return;
      }

      // Save auth state
      await _storageService.saveAuthState(
        role: user.role.code,
        userId: user.id,
        schoolId: user.schoolId,
        requirePasswordChange: false,
      );

      state = state.copyWith(
        status: AuthStatus.authenticated,
        user: user,
        isLoading: false,
      );
    } catch (e) {
      state = state.copyWith(
        status: AuthStatus.error,
        errorMessage: e.toString(),
        isLoading: false,
      );
    }
  }

  /// Changes the user's password.
  Future<void> changePassword(String oldPassword, String newPassword) async {
    state = state.copyWith(isLoading: true, errorMessage: null);

    try {
      await _authRepository.changePassword(
        oldPassword: oldPassword,
        newPassword: newPassword,
      );

      // Update stored state
      if (state.user != null) {
        await _storageService.saveAuthState(
          role: state.user!.role.code,
          userId: state.user!.id,
          schoolId: state.user!.schoolId,
          requirePasswordChange: false,
        );

        state = state.copyWith(
          status: AuthStatus.authenticated,
          user: state.user!.copyWith(requirePasswordChange: false),
          isLoading: false,
        );
      }
    } catch (e) {
      state = state.copyWith(
        status: AuthStatus.error,
        errorMessage: e.toString(),
        isLoading: false,
      );
    }
  }

  /// Logs out the current user.
  Future<void> logout() async {
    state = state.copyWith(isLoading: true);

    try {
      await _authRepository.logout();
    } finally {
      // Always clear local state, even if logout API call fails
      await _storageService.clearAuthData();
      await CookieService.clearAllCookies();

      state = const AuthState(status: AuthStatus.unauthenticated);
    }
  }

  /// Handles unauthorized (401) responses.
  Future<void> handleUnauthorized() async {
    await logout();
  }
}

/// Provider for the authentication state notifier.
final authProvider = StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  final authRepository = ref.watch(authRepositoryProvider);
  final storageService = ref.watch(storageServiceProvider);
  return AuthNotifier(
    authRepository: authRepository,
    storageService: storageService,
  );
});

/// Provider for the current user.
final currentUserProvider = Provider<User?>((ref) {
  return ref.watch(authProvider).user;
});

/// Provider for the current user's role.
final currentRoleProvider = Provider<UserRole?>((ref) {
  return ref.watch(authProvider).user?.role;
});

/// Provider for authentication status.
final authStatusProvider = Provider<AuthStatus>((ref) {
  return ref.watch(authProvider).status;
});
