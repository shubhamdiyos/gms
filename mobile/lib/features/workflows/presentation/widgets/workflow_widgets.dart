import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/api/api_exceptions.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../dashboard/data/models/dashboard_models.dart';

/// Chrome shared by every workflow screen: a back button that falls back to
/// the dashboard, and a width-bounded scroll body.
class WorkflowScaffold extends StatelessWidget {
  final String title;
  final String? subtitle;
  final List<Widget> children;
  final Widget? floatingActionButton;

  const WorkflowScaffold({
    super.key,
    required this.title,
    this.subtitle,
    required this.children,
    this.floatingActionButton,
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title),
            if (subtitle != null)
              Text(
                subtitle!,
                style: Theme.of(context).textTheme.labelSmall?.copyWith(
                  color: AppColors.textSecondary,
                ),
              ),
          ],
        ),
        leading: IconButton(
          tooltip: 'Back',
          icon: const Icon(Icons.arrow_back),
          onPressed: () {
            if (context.canPop()) {
              context.pop();
            } else {
              context.go('/home');
            }
          },
        ),
        actions: [
          IconButton(
            tooltip: 'Dashboard',
            icon: const Icon(Icons.home_outlined),
            onPressed: () => context.go('/home'),
          ),
        ],
      ),
      floatingActionButton: floatingActionButton,
      body: SafeArea(
        child: LayoutBuilder(
          builder: (context, constraints) {
            final horizontal = constraints.maxWidth >= 900 ? 32.0 : 16.0;
            return Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 1120),
                child: ListView(
                  padding: EdgeInsets.fromLTRB(horizontal, 20, horizontal, 32),
                  children: children,
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}

/// Explicit loading, error and data states for a workflow read.
class AsyncContent<T> extends StatelessWidget {
  final AsyncValue<T> value;
  final Future<void> Function() onRetry;
  final String errorTitle;
  final Widget Function(BuildContext context, T data) builder;

  const AsyncContent({
    super.key,
    required this.value,
    required this.onRetry,
    required this.builder,
    this.errorTitle = 'Could not load this page',
  });

  @override
  Widget build(BuildContext context) {
    return value.when(
      loading: () => const Padding(
        padding: EdgeInsets.symmetric(vertical: 64),
        child: Center(child: CircularProgressIndicator()),
      ),
      error: (error, stackTrace) => WorkflowError(
        title: errorTitle,
        message: error is ApiException
            ? error.message
            : 'Something went wrong while loading this page.',
        onRetry: onRetry,
      ),
      data: (data) => builder(context, data),
    );
  }
}

class WorkflowError extends StatelessWidget {
  final String title;
  final String message;
  final Future<void> Function() onRetry;

  const WorkflowError({
    super.key,
    required this.title,
    required this.message,
    required this.onRetry,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(vertical: 24),
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: AppColors.error.withValues(alpha: 0.06),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.error.withValues(alpha: 0.25)),
      ),
      child: Column(
        children: [
          const Icon(Icons.cloud_off_outlined, size: 44, color: AppColors.error),
          const SizedBox(height: 12),
          Text(title, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 6),
          Text(
            message,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: AppColors.textSecondary,
            ),
          ),
          const SizedBox(height: 16),
          FilledButton.icon(
            onPressed: () => onRetry(),
            icon: const Icon(Icons.refresh),
            label: const Text('Try again'),
          ),
        ],
      ),
    );
  }
}

class SearchField extends StatelessWidget {
  final TextEditingController controller;
  final ValueChanged<String> onSubmitted;
  final VoidCallback onClear;
  final String hint;

  const SearchField({
    super.key,
    required this.controller,
    required this.onSubmitted,
    required this.onClear,
    this.hint = 'Search by name or student ID',
  });

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<TextEditingValue>(
      valueListenable: controller,
      builder: (context, value, child) => TextField(
        controller: controller,
        textInputAction: TextInputAction.search,
        onSubmitted: onSubmitted,
        decoration: InputDecoration(
          hintText: hint,
          prefixIcon: const Icon(Icons.search),
          suffixIcon: value.text.isEmpty
              ? null
              : IconButton(
                  tooltip: 'Clear search',
                  icon: const Icon(Icons.close),
                  onPressed: onClear,
                ),
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
          isDense: true,
        ),
      ),
    );
  }
}

/// A tappable row for a student in a list.
class StudentListTile extends StatelessWidget {
  final StudentSummary student;
  final VoidCallback? onTap;

  const StudentListTile({super.key, required this.student, this.onTap});

  @override
  Widget build(BuildContext context) {
    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 4),
      onTap: onTap,
      leading: CircleAvatar(
        backgroundColor: AppColors.primary.withValues(alpha: 0.12),
        child: const Icon(Icons.person_outline, color: AppColors.primary),
      ),
      title: Text(student.fullName),
      subtitle: Text(
        student.studentId.isEmpty
            ? (student.email ?? 'Student')
            : '${student.studentId}${student.email == null ? '' : ' · ${student.email}'}',
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      trailing: onTap == null ? null : const Icon(Icons.chevron_right),
    );
  }
}

/// A labelled value used inside a section card.
class WorkflowInfoRow extends StatelessWidget {
  final String label;
  final String value;

  const WorkflowInfoRow({
    super.key,
    required this.label,
    required this.value,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            flex: 3,
            child: Text(
              label,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: AppColors.textSecondary,
              ),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            flex: 4,
            child: Text(
              value,
              textAlign: TextAlign.right,
              style: Theme.of(context).textTheme.bodyMedium,
            ),
          ),
        ],
      ),
    );
  }
}

/// Pager for a bounded server-side list.
class PageControls extends StatelessWidget {
  final String summary;
  final bool hasPrevious;
  final bool hasNext;
  final VoidCallback onPrevious;
  final VoidCallback onNext;

  const PageControls({
    super.key,
    required this.summary,
    required this.hasPrevious,
    required this.hasNext,
    required this.onPrevious,
    required this.onNext,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Text(
          summary,
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
            color: AppColors.textSecondary,
          ),
        ),
        const Spacer(),
        IconButton(
          tooltip: 'Previous page',
          onPressed: hasPrevious ? onPrevious : null,
          icon: const Icon(Icons.chevron_left),
        ),
        IconButton(
          tooltip: 'Next page',
          onPressed: hasNext ? onNext : null,
          icon: const Icon(Icons.chevron_right),
        ),
      ],
    );
  }
}

/// Two-state toggle used by the fee ledger filter.
class FilterChipRow extends StatelessWidget {
  final bool showOnlyOutstanding;
  final ValueChanged<bool> onChanged;

  const FilterChipRow({
    super.key,
    required this.showOnlyOutstanding,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 8,
      children: [
        ChoiceChip(
          label: const Text('Outstanding'),
          selected: showOnlyOutstanding,
          onSelected: (_) => onChanged(true),
        ),
        ChoiceChip(
          label: const Text('All fees'),
          selected: !showOnlyOutstanding,
          onSelected: (_) => onChanged(false),
        ),
      ],
    );
  }
}
