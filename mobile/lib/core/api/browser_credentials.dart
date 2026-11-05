import 'package:dio/dio.dart';

import 'browser_credentials_stub.dart'
    if (dart.library.js_interop) 'browser_credentials_web.dart' as platform;

/// Enables browser credential handling for cross-origin API calls.
///
/// The authentication cookie is HttpOnly, so on the web the browser — not
/// Dart — must attach it. On non-web platforms this is a no-op.
void enableBrowserCredentials(Dio dio) {
  platform.enableBrowserCredentials(dio);
}
