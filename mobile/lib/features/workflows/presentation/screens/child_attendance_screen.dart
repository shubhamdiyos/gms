import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../dashboard/data/models/dashboard_models.dart';
import '../../../dashboard/presentation/widgets/dashboard_widgets.dart';
import '../../data/models/workflow_models.dart';
import '../providers/workflow_providers.dart';
import '../widgets/workflow_widgets.dart';

/// A parent's view of one child's attendance, period by period.
class ChildAttendanceScreen extends ConsumerStatefulWidget {
  const ChildAttendanceScreen({super.key});

  @override
  ConsumerState<ChildAttendanceScreen> createState() =>
      _ChildAttendanceScreenState();
}

class _ChildAttendanceScreenState extends ConsumerState<ChildAttendanceScreen> {
  int? _childId;

  @override
  Widget build(BuildContext context) {
    final children = ref.watch(parentChildrenProvider);

    return WorkflowScaffold(
      title: 'Child attendance',
      subtitle: 'Every marked period for one child',
      children: [
        AsyncContent<List<StudentSummary>>(
          value: children,
          errorTitle: 'Could not load your children',
          onRetry: () async {
            ref.invalidate(parentChildrenProvider);
            try {
              await ref.read(parentChildrenProvider.future);
            } catch (_) {
              // Rendered by the error state.
            }
          },
          builder: (context, list) {
            if (list.isEmpty) {
              return const EmptySection(
                message: 'No children are linked to this account.',
                icon: Icons.family_restroom_outlined,
              );
            }
            final childId = _childId ?? list.first.id;
            final records = ref.watch(childAttendanceProvider(childId));

            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                DropdownButtonFormField<int>(
                  initialValue: childId,
                  decoration: const InputDecoration(
                    labelText: 'Child',
                    border: OutlineInputBorder(),
                    isDense: true,
                  ),
                  items: [
                    for (final child in list)
                      DropdownMenuItem(
                        value: child.id,
                        child: Text(child.fullName),
                      ),
                  ],
                  onChanged: (value) => setState(() => _childId = value),
                ),
                const SizedBox(height: 16),
                AsyncContent<List<AttendanceRecord>>(
                  value: records,
                  errorTitle: 'Could not load this attendance',
                  onRetry: () async {
                    ref.invalidate(childAttendanceProvider(childId));
                    try {
                      await ref.read(childAttendanceProvider(childId).future);
                    } catch (_) {
                      // Rendered by the error state.
                    }
                  },
                  builder: (context, data) => _AttendanceBody(records: data),
                ),
              ],
            );
          },
        ),
      ],
    );
  }
}

class _AttendanceBody extends StatelessWidget {
  final List<AttendanceRecord> records;

  const _AttendanceBody({required this.records});

  @override
  Widget build(BuildContext context) {
    if (records.isEmpty) {
      return const SectionCard(
        title: 'Attendance',
        child: EmptySection(
          message: 'No attendance has been recorded for this child yet.',
          icon: Icons.event_busy_outlined,
        ),
      );
    }

    final present = records.where((r) => r.status == 'PRESENT').length;
    final late = records.where((r) => r.status == 'LATE').length;
    final absent = records.where((r) => r.status == 'ABSENT').length;
    final recorded = present + late + absent;
    final rate = recorded == 0 ? null : (present + late) / recorded * 100;
    final sorted = [...records]..sort((a, b) {
      final left = a.date ?? DateTime(1970);
      final right = b.date ?? DateTime(1970);
      final byDate = right.compareTo(left);
      if (byDate != 0) return byDate;
      return (b.period ?? 0).compareTo(a.period ?? 0);
    });

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        MetricGrid(
          children: [
            MetricCard(
              label: 'Attendance rate',
              value: formatPercent(rate),
              icon: Icons.percent_outlined,
              color: rate == null
                  ? AppColors.textSecondary
                  : rate >= 90
                  ? AppColors.success
                  : rate >= 75
                  ? AppColors.warning
                  : AppColors.error,
              hint: '$recorded periods',
            ),
            MetricCard(
              label: 'Present',
              value: present.toString(),
              icon: Icons.check_circle_outline,
              color: AppColors.success,
            ),
            MetricCard(
              label: 'Absent',
              value: absent.toString(),
              icon: Icons.cancel_outlined,
              color: AppColors.error,
            ),
            MetricCard(
              label: 'Late',
              value: late.toString(),
              icon: Icons.schedule_outlined,
              color: AppColors.warning,
            ),
          ],
        ),
        const SizedBox(height: 16),
        SectionCard(
          title: 'Period register',
          trailing: Text(
            '${sorted.length} records',
            style: Theme.of(context).textTheme.labelSmall?.copyWith(
              color: AppColors.textTertiary,
            ),
          ),
          child: Column(
            children: [
              for (final record in sorted)
                Padding(
                  padding: const EdgeInsets.only(bottom: 10),
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
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              record.subjectName ??
                                  (record.period == null
                                      ? 'Period'
                                      : 'Period ${record.period}'),
                              style: Theme.of(context).textTheme.bodyLarge,
                            ),
                            if ((record.remarks ?? '').isNotEmpty)
                              Text(
                                record.remarks!,
                                style: Theme.of(context).textTheme.bodySmall
                                    ?.copyWith(
                                      color: AppColors.textSecondary,
                                    ),
                              ),
                          ],
                        ),
                      ),
                      StatusPill(status: record.status),
                    ],
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }
}
