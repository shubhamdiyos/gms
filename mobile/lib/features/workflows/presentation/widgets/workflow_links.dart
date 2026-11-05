import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../dashboard/presentation/widgets/dashboard_widgets.dart';

class WorkflowLink {
  final String title;
  final String subtitle;
  final IconData icon;
  final String route;
  final Color color;

  const WorkflowLink({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.route,
    required this.color,
  });
}

/// The role-appropriate workflow entry points on a dashboard.
class WorkflowLinks extends StatelessWidget {
  final List<WorkflowLink> links;

  const WorkflowLinks({super.key, required this.links});

  @override
  Widget build(BuildContext context) {
    return SectionCard(
      title: 'Workflows',
      child: Column(
        children: [
          for (final link in links)
            ListTile(
              contentPadding: const EdgeInsets.symmetric(horizontal: 4),
              leading: Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: link.color.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Icon(link.icon, color: link.color, size: 20),
              ),
              title: Text(link.title),
              subtitle: Text(link.subtitle),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => context.push(link.route),
            ),
        ],
      ),
    );
  }
}

/// Shared palette for the admin workflow links.
const adminWorkflowLinks = [
  WorkflowLink(
    title: 'Students',
    subtitle: 'Search the roll and open a student record',
    icon: Icons.groups_outlined,
    route: '/students',
    color: AppColors.roleAdmin,
  ),
  WorkflowLink(
    title: 'Fee collection',
    subtitle: 'Record a payment against an outstanding fee',
    icon: Icons.account_balance_wallet_outlined,
    route: '/fees/collect',
    color: AppColors.roleAccountant,
  ),
  WorkflowLink(
    title: 'Mark attendance',
    subtitle: 'Take the register for one period',
    icon: Icons.fact_check_outlined,
    route: '/attendance/mark',
    color: AppColors.roleTeacher,
  ),
];
