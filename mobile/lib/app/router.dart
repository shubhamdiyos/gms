import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../features/auth/presentation/providers/auth_provider.dart';
import '../features/auth/presentation/screens/login_screen.dart';
import '../features/auth/presentation/screens/change_password_screen.dart';
import '../features/dashboard/presentation/screens/role_dashboard_screen.dart';

/// Router configuration for the GMS mobile application.
///
/// Uses GoRouter for declarative routing with authentication guards.
/// Routes are protected based on authentication status and user role.
final routerProvider = Provider<GoRouter>((ref) {
  final authState = ref.watch(authProvider);

  return GoRouter(
    initialLocation: '/',
    redirect: (context, state) {
      final isAuthenticated = authState.isAuthenticated;
      final isPasswordChangeRequired = authState.isPasswordChangeRequired;
      final currentPath = state.uri.path;

      // Hold on a splash screen until the stored session has been checked, so
      // a returning user is not briefly shown the login screen.
      final isSplashRoute = currentPath == '/splash';
      if (authState.status == AuthStatus.initial) {
        return isSplashRoute ? null : '/splash';
      }
      if (isSplashRoute) {
        if (!isAuthenticated) return '/login';
        return isPasswordChangeRequired ? '/change-password' : '/home';
      }

      // Public routes (no authentication required)
      final isPublicRoute = currentPath == '/login';

      // Password change route
      final isPasswordChangeRoute = currentPath == '/change-password';

      // If not authenticated, redirect to login
      if (!isAuthenticated && !isPublicRoute) {
        return '/login';
      }

      // If authenticated but password change required, redirect to change password
      if (isAuthenticated &&
          isPasswordChangeRequired &&
          !isPasswordChangeRoute) {
        return '/change-password';
      }

      // If authenticated and trying to access login, redirect to home
      if (isAuthenticated && isPublicRoute && !isPasswordChangeRequired) {
        return '/home';
      }

      // If password changed successfully, redirect to home
      if (isAuthenticated &&
          isPasswordChangeRoute &&
          !isPasswordChangeRequired) {
        return '/home';
      }

      return null;
    },
    routes: [
      GoRoute(path: '/', redirect: (context, state) => '/splash'),
      GoRoute(
        path: '/splash',
        builder: (context, state) => const SplashScreen(),
      ),
      GoRoute(path: '/login', builder: (context, state) => const LoginScreen()),
      GoRoute(
        path: '/change-password',
        builder: (context, state) => const ChangePasswordScreen(),
      ),
      GoRoute(
        path: '/home',
        builder: (context, state) => const RoleDashboardScreen(),
      ),
    ],
    errorBuilder: (context, state) => const NotFoundScreen(),
  );
});

/// Shown while the stored session is being validated.
class SplashScreen extends StatelessWidget {
  const SplashScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.school_outlined, size: 64, color: Colors.blue),
            SizedBox(height: 16),
            Text('GMS'),
            SizedBox(height: 24),
            CircularProgressIndicator(),
          ],
        ),
      ),
    );
  }
}

/// 404 Not Found screen.
class NotFoundScreen extends StatelessWidget {
  const NotFoundScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Not Found')),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.error_outline, size: 80, color: Colors.red),
            const SizedBox(height: 16),
            Text(
              'Page Not Found',
              style: Theme.of(context).textTheme.headlineMedium,
            ),
            const SizedBox(height: 8),
            const Text('The page you are looking for does not exist.'),
            const SizedBox(height: 24),
            FilledButton(
              onPressed: () => context.go('/'),
              child: const Text('Go Home'),
            ),
          ],
        ),
      ),
    );
  }
}
