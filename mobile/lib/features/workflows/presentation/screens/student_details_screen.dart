import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../dashboard/data/models/dashboard_models.dart';
import '../../../dashboard/presentation/widgets/dashboard_widgets.dart';
import '../../data/models/workflow_models.dart';
import '../providers/workflow_providers.dart';
import '../widgets/workflow_widgets.dart';

/// One student's record: profile, 30-day attendance, results and fees.
class StudentDetailsScreen extends ConsumerWidget {
  final int studentId;

  const StudentDetailsScreen({super.key, required this.studentId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final details = ref.watch(studentDetailsProvider(studentId));

    return WorkflowScaffold(
      title: 'Student record',
      children: [
        AsyncContent<StudentDetailsBundle>(
          value: details,
          onRetry: () async {
            ref.invalidate(studentDetailsProvider(studentId));
            try {
              await ref.read(studentDetailsProvider(studentId).future);
            } catch (_) {
              // Rendered by the error state.
            }
          },
          errorTitle: 'Could not load this student',
          builder: (context, data) => Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _profile(context, data.profile),
              const SizedBox(height: 16),
              _attendance(context, data.attendance),
              const SizedBox(height: 16),
              _results(context, data.results),
              const SizedBox(height: 16),
              _fees(context, data.fees),
            ],
          ),
        ),
      ],
    );
  }

  Widget _profile(BuildContext context, StudentProfile profile) {
    return SectionCard(
      title: profile.fullName,
      trailing: StatusPill(status: profile.isActive ? 'ACTIVE' : 'INACTIVE'),
      child: Column(
        children: [
          WorkflowInfoRow(
            label: 'Student ID',
            value: profile.studentId.isEmpty ? '—' : profile.studentId,
          ),
          WorkflowInfoRow(
            label: 'Classroom',
            value: profile.classId == null ? '—' : 'Class #${profile.classId}',
          ),
          WorkflowInfoRow(
            label: 'Email',
            value: profile.email ?? '—',
          ),
          WorkflowInfoRow(
            label: 'Phone',
            value: profile.phoneNumber ?? '—',
          ),
          WorkflowInfoRow(
            label: 'Date of birth',
            value: formatDate(profile.dateOfBirth),
          ),
          WorkflowInfoRow(
            label: 'Gender',
            value: profile.gender ?? '—',
          ),
        ],
      ),
    );
  }

  Widget _attendance(BuildContext context, List<AttendanceRecord> records) {
    final present = records.where((r) => r.status == 'PRESENT').length;
    final late = records.where((r) => r.status == 'LATE').length;
    final absent = records.where((r) => r.status == 'ABSENT').length;
    final recorded = present + late + absent;
    final rate = recorded == 0 ? null : (present + late) / recorded * 100;

    return SectionCard(
      title: 'Attendance',
      trailing: Text(
        'Last 30 days',
        style: Theme.of(context).textTheme.labelSmall?.copyWith(
          color: AppColors.textTertiary,
        ),
      ),
      child: records.isEmpty
          ? const EmptySection(
              message: 'No attendance has been recorded in the last 30 days.',
              icon: Icons.event_busy_outlined,
            )
          : Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Attendance rate ${formatPercent(rate)}',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 4),
                Text(
                  'Present $present · Late $late · Absent $absent · '
                  '$recorded periods marked',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: AppColors.textSecondary,
                  ),
                ),
                const SizedBox(height: 12),
                for (final record in records.take(10))
                  Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: Row(
                      children: [
                        SizedBox(
                          width: 100,
                          child: Text(
                            formatDate(record.date),
                            style: Theme.of(context).textTheme.bodySmall,
                          ),
                        ),
                        Expanded(
                          child: Text(
                            record.subjectName ??
                                (record.period == null
                                    ? 'Period'
                                    : 'Period ${record.period}'),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: Theme.of(context).textTheme.bodyMedium,
                          ),
                        ),
                        StatusPill(status: record.status),
                      ],
                    ),
                  ),
                if (records.length > 10)
                  Text(
                    'Showing the latest 10 of ${records.length} records.',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: AppColors.textTertiary,
                    ),
                  ),
              ],
            ),
    );
  }

  Widget _results(BuildContext context, List<ExamResult> results) {
    return SectionCard(
      title: 'Results',
      child: results.isEmpty
          ? const EmptySection(
              message: 'No exam results have been published yet.',
              icon: Icons.assessment_outlined,
            )
          : Column(
              children: [
                for (final result in results)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child: Row(
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                result.examName ?? 'Exam',
                                style: Theme.of(context).textTheme.bodyLarge,
                              ),
                              Text(
                                result.subjectName ?? 'Subject',
                                style: Theme.of(context).textTheme.bodySmall
                                    ?.copyWith(
                                      color: AppColors.textSecondary,
                                    ),
                              ),
                            ],
                          ),
                        ),
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.end,
                          children: [
                            Text(
                              result.obtainedMarks == null
                                  ? '—'
                                  : '${result.obtainedMarks}',
                              style: Theme.of(context).textTheme.titleMedium,
                            ),
                            Text(
                              result.grade ??
                                  formatPercent(result.percentage),
                              style: Theme.of(context).textTheme.labelSmall
                                  ?.copyWith(color: AppColors.textTertiary),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
              ],
            ),
    );
  }

  Widget _fees(BuildContext context, List<FeeRecord> fees) {
    return SectionCard(
      title: 'Fees',
      trailing: Text(
        '${formatMoney(fees.totalOutstanding)} outstanding',
        style: Theme.of(context).textTheme.labelSmall?.copyWith(
          color: AppColors.textTertiary,
        ),
      ),
      child: fees.isEmpty
          ? const EmptySection(
              message: 'No fees have been assigned to this student.',
              icon: Icons.receipt_long_outlined,
            )
          : Column(
              children: [
                for (final fee in fees)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child: Row(
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                fee.label,
                                style: Theme.of(context).textTheme.bodyLarge,
                              ),
                              Text(
                                '${formatMoney(fee.amountPaid)} paid of '
                                '${formatMoney(fee.totalAmount)}',
                                style: Theme.of(context).textTheme.bodySmall
                                    ?.copyWith(
                                      color: AppColors.textSecondary,
                                    ),
                              ),
                            ],
                          ),
                        ),
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.end,
                          children: [
                            Text(
                              formatMoney(fee.balanceAmount),
                              style: Theme.of(context).textTheme.titleMedium,
                            ),
                            StatusPill(status: fee.status),
                          ],
                        ),
                      ],
                    ),
                  ),
              ],
            ),
    );
  }
}
