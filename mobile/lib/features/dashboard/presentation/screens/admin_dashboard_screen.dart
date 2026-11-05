import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../auth/presentation/providers/auth_provider.dart';
import '../../data/models/dashboard_models.dart';
import '../providers/dashboard_providers.dart';
import '../widgets/dashboard_widgets.dart';

/// Administrator dashboard backed by `GET /dashboard/school`.
class AdminDashboardScreen extends ConsumerWidget {
  const AdminDashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(currentUserProvider);
    final dashboard = ref.watch(adminDashboardProvider);

    return DashboardScaffold(
      title: 'Administrator',
      onRefresh: () => _refresh(ref),
      children: [
        DashboardHeader(
          eyebrow: 'School administration',
          title: user?.fullName.isNotEmpty == true
              ? user!.fullName
              : 'Administrator',
          subtitle: 'Live totals for your school',
          icon: Icons.admin_panel_settings_outlined,
          color: AppColors.roleAdmin,
        ),
        const SizedBox(height: 20),
        AsyncDashboard<SchoolDashboard>(
          value: dashboard,
          onRetry: () => _refresh(ref),
          builder: (context, data) => Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              MetricGrid(
                children: [
                  MetricCard(
                    label: 'Students',
                    value: data.totalStudents.toString(),
                    icon: Icons.groups_outlined,
                    color: AppColors.roleStudent,
                  ),
                  MetricCard(
                    label: 'Employees',
                    value: data.totalEmployees.toString(),
                    icon: Icons.badge_outlined,
                    color: AppColors.roleTeacher,
                  ),
                  MetricCard(
                    label: 'Classrooms',
                    value: data.totalClassrooms.toString(),
                    icon: Icons.meeting_room_outlined,
                    color: AppColors.roleAdmin,
                  ),
                  MetricCard(
                    label: 'Sections',
                    value: data.totalSections.toString(),
                    icon: Icons.view_list_outlined,
                    color: AppColors.primary,
                  ),
                  MetricCard(
                    label: 'Active exams',
                    value: data.totalExams.toString(),
                    icon: Icons.assignment_outlined,
                    color: AppColors.info,
                  ),
                  MetricCard(
                    label: 'Fees collected',
                    value: formatMoney(data.feesCollectedAmount),
                    icon: Icons.savings_outlined,
                    color: AppColors.success,
                  ),
                  MetricCard(
                    label: 'Fees outstanding',
                    value: formatMoney(data.feesUncollectedAmount),
                    icon: Icons.receipt_long_outlined,
                    color: AppColors.warning,
                  ),
                  MetricCard(
                    label: 'Collection rate',
                    value: formatPercent(data.collectionRate),
                    icon: Icons.trending_up,
                    color: AppColors.secondary,
                  ),
                ],
              ),
              const SizedBox(height: 20),
              SectionCard(
                title: 'Fee position',
                trailing: Text(
                  'School #${data.schoolId}',
                  style: Theme.of(context).textTheme.labelSmall?.copyWith(
                    color: AppColors.textTertiary,
                  ),
                ),
                child: Column(
                  children: [
                    InfoRow(
                      label: 'Collected',
                      value: formatMoney(data.feesCollectedAmount),
                    ),
                    InfoRow(
                      label: 'Outstanding',
                      value: formatMoney(data.feesUncollectedAmount),
                    ),
                    InfoRow(
                      label: 'Total billed',
                      value: formatMoney(data.totalFees),
                    ),
                    const SizedBox(height: 10),
                    ClipRRect(
                      borderRadius: BorderRadius.circular(6),
                      child: LinearProgressIndicator(
                        value: (data.collectionRate ?? 0) / 100,
                        minHeight: 8,
                        color: AppColors.success,
                        backgroundColor: AppColors.gray100,
                      ),
                    ),
                  ],
                ),
              ),
              if (data.generatedAt != null) ...[
                const SizedBox(height: 12),
                Text(
                  'Counted ${formatDate(data.generatedAt!.toLocal())}',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: AppColors.textTertiary,
                  ),
                ),
              ],
            ],
          ),
        ),
      ],
    );
  }

  Future<void> _refresh(WidgetRef ref) async {
    ref.invalidate(adminDashboardProvider);
    try {
      await ref.read(adminDashboardProvider.future);
    } catch (_) {
      // The AsyncDashboard error state renders the failure.
    }
  }
}
