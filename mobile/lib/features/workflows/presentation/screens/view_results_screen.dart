import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../auth/domain/enums/user_role.dart';
import '../../../auth/presentation/providers/auth_provider.dart';
import '../../../dashboard/data/models/dashboard_models.dart';
import '../../../dashboard/presentation/widgets/dashboard_widgets.dart';
import '../providers/workflow_providers.dart';
import '../widgets/workflow_widgets.dart';

/// Published results for the student, or for the parent's selected child.
class ViewResultsScreen extends ConsumerStatefulWidget {
  const ViewResultsScreen({super.key});

  @override
  ConsumerState<ViewResultsScreen> createState() => _ViewResultsScreenState();
}

class _ViewResultsScreenState extends ConsumerState<ViewResultsScreen> {
  int? _childId;

  @override
  Widget build(BuildContext context) {
    final role = ref.watch(currentRoleProvider);

    return WorkflowScaffold(
      title: 'Results',
      subtitle: role == UserRole.parent
          ? 'Published exam results per child'
          : 'Your published exam results',
      children: switch (role) {
        UserRole.student => [_studentResults()],
        UserRole.parent => [_parentResults()],
        _ => const [
          EmptySection(
            message: 'Results are available to students and their parents.',
            icon: Icons.assessment_outlined,
          ),
        ],
      },
    );
  }

  Widget _studentResults() {
    return AsyncContent<List<ExamResult>>(
      value: ref.watch(myResultsProvider),
      errorTitle: 'Could not load your results',
      onRetry: () async {
        ref.invalidate(myResultsProvider);
        try {
          await ref.read(myResultsProvider.future);
        } catch (_) {
          // Rendered by the error state.
        }
      },
      builder: (context, results) => _ResultsBody(results: results),
    );
  }

  Widget _parentResults() {
    final children = ref.watch(parentChildrenProvider);

    return AsyncContent<List<StudentSummary>>(
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
        final results = ref.watch(childResultsProvider(childId));

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
            AsyncContent<List<ExamResult>>(
              value: results,
              errorTitle: 'Could not load these results',
              onRetry: () async {
                ref.invalidate(childResultsProvider(childId));
                try {
                  await ref.read(childResultsProvider(childId).future);
                } catch (_) {
                  // Rendered by the error state.
                }
              },
              builder: (context, data) => _ResultsBody(results: data),
            ),
          ],
        );
      },
    );
  }
}

class _ResultsBody extends StatelessWidget {
  final List<ExamResult> results;

  const _ResultsBody({required this.results});

  @override
  Widget build(BuildContext context) {
    if (results.isEmpty) {
      return const SectionCard(
        title: 'Published results',
        child: EmptySection(
          message: 'No results have been published yet.',
          icon: Icons.assessment_outlined,
        ),
      );
    }

    final scored = results
        .where((result) => result.percentage != null)
        .toList();
    final average = scored.isEmpty
        ? null
        : scored.fold<double>(0, (sum, result) => sum + result.percentage!) /
              scored.length;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        MetricGrid(
          children: [
            MetricCard(
              label: 'Subjects',
              value: results.length.toString(),
              icon: Icons.menu_book_outlined,
              color: AppColors.roleStudent,
            ),
            MetricCard(
              label: 'Average',
              value: average == null ? '—' : '${average.toStringAsFixed(1)}%',
              icon: Icons.percent_outlined,
              color: AppColors.primary,
            ),
            MetricCard(
              label: 'Highest grade',
              value: _bestGrade(results),
              icon: Icons.emoji_events_outlined,
              color: AppColors.warning,
            ),
          ],
        ),
        const SizedBox(height: 16),
        SectionCard(
          title: 'Published results',
          child: Column(
            children: [
              for (final result in results)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              result.subjectName ?? 'Subject',
                              style: Theme.of(context).textTheme.bodyLarge,
                            ),
                            Text(
                              result.examName ?? 'Exam',
                              style: Theme.of(context).textTheme.bodySmall
                                  ?.copyWith(color: AppColors.textSecondary),
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
                                : '${result.obtainedMarks} marks',
                            style: Theme.of(context).textTheme.bodyLarge,
                          ),
                          Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              if (result.grade != null)
                                StatusPill(status: result.grade!),
                              if (result.percentage != null) ...[
                                const SizedBox(width: 6),
                                Text(
                                  formatPercent(result.percentage),
                                  style: Theme.of(context).textTheme.labelSmall
                                      ?.copyWith(
                                        color: AppColors.textTertiary,
                                      ),
                                ),
                              ],
                            ],
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }

  String _bestGrade(List<ExamResult> results) {
    final scored = results
        .where((result) => result.percentage != null)
        .toList()
      ..sort((a, b) => b.percentage!.compareTo(a.percentage!));
    if (scored.isNotEmpty) {
      final grade = scored.first.grade;
      if (grade != null && grade.isNotEmpty) return grade;
    }
    for (final result in results) {
      if ((result.grade ?? '').isNotEmpty) return result.grade!;
    }
    return '—';
  }
}
