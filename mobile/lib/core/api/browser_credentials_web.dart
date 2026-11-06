import 'package:dio/browser.dart';
import 'package:dio/dio.dart';

/// Tells the browser to include the HttpOnly auth cookie on API requests.
///
/// The adapter also accepts a per-request `withCredentials` override in
/// `Options.extra`; setting it on the base options covers every request and
/// does not depend on which adapter class Dio selected.
void enableBrowserCredentials(Dio dio) {
  dio.options.extra['withCredentials'] = true;
  final adapter = dio.httpClientAdapter;
  if (adapter is BrowserHttpClientAdapter) {
    adapter.withCredentials = true;
  }
}
