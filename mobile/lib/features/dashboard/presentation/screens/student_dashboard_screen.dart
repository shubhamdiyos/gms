import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../auth/presentation/providers/auth_provider.dart';
import '../../data/models/dashboard_models.dart';
import '../providers/dashboard_providers.dart';
import '../widgets/dashboard_widgets.dart';

/// Student dashboard backed by the student portal endpoints.
class StudentDashboardScreen extends ConsumerWidget {
  const StudentDashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(currentUserProvider);
    final dashboard = ref.watch(studentDashboardProvider);

    return DashboardScaffold(
      title: 'Student',
      onRefresh: () => _refresh(ref),
      children: [
        DashboardHeader(
          eyebrow: 'Student portal',
          title: user?.fullName.isNotEmpty == true
              ? user!.fullName
              : 'Student',
          subtitle: 'Attendance, results, fees and your timetable',
          icon: Icons.menu_book_outlined,
          color: AppColors.roleStudent,
        ),
        const SizedBox(height: 20),
        AsyncDashboard<StudentDashboardData>(
          value: dashboard,
          onRetry: () => _refresh(ref),
          builder: (context, data) => Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (data.classroom != null || data.profile.studentId.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(bottom: 16),
                  child: Text(
                    [
                      if (data.classroom != null) data.classroom!.name,
                      if (data.profile.studentId.isNotEmpty)
                        'ID ${data.profile.studentId}',
                    ].join(' · '),
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: AppColors.textSecondary,
                    ),
                  ),
                ),
              MetricGrid(
                children: [
                  MetricCard(
                    label: 'Attendance (30 days)',
                    value: formatPercent(data.attendance.rate),
                    icon: Icons.fact_check_outlined,
                    color: AppColors.roleStudent,
                  ),
                  MetricCard(
                    label: 'Days present',
                    value: data.attendance.present.toString(),
                    icon: Icons.check_circle_outline,
                    color: AppColors.success,
                  ),
                  MetricCard(
                    label: 'Fees outstanding',
                    value: formatMoney(data.fees.totalOutstanding),
                    icon: Icons.receipt_long_outlined,
                    color: AppColors.warning,
                  ),
                  MetricCard(
                    label: 'Results published',
                    value: data.results.length.toString(),
                    icon: Icons.assignment_turned_in_outlined,
                    color: AppColors.primary,
                  ),
                ],
              ),
              const SizedBox(height: 20),
              SectionCard(
                title: 'Attendance',
                trailing: Text(
                  'Last 30 days',
                  style: Theme.of(context).textTheme.labelSmall?.copyWith(
                    color: AppColors.textTertiary,
                  ),
                ),
                child: AttendanceMeter(summary: data.attendance),
              ),
              const SizedBox(height: 16),
              SectionCard(
                title: 'Fees',
                child: Column(
                  children: [
                    InfoRow(
                      label: 'Billed',
                      value: formatMoney(data.fees.totalBilled),
                    ),
                    InfoRow(
                      label: 'Paid',
                      value: formatMoney(data.fees.totalPaid),
                    ),
                    InfoRow(
                      label: 'Outstanding',
                      value: formatMoney(data.fees.totalOutstanding),
                    ),
                    const SizedBox(height: 12),
                    if (data.fees.isEmpty)
                      const EmptySection(
                        message: 'No fees have been assigned yet.',
                        icon: Icons.receipt_long_outlined,
                      )
                    else if (data.fees.outstanding.isEmpty)
                      const EmptySection(
                        message: 'No outstanding fees. You are up to date.',
                        icon: Icons.check_circle_outline,
                      )
                    else
                      for (final fee in data.fees.outstanding.take(5))
                        PersonRow(
                          name: fee.label,
                          subtitle: [
                            if ((fee.academicYear ?? '').isNotEmpty)
                              fee.academicYear!,
                            'Due ${formatDate(fee.dueDate)}',
                          ].join(' · '),
                          icon: Icons.receipt_long_outlined,
                          trailing: Column(
                            mainAxisSize: MainAxisSize.min,
                            crossAxisAlignment: CrossAxisAlignment.end,
                            children: [
                              Text(
                                formatMoney(fee.balanceAmount),
                                style: Theme.of(context).textTheme.bodyMedium,
                              ),
                              const SizedBox(height: 4),
                              StatusPill(status: fee.status),
                            ],
                          ),
                        ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              SectionCard(
                title: 'Recent results',
                child: data.results.isEmpty
                    ? const EmptySection(
                        message: 'No results have been published yet.',
                        icon: Icons.assignment_outlined,
                      )
                    : Column(
                        children: [
                          for (final result in data.results.take(5))
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
                                style: Theme.of(context).textTheme.bodySmall
                                    ?.copyWith(
                                      color: AppColors.textSecondary,
                                    ),
                              ),
                            ),
                        ],
                      ),
              ),
              const SizedBox(height: 16),
              SectionCard(
                title: "Today's timetable",
                child: ScheduleList(
                  slots: data.todaySlots,
                  emptyMessage: 'No periods are scheduled for today.',
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Future<void> _refresh(WidgetRef ref) async {
    ref.invalidate(studentDashboardProvider);
    try {
      await ref.read(studentDashboardProvider.future);
    } catch (_) {
      // The AsyncDashboard error state renders the failure.
    }
  }
}
