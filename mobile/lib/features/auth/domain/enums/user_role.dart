/// User roles in the GMS system.
///
/// Maps to the RoleEnum in the backend. SUPERADMIN is web-only and should
/// not be used in mobile contexts.
enum UserRole {
  superadmin('SUPERADMIN'),
  admin('ADMIN'),
  teacher('TEACHER'),
  student('STUDENT'),
  parent('PARENT'),
  accountant('ACCOUNTANT');

  final String code;
  const UserRole(this.code);

  static UserRole fromCode(String code) {
    return UserRole.values.firstWhere(
      (role) => role.code == code.toUpperCase(),
      orElse: () => throw ArgumentError('Unknown role: $code'),
    );
  }

  /// Checks if this role is accessible from the mobile app.
  bool get isMobileAccessible => this != UserRole.superadmin;

  /// Returns a human-readable name for the role.
  String get displayName {
    switch (this) {
      case UserRole.superadmin:
        return 'Super Administrator';
      case UserRole.admin:
        return 'Administrator';
      case UserRole.teacher:
        return 'Teacher';
      case UserRole.student:
        return 'Student';
      case UserRole.parent:
        return 'Parent';
      case UserRole.accountant:
        return 'Accountant';
    }
  }
}
