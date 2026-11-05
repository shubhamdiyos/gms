import 'package:dio/browser.dart';
import 'package:dio/dio.dart';

/// Tells the browser to include the HttpOnly auth cookie on API requests.
void enableBrowserCredentials(Dio dio) {
  final adapter = dio.httpClientAdapter;
  if (adapter is BrowserHttpClientAdapter) {
    adapter.withCredentials = true;
  }
}
