import 'package:flutter/foundation.dart';

/// Environment configuration for the GMS mobile application.
///
/// Provides environment-specific API base URLs and configuration values.
/// The environment is determined at compile time via `--dart-define`:
///
/// ```
/// flutter run --dart-define=ENVIRONMENT=development \
///             --dart-define=API_BASE_URL=http://192.168.1.10:8080
/// ```
///
/// `API_BASE_URL`, when present, always wins. Otherwise a development build
/// targets the host machine: `localhost` on web and iOS simulators, and the
/// Android emulator's `10.0.2.2` alias. Staging and production have no
/// fallback: an unset deployment URL fails closed instead of silently calling
/// a placeholder host.
enum Environment {
  development,
  staging,
  production;

  static Environment fromString(String value) {
    return Environment.values.firstWhere(
      (env) => env.name.toLowerCase() == value.toLowerCase(),
      orElse: () => Environment.development,
    );
  }
}

class EnvironmentConfig {
  final Environment environment;
  final String apiBaseUrl;
  final bool enableLogging;

  const EnvironmentConfig({
    required this.environment,
    required this.apiBaseUrl,
    required this.enableLogging,
  });

  static const String _environmentName = String.fromEnvironment(
    'ENVIRONMENT',
    defaultValue: 'development',
  );

  static const String _baseUrlOverride = String.fromEnvironment(
    'API_BASE_URL',
  );

  static EnvironmentConfig _instance = _createDefault();

  static EnvironmentConfig get instance => _instance;

  static void initialize({String environment = _environmentName}) {
    final env = Environment.fromString(environment);
    _instance = _createFromEnvironment(env);
  }

  static EnvironmentConfig _createDefault() {
    return _createFromEnvironment(Environment.fromString(_environmentName));
  }

  static EnvironmentConfig _createFromEnvironment(Environment env) {
    switch (env) {
      case Environment.production:
        return EnvironmentConfig(
          environment: env,
          apiBaseUrl: _overrideOr('https://api.gms.com'),
          enableLogging: false,
        );
      case Environment.staging:
        return EnvironmentConfig(
          environment: env,
          apiBaseUrl: _overrideOr('https://staging-api.gms.com'),
          enableLogging: true,
        );
      case Environment.development:
        return EnvironmentConfig(
          environment: env,
          apiBaseUrl: _overrideOr(_developmentBaseUrl),
          enableLogging: true,
        );
    }
  }

  static String _overrideOr(String fallback) {
    final override = _baseUrlOverride.trim();
    return override.isEmpty ? fallback : override;
  }

  /// The host machine as seen from the platform running this build.
  static String get _developmentBaseUrl {
    if (kIsWeb) {
      return 'http://localhost:8080';
    }
    if (defaultTargetPlatform == TargetPlatform.android) {
      return 'http://10.0.2.2:8080';
    }
    return 'http://localhost:8080';
  }
}
