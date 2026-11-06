import 'package:flutter_test/flutter_test.dart';
import 'package:gms_mobile/core/config/environment.dart';

void main() {
  const override = String.fromEnvironment('API_BASE_URL');

  test('production and staging refuse to start without an explicit API URL', () {
    expect(
      () => EnvironmentConfig.initialize(environment: 'production'),
      throwsStateError,
    );
    expect(
      () => EnvironmentConfig.initialize(environment: 'staging'),
      throwsStateError,
    );
  }, skip: override.isNotEmpty);

  test('development keeps the host-machine default', () {
    EnvironmentConfig.initialize(environment: 'development');
    expect(
      EnvironmentConfig.instance.apiBaseUrl,
      anyOf('http://localhost:8080', 'http://10.0.2.2:8080'),
    );
  }, skip: override.isNotEmpty);
}
