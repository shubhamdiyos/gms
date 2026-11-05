import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../dashboard/presentation/widgets/dashboard_widgets.dart';
import '../../data/models/workflow_models.dart';
import '../providers/workflow_providers.dart';
import '../widgets/workflow_widgets.dart';

/// Admin student directory backed by `GET /students/search`.
///
/// The search runs on the server so a tenant with 50,000 pupils still answers
/// in one page, and the pager exposes the whole roll without loading it.
class StudentListScreen extends ConsumerStatefulWidget {
  const StudentListScreen({super.key});

  @override
  ConsumerState<StudentListScreen> createState() => _StudentListScreenState();
}

class _StudentListScreenState extends ConsumerState<StudentListScreen> {
  final TextEditingController _controller = TextEditingController();
  StudentQuery _query = const StudentQuery();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _search(String text) {
    setState(() => _query = _query.copyWith(query: text, page: 0));
  }

  void _clear() {
    _controller.clear();
    _search('');
  }

  void _goToPage(int page) {
    setState(() => _query = _query.copyWith(page: page));
  }

  Future<void> _reload() async {
    ref.invalidate(studentSearchProvider(_query));
    try {
      await ref.read(studentSearchProvider(_query).future);
    } catch (_) {
      // The AsyncContent error state renders the failure.
    }
  }

  @override
  Widget build(BuildContext context) {
    final page = ref.watch(studentSearchProvider(_query));

    return WorkflowScaffold(
      title: 'Students',
      subtitle: 'Search the roll and open a student record',
      children: [
        SearchField(
          controller: _controller,
          onSubmitted: _search,
          onClear: _clear,
        ),
        const SizedBox(height: 16),
        AsyncContent<StudentPage>(
          value: page,
          onRetry: _reload,
          errorTitle: 'Could not load the student roll',
          builder: (context, data) => SectionCard(
            title: 'Results',
            trailing: Text(
              '${data.totalElements} found',
              style: Theme.of(context).textTheme.labelSmall?.copyWith(
                color: AppColors.textTertiary,
              ),
            ),
            child: data.students.isEmpty
                ? const EmptySection(
                    message: 'No students match this search.',
                    icon: Icons.search_off_outlined,
                  )
                : Column(
                    children: [
                      for (final student in data.students)
                        StudentListTile(
                          student: student,
                          onTap: () =>
                              context.push('/students/${student.id}'),
                        ),
                      if (data.totalPages > 1) ...[
                        const Divider(height: 24),
                        PageControls(
                          summary:
                              'Page ${data.page + 1} of ${data.totalPages}',
                          hasPrevious: data.hasPrevious,
                          hasNext: data.hasNext,
                          onPrevious: () => _goToPage(data.page - 1),
                          onNext: () => _goToPage(data.page + 1),
                        ),
                      ],
                    ],
                  ),
          ),
        ),
      ],
    );
  }
}
