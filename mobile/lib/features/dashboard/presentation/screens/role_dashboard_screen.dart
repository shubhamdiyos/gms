import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../auth/domain/enums/user_role.dart';
import '../../../auth/presentation/providers/auth_provider.dart';
import 'accountant_dashboard_screen.dart';
import 'admin_dashboard_screen.dart';
import 'parent_dashboard_screen.dart';
import 'student_dashboard_screen.dart';
import 'teacher_dashboard_screen.dart';

/// Chooses the dashboard that belongs to the signed-in role.
///
/// The role comes from the session the backend issued, never from a route
/// parameter, so a user cannot navigate into another role's dashboard.
class RoleDashboardScreen extends ConsumerWidget {
  const RoleDashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final role = ref.watch(currentRoleProvider);

    return switch (role) {
      UserRole.admin => const AdminDashboardScreen(),
      UserRole.teacher => const TeacherDashboardScreen(),
      UserRole.student => const StudentDashboardScreen(),
      UserRole.parent => const ParentDashboardScreen(),
      UserRole.accountant => const AccountantDashboardScreen(),
      UserRole.superadmin || null => const _UnsupportedRoleScreen(),
    };
  }
}

class _UnsupportedRoleScreen extends ConsumerWidget {
  const _UnsupportedRoleScreen();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      appBar: AppBar(title: const Text('GMS')),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(
                Icons.devices_other_outlined,
                size: 64,
                color: AppColors.textTertiary,
              ),
              const SizedBox(height: 16),
              Text(
                'This role signs in on the web',
                style: Theme.of(context).textTheme.titleLarge,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 8),
              Text(
                'The mobile application supports administrators, teachers, '
                'students, parents and accountants.',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: AppColors.textSecondary,
                ),
              ),
              const SizedBox(height: 24),
              FilledButton(
                onPressed: () async {
                  await ref.read(authProvider.notifier).logout();
                  if (context.mounted) {
                    context.go('/login');
                  }
                },
                child: const Text('Sign out'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
