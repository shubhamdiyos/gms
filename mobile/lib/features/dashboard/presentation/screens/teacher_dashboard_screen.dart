import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../auth/presentation/providers/auth_provider.dart';
import '../../data/models/dashboard_models.dart';
import '../../../workflows/presentation/widgets/workflow_links.dart';
import '../providers/dashboard_providers.dart';
import '../widgets/dashboard_widgets.dart';

/// Teacher dashboard backed by the teacher portal endpoints.
class TeacherDashboardScreen extends ConsumerWidget {
  const TeacherDashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(currentUserProvider);
    final dashboard = ref.watch(teacherDashboardProvider);

    return DashboardScaffold(
      title: 'Teacher',
      onRefresh: () => _refresh(ref),
      children: [
        DashboardHeader(
          eyebrow: 'Teaching workspace',
          title: user?.fullName.isNotEmpty == true
              ? user!.fullName
              : 'Teacher',
          subtitle: 'Your classes, students and timetable',
          icon: Icons.school_outlined,
          color: AppColors.roleTeacher,
        ),
        const SizedBox(height: 16),
        const WorkflowLinks(
          links: [
            WorkflowLink(
              title: 'Mark attendance',
              subtitle: 'Take the register for one of your periods',
              icon: Icons.fact_check_outlined,
              route: '/attendance/mark',
              color: AppColors.roleTeacher,
            ),
          ],
        ),
        const SizedBox(height: 20),
        AsyncDashboard<TeacherDashboardData>(
          value: dashboard,
          onRetry: () => _refresh(ref),
          builder: (context, data) => Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              MetricGrid(
                children: [
                  MetricCard(
                    label: 'Assigned classes',
                    value: data.classes.length.toString(),
                    icon: Icons.meeting_room_outlined,
                    color: AppColors.roleTeacher,
                  ),
                  MetricCard(
                    label: 'Students',
                    value: data.students.length.toString(),
                    icon: Icons.groups_outlined,
                    color: AppColors.roleStudent,
                  ),
                  MetricCard(
                    label: "Today's periods",
                    value: data.todaySlots.length.toString(),
                    icon: Icons.today_outlined,
                    color: AppColors.primary,
                  ),
                  MetricCard(
                    label: 'Weekly periods',
                    value: data.weeklyPeriods.toString(),
                    icon: Icons.calendar_view_week_outlined,
                    color: AppColors.roleAdmin,
                  ),
                ],
              ),
              const SizedBox(height: 20),
              SectionCard(
                title: "Today's schedule",
                child: ScheduleList(
                  slots: data.todaySlots,
                  emptyMessage: 'No periods are scheduled for today.',
                ),
              ),
              const SizedBox(height: 16),
              SectionCard(
                title: 'Assigned classes',
                child: data.classes.isEmpty
                    ? const EmptySection(
                        message: 'No classes have been assigned yet.',
                        icon: Icons.meeting_room_outlined,
                      )
                    : Column(
                        children: [
                          for (final classroom in data.classes)
                            PersonRow(
                              name: classroom.name,
                              subtitle: [
                                if ((classroom.grade ?? '').isNotEmpty)
                                  'Grade ${classroom.grade}',
                                if ((classroom.section ?? '').isNotEmpty)
                                  'Section ${classroom.section}',
                              ].join(' · '),
                              icon: Icons.meeting_room_outlined,
                            ),
                        ],
                      ),
              ),
              const SizedBox(height: 16),
              SectionCard(
                title: 'Students',
                trailing: Text(
                  '${data.students.length} total',
                  style: Theme.of(context).textTheme.labelSmall?.copyWith(
                    color: AppColors.textTertiary,
                  ),
                ),
                child: data.students.isEmpty
                    ? const EmptySection(
                        message: 'No students are assigned to your classes yet.',
                        icon: Icons.groups_outlined,
                      )
                    : Column(
                        children: [
                          for (final student in data.students.take(8))
                            PersonRow(
                              name: student.fullName,
                              subtitle: student.studentId.isEmpty
                                  ? (student.email ?? 'Student')
                                  : 'ID ${student.studentId}',
                              icon: Icons.person_outline,
                            ),
                          if (data.students.length > 8)
                            Padding(
                              padding: const EdgeInsets.only(top: 4),
                              child: Align(
                                alignment: Alignment.centerLeft,
                                child: Text(
                                  'Showing 8 of ${data.students.length} students',
                                  style: Theme.of(context).textTheme.bodySmall
                                      ?.copyWith(
                                        color: AppColors.textTertiary,
                                      ),
                                ),
                              ),
                            ),
                        ],
                      ),
              ),
              const SizedBox(height: 16),
              SectionCard(
                title: 'Profile',
                child: Column(
                  children: [
                    InfoRow(
                      label: 'Name',
                      value: data.profile.fullName,
                    ),
                    InfoRow(
                      label: 'Designation',
                      value: data.profile.designation ?? '—',
                    ),
                    InfoRow(
                      label: 'Department',
                      value: data.profile.department ?? '—',
                    ),
                    InfoRow(
                      label: 'Email',
                      value: data.profile.email ?? '—',
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

  Future<void> _refresh(WidgetRef ref) async {
    ref.invalidate(teacherDashboardProvider);
    try {
      await ref.read(teacherDashboardProvider.future);
    } catch (_) {
      // The AsyncDashboard error state renders the failure.
    }
  }
}
