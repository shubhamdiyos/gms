import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../core/theme/app_theme.dart';
import '../features/auth/presentation/providers/auth_provider.dart';
import 'router.dart';

/// Root application widget.
///
/// Configures the Material app with:
/// - Theme from Stitch design system
/// - Router from GoRouter
/// - Riverpod for state management
class GmsApp extends ConsumerWidget {
  const GmsApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(routerProvider);

    return AuthInitializer(
      child: MaterialApp.router(
        title: 'GMS',
        debugShowCheckedModeBanner: false,
        theme: AppTheme.lightTheme,
        darkTheme: AppTheme.darkTheme,
        themeMode: ThemeMode.system,
        routerConfig: router,
      ),
    );
  }
}

/// Restores a session from the HttpOnly cookie before the router decides
/// whether the caller is signed in.
class AuthInitializer extends ConsumerStatefulWidget {
  final Widget child;

  const AuthInitializer({super.key, required this.child});

  @override
  ConsumerState<AuthInitializer> createState() => _AuthInitializerState();
}

class _AuthInitializerState extends ConsumerState<AuthInitializer> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(authProvider.notifier).initialize();
    });
  }

  @override
  Widget build(BuildContext context) {
    return widget.child;
  }
}
