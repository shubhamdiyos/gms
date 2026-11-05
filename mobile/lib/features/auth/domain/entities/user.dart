import 'package:equatable/equatable.dart';
import '../enums/user_role.dart';

/// Represents an authenticated user in the system.
///
/// This entity contains only the information needed for the mobile app
/// to function. Sensitive data like passwords are never stored here.
class User extends Equatable {
  final String id;
  final String username;
  final String fullName;
  final String? email;
  final UserRole role;
  final int? schoolId;
  final bool requirePasswordChange;

  const User({
    required this.id,
    required this.username,
    required this.fullName,
    this.email,
    required this.role,
    this.schoolId,
    this.requirePasswordChange = false,
  });

  /// Creates a User from a JSON map (backend response).
  factory User.fromJson(Map<String, dynamic> json) {
    final roles =
        (json['roles'] as List<dynamic>?)?.map((r) => r.toString()).toList() ??
        [];

    // Backend returns roles as a list; we take the primary role
    final primaryRole = roles.isNotEmpty
        ? UserRole.fromCode(roles.first)
        : UserRole.student;

    return User(
      id: json['id']?.toString() ?? json['uid']?.toString() ?? '',
      username: json['username'] as String? ?? '',
      fullName: json['fullName'] as String? ?? '',
      email: json['email'] as String?,
      role: primaryRole,
      schoolId: json['schoolId'] as int?,
      requirePasswordChange: json['requirePasswordChange'] as bool? ?? false,
    );
  }

  /// Converts the User to a JSON map.
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'username': username,
      'fullName': fullName,
      'email': email,
      'role': role.code,
      'schoolId': schoolId,
      'requirePasswordChange': requirePasswordChange,
    };
  }

  /// Creates a copy of this User with modified fields.
  User copyWith({
    String? id,
    String? username,
    String? fullName,
    String? email,
    UserRole? role,
    int? schoolId,
    bool? requirePasswordChange,
  }) {
    return User(
      id: id ?? this.id,
      username: username ?? this.username,
      fullName: fullName ?? this.fullName,
      email: email ?? this.email,
      role: role ?? this.role,
      schoolId: schoolId ?? this.schoolId,
      requirePasswordChange:
          requirePasswordChange ?? this.requirePasswordChange,
    );
  }

  @override
  List<Object?> get props => [
    id,
    username,
    fullName,
    email,
    role,
    schoolId,
    requirePasswordChange,
  ];

  @override
  String toString() {
    return 'User(id: $id, username: $username, role: ${role.code})';
  }
}
