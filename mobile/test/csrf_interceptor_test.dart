import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:gms_mobile/core/api/interceptors/csrf_interceptor.dart';
import 'package:gms_mobile/core/constants/api_constants.dart';

class MockDio extends Mock implements Dio {}

class MockRequestInterceptorHandler extends Mock
    implements RequestInterceptorHandler {}

class FakeDioException extends Fake implements DioException {}
class FakeRequestOptions extends Fake implements RequestOptions {}
class FakeOptions extends Fake implements Options {}

void main() {
  group('CsrfInterceptor', () {
    late CsrfInterceptor interceptor;
    late MockDio dio;
    late MockRequestInterceptorHandler handler;

    setUpAll(() {
      registerFallbackValue(FakeDioException());
      registerFallbackValue(FakeRequestOptions());
      registerFallbackValue(FakeOptions());
    });

    setUp(() {
      dio = MockDio();
      interceptor = CsrfInterceptor(dio);
      handler = MockRequestInterceptorHandler();
    });

    test('adds CSRF token to POST requests', () {
      interceptor.setCsrfToken('test-csrf-token');

      final options = RequestOptions(
        path: '/api/v1/auth/login',
        method: 'POST',
        headers: {},
      );

      interceptor.onRequest(options, handler);

      expect(
        options.headers[ApiConstants.headerCsrfToken],
        equals('test-csrf-token'),
      );
      verify(() => handler.next(options)).called(1);
    });

    test('adds CSRF token to PUT requests', () {
      interceptor.setCsrfToken('test-csrf-token');

      final options = RequestOptions(
        path: '/api/v1/users/1',
        method: 'PUT',
        headers: {},
      );

      interceptor.onRequest(options, handler);

      expect(
        options.headers[ApiConstants.headerCsrfToken],
        equals('test-csrf-token'),
      );
    });

    test('adds CSRF token to PATCH requests', () {
      interceptor.setCsrfToken('test-csrf-token');

      final options = RequestOptions(
        path: '/api/v1/users/1',
        method: 'PATCH',
        headers: {},
      );

      interceptor.onRequest(options, handler);

      expect(
        options.headers[ApiConstants.headerCsrfToken],
        equals('test-csrf-token'),
      );
    });

    test('adds CSRF token to DELETE requests', () {
      interceptor.setCsrfToken('test-csrf-token');

      final options = RequestOptions(
        path: '/api/v1/users/1',
        method: 'DELETE',
        headers: {},
      );

      interceptor.onRequest(options, handler);

      expect(
        options.headers[ApiConstants.headerCsrfToken],
        equals('test-csrf-token'),
      );
    });

    test('does not add CSRF token to GET requests', () {
      interceptor.setCsrfToken('test-csrf-token');

      final options = RequestOptions(
        path: '/api/v1/users/me',
        method: 'GET',
        headers: {},
      );

      interceptor.onRequest(options, handler);

      expect(options.headers[ApiConstants.headerCsrfToken], isNull);
    });

    test('blocks request when CSRF token is missing for state-changing request', () {
      final options = RequestOptions(
        path: '/api/v1/auth/login',
        method: 'POST',
        headers: {},
      );

      interceptor.onRequest(options, handler);

      // Verify that handler.reject was called (not handler.next)
      verify(() => handler.reject(any())).called(1);
      verifyNever(() => handler.next(any()));
    });

    test('clears CSRF token on logout', () {
      interceptor.setCsrfToken('test-csrf-token');
      expect(interceptor.csrfToken, equals('test-csrf-token'));

      interceptor.clearCsrfToken();
      expect(interceptor.csrfToken, isNull);
    });

    test('fetches CSRF token successfully', () async {
      when(
        () => dio.get<dynamic>(
          ApiConstants.csrfToken,
          options: any(named: 'options'),
        ),
      ).thenAnswer(
        (_) async => Response<dynamic>(
          requestOptions: RequestOptions(path: ApiConstants.csrfToken),
          statusCode: 200,
          data: {'token': 'new-csrf-token'},
        ),
      );

      await interceptor.fetchCsrfToken();

      expect(interceptor.csrfToken, equals('new-csrf-token'));
    });
  });
}
