import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../auth/presentation/providers/auth_provider.dart';
import '../../data/models/dashboard_models.dart';
import '../providers/dashboard_providers.dart';
import '../widgets/dashboard_widgets.dart';

/// Parent dashboard: one section per linked child.
class ParentDashboardScreen extends ConsumerWidget {
  const ParentDashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(currentUserProvider);
    final dashboard = ref.watch(parentDashboardProvider);

    return DashboardScaffold(
      title: 'Parent',
      onRefresh: () => _refresh(ref),
      children: [
        DashboardHeader(
          eyebrow: 'Parent portal',
          title: user?.fullName.isNotEmpty == true
              ? user!.fullName
              : 'Parent',
          subtitle: "Your children's attendance, results and fees",
          icon: Icons.family_restroom_outlined,
          color: AppColors.roleParent,
        ),
        const SizedBox(height: 20),
        AsyncDashboard<ParentDashboardData>(
          value: dashboard,
          onRetry: () => _refresh(ref),
          builder: (context, data) {
            final rates = data.children
                .map((child) => child.attendance.rate)
                .whereType<double>()
                .toList();
            final averageRate = rates.isEmpty
                ? null
                : rates.reduce((a, b) => a + b) / rates.length;
            final totalOutstanding = data.children.fold<double>(
              0,
              (sum, child) => sum + child.fees.totalOutstanding,
            );
            final totalResults = data.children.fold<int>(
              0,
              (sum, child) => sum + child.results.length,
            );

            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                MetricGrid(
                  children: [
                    MetricCard(
                      label: 'Children',
                      value: data.children.length.toString(),
                      icon: Icons.family_restroom_outlined,
                      color: AppColors.roleParent,
                    ),
                    MetricCard(
                      label: 'Fees outstanding',
                      value: formatMoney(totalOutstanding),
                      icon: Icons.receipt_long_outlined,
                      color: AppColors.warning,
                    ),
                    MetricCard(
                      label: 'Average attendance',
                      value: formatPercent(averageRate),
                      icon: Icons.fact_check_outlined,
                      color: AppColors.roleStudent,
                    ),
                    MetricCard(
                      label: 'Results recorded',
                      value: totalResults.toString(),
                      icon: Icons.assignment_turned_in_outlined,
                      color: AppColors.primary,
                    ),
                  ],
                ),
                const SizedBox(height: 20),
                if (data.children.isEmpty)
                  const SectionCard(
                    title: 'Children',
                    child: EmptySection(
                      message:
                          'No students are linked to this parent account yet.',
                      icon: Icons.family_restroom_outlined,
                    ),
                  )
                else
                  for (final child in data.children) ...[
                    _ChildSection(data: child),
                    const SizedBox(height: 16),
                  ],
                SectionCard(
                  title: 'Contact',
                  child: Column(
                    children: [
                      InfoRow(
                        label: 'Name',
                        value: data.profile.fullName,
                      ),
                      InfoRow(
                        label: 'Email',
                        value: data.profile.email ?? '—',
                      ),
                      InfoRow(
                        label: 'Phone',
                        value: data.profile.phoneNumber ?? '—',
                      ),
                    ],
                  ),
                ),
              ],
            );
          },
        ),
      ],
    );
  }

  Future<void> _refresh(WidgetRef ref) async {
    ref.invalidate(parentDashboardProvider);
    try {
      await ref.read(parentDashboardProvider.future);
    } catch (_) {
      // The AsyncDashboard error state renders the failure.
    }
  }
}

class _ChildSection extends StatelessWidget {
  final ChildDashboardData data;

  const _ChildSection({required this.data});

  @override
  Widget build(BuildContext context) {
    final child = data.child;
    return SectionCard(
      title: child.fullName,
      trailing: child.studentId.isEmpty
          ? null
          : Text(
              'ID ${child.studentId}',
              style: Theme.of(context).textTheme.labelSmall?.copyWith(
                color: AppColors.textTertiary,
              ),
            ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          AttendanceMeter(summary: data.attendance),
          const Divider(height: 28),
          InfoRow(
            label: 'Fees billed',
            value: formatMoney(data.fees.totalBilled),
          ),
          InfoRow(
            label: 'Fees paid',
            value: formatMoney(data.fees.totalPaid),
          ),
          InfoRow(
            label: 'Fees outstanding',
            value: formatMoney(data.fees.totalOutstanding),
          ),
          if (data.fees.outstanding.isNotEmpty) ...[
            const SizedBox(height: 8),
            for (final fee in data.fees.outstanding.take(3))
              PersonRow(
                name: fee.label,
                subtitle: 'Due ${formatDate(fee.dueDate)}',
                icon: Icons.receipt_long_outlined,
                trailing: Text(
                  formatMoney(fee.balanceAmount),
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
              ),
          ],
          const Divider(height: 28),
          Text(
            'Recent results',
            style: Theme.of(context).textTheme.labelLarge?.copyWith(
              color: AppColors.textSecondary,
            ),
          ),
          const SizedBox(height: 8),
          if (data.results.isEmpty)
            const EmptySection(
              message: 'No results have been published yet.',
              icon: Icons.assignment_outlined,
            )
          else
            for (final result in data.results.take(3))
              PersonRow(
                name: result.subjectName ?? 'Subject',
                subtitle: result.examName ?? 'Exam',
                icon: Icons.assignment_turned_in_outlined,
                trailing: Text(
                  [
                    if (result.percentage != null)
                      formatPercent(result.percentage),
                    if ((result.grade ?? '').isNotEmpty)
                      'Grade ${result.grade}',
                  ].join(' · '),
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: AppColors.textSecondary,
                  ),
                ),
              ),
        ],
      ),
    );
  }
}
