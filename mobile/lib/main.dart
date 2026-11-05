import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'app/app.dart';
import 'core/config/environment.dart';
import 'services/cookie_service.dart';

/// Application entry point.
///
/// Initializes:
/// 1. Flutter bindings
/// 2. Environment configuration
/// 3. Cookie service (async initialization)
/// 4. Riverpod provider scope
/// 5. Authentication state
/// 6. Root app widget
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Initialize environment configuration
  // In production, this would read from --dart-define or environment variables
  const environment = String.fromEnvironment(
    'ENVIRONMENT',
    defaultValue: 'development',
  );
  EnvironmentConfig.initialize(environment: environment);

  // Initialize cookie service (must be done before any HTTP requests)
  await CookieService.ensureInitialized();

  runApp(const ProviderScope(child: GmsApp()));
}
