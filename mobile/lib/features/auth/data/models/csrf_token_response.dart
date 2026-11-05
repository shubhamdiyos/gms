import 'package:equatable/equatable.dart';

/// CSRF token response from the backend.
///
/// The backend provides a CSRF token that must be included in the
/// X-XSRF-TOKEN header for all state-changing requests (POST, PUT, PATCH, DELETE).
class CsrfTokenResponse extends Equatable {
  final String token;
  final String headerName;
  final String parameterName;

  const CsrfTokenResponse({
    required this.token,
    required this.headerName,
    required this.parameterName,
  });

  factory CsrfTokenResponse.fromJson(Map<String, dynamic> json) {
    return CsrfTokenResponse(
      token: json['token'] as String,
      headerName: json['headerName'] as String? ?? 'X-XSRF-TOKEN',
      parameterName: json['parameterName'] as String? ?? '_csrf',
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'token': token,
      'headerName': headerName,
      'parameterName': parameterName,
    };
  }

  @override
  List<Object?> get props => [token, headerName, parameterName];
}
