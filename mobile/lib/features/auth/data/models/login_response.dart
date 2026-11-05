import 'package:equatable/equatable.dart';
import '../../domain/entities/user.dart';

/// Login response from the backend.
///
/// The backend uses HttpOnly cookies for authentication, so the response
/// does not include a token. The token is stored in the gms_auth cookie
/// which is automatically managed by the browser/HTTP client.
class LoginResponse extends Equatable {
  final User? user;
  final int? expiresIn;
  final bool requirePasswordChange;

  const LoginResponse({
    this.user,
    this.expiresIn,
    this.requirePasswordChange = false,
  });

  factory LoginResponse.fromJson(Map<String, dynamic> json) {
    return LoginResponse(
      user: json['user'] != null ? User.fromJson(json['user']) : null,
      expiresIn: json['expiresIn'] as int?,
      requirePasswordChange: json['requirePasswordChange'] as bool? ?? false,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'user': user?.toJson(),
      'expiresIn': expiresIn,
      'requirePasswordChange': requirePasswordChange,
    };
  }

  @override
  List<Object?> get props => [user, expiresIn, requirePasswordChange];
}
