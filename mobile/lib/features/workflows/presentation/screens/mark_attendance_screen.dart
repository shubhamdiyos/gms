import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/api/api_exceptions.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../auth/domain/enums/user_role.dart';
import '../../../auth/presentation/providers/auth_provider.dart';
import '../../../dashboard/data/models/dashboard_models.dart';
import '../../../dashboard/presentation/widgets/dashboard_widgets.dart';
import '../../data/models/workflow_models.dart';
import '../providers/workflow_providers.dart';
import '../widgets/workflow_widgets.dart';

/// Roll call for one period.
///
/// The bulk endpoint inserts and ignores rows that already exist, so a period
/// that was already marked is shown read-only rather than offering a save the
/// backend would silently discard.
class MarkAttendanceScreen extends ConsumerStatefulWidget {
  const MarkAttendanceScreen({super.key});

  @override
  ConsumerState<MarkAttendanceScreen> createState() =>
      _MarkAttendanceScreenState();
}

class _MarkAttendanceScreenState extends ConsumerState<MarkAttendanceScreen> {
  static const _statuses = ['PRESENT', 'ABSENT', 'LATE'];

  int? _classroomId;
  int? _slotId;
  DateTime _date = startOfDay(DateTime.now());
  final Map<int, String> _drafts = {};
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    ref.listenManual<AsyncValue<List<ClassroomInfo>>>(
      classroomOptionsProvider,
      (previous, next) {
        next.whenData((classrooms) {
          if (_classroomId == null && classrooms.isNotEmpty && mounted) {
            setState(() => _classroomId = classrooms.first.id);
          }
        });
      },
      fireImmediately: true,
    );
  }

  void _selectClassroom(int? id) {
    if (id == null || id == _classroomId) return;
    setState(() {
      _classroomId = id;
      _slotId = null;
      _drafts.clear();
    });
  }

  void _selectSlot(int? id) {
    if (id == null || id == _slotId) return;
    setState(() {
      _slotId = id;
      _drafts.clear();
    });
  }

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _date,
      firstDate: DateTime(2020),
      lastDate: startOfDay(DateTime.now()),
    );
    if (picked == null) return;
    setState(() {
      _date = startOfDay(picked);
      _drafts.clear();
    });
  }

  String _statusFor(
    StudentSummary student,
    RollCallData rollCall,
    int? slotId,
  ) {
    return _drafts[student.id] ??
        rollCall.existingFor(slotId, student.id)?.status ??
        'PRESENT';
  }

  Future<void> _submit(
    int classroomId,
    int slotId,
    RollCallData rollCall,
  ) async {
    final marks = rollCall.students
        .where((student) => rollCall.existingFor(slotId, student.id) == null)
        .map(
          (student) => AttendanceMark(
            student: student,
            status: _statusFor(student, rollCall, slotId),
          ),
        )
        .toList();
    if (marks.isEmpty || _saving) return;

    setState(() => _saving = true);
    try {
      await ref
          .read(workflowRepositoryProvider)
          .recordBulkAttendance(
            timetableSlotId: slotId,
            date: _date,
            marks: marks,
          );
      if (!mounted) return;
      ref.invalidate(
        rollCallProvider(
          RollCallQuery(
            classroomId: classroomId,
            date: _date,
            ownStudentsOnly:
                ref.read(currentRoleProvider) == UserRole.teacher,
          ),
        ),
      );
      setState(() {
        _drafts.clear();
        _saving = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Attendance saved for ${marks.length} '
            '${marks.length == 1 ? 'student' : 'students'}.',
          ),
        ),
      );
    } catch (error) {
      if (!mounted) return;
      setState(() => _saving = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            error is ApiException
                ? error.message
                : 'Attendance could not be saved.',
          ),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final role = ref.watch(currentRoleProvider);
    final ownStudentsOnly = role == UserRole.teacher;
    final classrooms = ref.watch(classroomOptionsProvider);
    final classroomId = _classroomId;
    final timetable = classroomId == null
        ? null
        : ref.watch(classroomTimetableProvider(classroomId));
    final slotId = _effectiveSlot(timetable?.valueOrNull);
    final rollCall = classroomId == null
        ? null
        : ref.watch(
            rollCallProvider(
              RollCallQuery(
                classroomId: classroomId,
                date: _date,
                ownStudentsOnly: ownStudentsOnly,
              ),
            ),
          );

    return WorkflowScaffold(
      title: 'Mark attendance',
      subtitle: 'One period, one register',
      children: [
        _selectionCard(classrooms, timetable, slotId),
        const SizedBox(height: 16),
        if (classroomId == null)
          const EmptySection(
            message: 'Loading classrooms…',
            icon: Icons.meeting_room_outlined,
          )
        else if (slotId == null)
          const EmptySection(
            message: 'Choose a period to start the register.',
            icon: Icons.event_available_outlined,
          )
        else if (rollCall != null)
          AsyncContent<RollCallData>(
            value: rollCall,
            errorTitle: 'Could not load the register',
            onRetry: () async {
              ref.invalidate(
                rollCallProvider(
                  RollCallQuery(
                    classroomId: classroomId,
                    date: _date,
                    ownStudentsOnly: ownStudentsOnly,
                  ),
                ),
              );
              try {
                await ref.read(
                  rollCallProvider(
                    RollCallQuery(
                      classroomId: classroomId,
                      date: _date,
                      ownStudentsOnly: ownStudentsOnly,
                    ),
                  ).future,
                );
              } catch (_) {
                // Rendered by the error state.
              }
            },
            builder: (context, data) => _register(
              classroomId,
              slotId,
              data,
            ),
          ),
      ],
    );
  }

  /// Only slots that carry an id can be marked; a schedule row without one
  /// has nothing for the attendance write to reference.
  List<TimetableSlot> _markableSlots(TimetableWeek? week) {
    if (week == null) return const [];
    return week.slots.where((slot) => slot.id != null).toList();
  }

  int? _effectiveSlot(TimetableWeek? week) {
    final slots = _markableSlots(week);
    if (slots.isEmpty) return null;
    final known = slots.map((slot) => slot.id).toSet();
    if (_slotId != null && known.contains(_slotId)) return _slotId;
    return slots.first.id;
  }

  String _shortDay(String day) =>
      day.length <= 3 ? day : day.substring(0, 3);

  Widget _selectionCard(
    AsyncValue<List<ClassroomInfo>> classrooms,
    AsyncValue<TimetableWeek>? timetable,
    int? slotId,
  ) {
    return SectionCard(
      title: 'Period',
      trailing: OutlinedButton.icon(
        onPressed: _pickDate,
        icon: const Icon(Icons.event_outlined, size: 18),
        label: Text(formatDate(_date)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          classrooms.when(
            loading: () => const LinearProgressIndicator(),
            error: (error, stackTrace) => Text(
              error is ApiException
                  ? error.message
                  : 'Could not load classrooms.',
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: AppColors.error,
              ),
            ),
            data: (items) => DropdownButtonFormField<int>(
              initialValue: _classroomId,
              decoration: const InputDecoration(
                labelText: 'Classroom',
                border: OutlineInputBorder(),
                isDense: true,
              ),
              items: [
                for (final classroom in items)
                  DropdownMenuItem(
                    value: classroom.id,
                    child: Text(classroom.name),
                  ),
              ],
              onChanged: _selectClassroom,
            ),
          ),
          const SizedBox(height: 12),
          if (timetable == null)
            const Text('Choose a classroom to list its periods.')
          else
            timetable.when(
              loading: () => const LinearProgressIndicator(),
              error: (error, stackTrace) => Text(
                error is ApiException
                    ? error.message
                    : 'Could not load the timetable.',
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: AppColors.error,
                ),
              ),
              data: (week) {
                final slots = _markableSlots(week);
                return slots.isEmpty
                    ? const EmptySection(
                        message: 'This classroom has no timetable yet.',
                        icon: Icons.event_busy_outlined,
                      )
                    : Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          for (final slot in slots)
                            ChoiceChip(
                              selected: slot.id == slotId,
                              onSelected: (_) => _selectSlot(slot.id),
                              label: Text(
                                '${_shortDay(slot.dayOfWeek)} '
                                'P${slot.period} · ${slot.subjectName}',
                              ),
                            ),
                        ],
                      );
              },
            ),
        ],
      ),
    );
  }

  Widget _register(
    int classroomId,
    int slotId,
    RollCallData data,
  ) {
    if (data.students.isEmpty) {
      return const EmptySection(
        message: 'No students are assigned to this classroom.',
        icon: Icons.groups_outlined,
      );
    }

    final pending = data.students
        .where((student) => data.existingFor(slotId, student.id) == null)
        .toList();
    final recorded = data.students.length - pending.length;

    return SectionCard(
      title: 'Register',
      trailing: Text(
        '${data.students.length} students',
        style: Theme.of(context).textTheme.labelSmall?.copyWith(
          color: AppColors.textTertiary,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (recorded > 0)
            Container(
              margin: const EdgeInsets.only(bottom: 12),
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppColors.info.withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Text(
                '$recorded ${recorded == 1 ? 'student is' : 'students are'} '
                'already marked for this period and shown read-only.',
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ),
          for (final student in data.students)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          student.fullName,
                          style: Theme.of(context).textTheme.bodyLarge,
                        ),
                        if (student.studentId.isNotEmpty)
                          Text(
                            student.studentId,
                            style: Theme.of(context).textTheme.bodySmall
                                ?.copyWith(color: AppColors.textTertiary),
                          ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 8),
                  if (data.existingFor(slotId, student.id) != null)
                    StatusPill(
                      status: data.existingFor(slotId, student.id)!.status,
                    )
                  else
                    DropdownButton<String>(
                      value: _statusFor(student, data, slotId),
                      underline: const SizedBox.shrink(),
                      items: [
                        for (final status in _statuses)
                          DropdownMenuItem(
                            value: status,
                            child: Text(status),
                          ),
                      ],
                      onChanged: (value) {
                        if (value == null) return;
                        setState(() => _drafts[student.id] = value);
                      },
                    ),
                ],
              ),
            ),
          const SizedBox(height: 8),
          FilledButton.icon(
            onPressed: pending.isEmpty || _saving
                ? null
                : () => _submit(classroomId, slotId, data),
            icon: _saving
                ? const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.save_outlined),
            label: Text(
              pending.isEmpty
                  ? 'This period is fully marked'
                  : 'Save attendance (${pending.length})',
            ),
          ),
        ],
      ),
    );
  }
}
