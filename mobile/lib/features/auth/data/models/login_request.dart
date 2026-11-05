import 'package:equatable/equatable.dart';

/// Login request payload.
///
/// Matches the backend LoginRequest DTO.
class LoginRequest extends Equatable {
  final String username;
  final String password;

  const LoginRequest({required this.username, required this.password});

  Map<String, dynamic> toJson() {
    return {'username': username, 'password': password};
  }

  @override
  List<Object?> get props => [username];

  @override
  bool get stringify => false;
}
