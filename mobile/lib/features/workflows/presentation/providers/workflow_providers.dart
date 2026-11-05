import 'package:equatable/equatable.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../auth/domain/enums/user_role.dart';
import '../../../auth/presentation/providers/auth_provider.dart';
import '../../../dashboard/data/models/dashboard_models.dart';
import '../../data/models/workflow_models.dart';
import '../../data/repositories/workflow_repository.dart';

final workflowRepositoryProvider = Provider<WorkflowRepository>((ref) {
  return WorkflowRepository(apiClient: ref.watch(apiClientProvider));
});

/// `GET /students/search`, keyed by the text and page the admin picked.
final studentSearchProvider =
    FutureProvider.autoDispose.family<StudentPage, StudentQuery>((ref, query) {
      return ref
          .watch(workflowRepositoryProvider)
          .searchStudents(
            query: query.query,
            classId: query.classId,
            page: query.page,
            size: query.size,
          );
    });

/// Profile, attendance, results and fees for one student.
final studentDetailsProvider =
    FutureProvider.autoDispose.family<StudentDetailsBundle, int>((ref, id) {
      return ref.watch(workflowRepositoryProvider).loadStudentDetails(id);
    });

/// `GET /student-fees` — the accountant's collection ledger.
final feeLedgerProvider = FutureProvider.autoDispose<List<FeeRecord>>((ref) {
  return ref.watch(workflowRepositoryProvider).loadAllStudentFees();
});

/// `GET /classrooms` — the roll-call classroom picker for administrators.
final classroomListProvider =
    FutureProvider.autoDispose<List<ClassroomInfo>>((ref) {
      return ref.watch(workflowRepositoryProvider).loadClassrooms();
    });

/// What the roll-call picker should offer the signed-in role: a teacher sees
/// only the classrooms they are assigned to, everyone else sees the school.
final classroomOptionsProvider =
    FutureProvider.autoDispose<List<ClassroomInfo>>((ref) {
      final role = ref.watch(currentRoleProvider);
      final repository = ref.watch(workflowRepositoryProvider);
      if (role == UserRole.teacher) {
        return repository.loadAssignedClassrooms();
      }
      return repository.loadClassrooms();
    });

/// `GET /timetables/classroom/{id}` — the periods available to mark.
final classroomTimetableProvider =
    FutureProvider.autoDispose.family<TimetableWeek, int>((ref, classroomId) {
      return ref
          .watch(workflowRepositoryProvider)
          .loadTimetableForClassroom(classroomId);
    });

/// The teacher's own pupils, used to fill a roll call.
final teacherStudentsProvider =
    FutureProvider.autoDispose<List<StudentSummary>>((ref) {
      return ref.watch(workflowRepositoryProvider).loadTeacherStudents();
    });

/// Who is in one classroom's roll call and who was already marked.
class RollCallQuery extends Equatable {
  final int classroomId;
  final DateTime date;
  final bool ownStudentsOnly;

  const RollCallQuery({
    required this.classroomId,
    required this.date,
    required this.ownStudentsOnly,
  });

  @override
  List<Object?> get props => [
    classroomId,
    date.year,
    date.month,
    date.day,
    ownStudentsOnly,
  ];
}

final rollCallProvider =
    FutureProvider.autoDispose.family<RollCallData, RollCallQuery>((
      ref,
      query,
    ) async {
      final repository = ref.watch(workflowRepositoryProvider);
      final results = await Future.wait<dynamic>([
        query.ownStudentsOnly
            ? repository.loadTeacherStudents()
            : repository.searchStudents(classId: query.classroomId, size: 200),
        repository.loadClassroomAttendance(
          query.classroomId,
          date: query.date,
        ),
      ]);

      final List<StudentSummary> students;
      if (query.ownStudentsOnly) {
        students = (results[0] as List<StudentSummary>)
            .where((student) => student.classId == query.classroomId)
            .toList();
      } else {
        students = (results[0] as StudentPage).students;
      }

      return RollCallData(
        students: students,
        existing: results[1] as List<AttendanceRecord>,
      );
    });

class ClassroomAttendanceQuery extends Equatable {
  final int classroomId;
  final DateTime date;

  const ClassroomAttendanceQuery({
    required this.classroomId,
    required this.date,
  });

  @override
  List<Object?> get props => [classroomId, date.year, date.month, date.day];
}

/// Attendance already recorded for a classroom on a day, used to prefill.
final classroomAttendanceProvider =
    FutureProvider.autoDispose
        .family<List<AttendanceRecord>, ClassroomAttendanceQuery>((ref, query) {
          return ref
              .watch(workflowRepositoryProvider)
              .loadClassroomAttendance(query.classroomId, date: query.date);
        });

/// `GET /students/my-results` for the student portal.
final myResultsProvider = FutureProvider.autoDispose<List<ExamResult>>((ref) {
  return ref.watch(workflowRepositoryProvider).loadMyResults();
});

/// The parent's children, for the attendance and results pickers.
final parentChildrenProvider =
    FutureProvider.autoDispose<List<StudentSummary>>((ref) {
      return ref.watch(workflowRepositoryProvider).loadChildren();
    });

/// `GET /parents/children/{id}/attendance`.
final childAttendanceProvider =
    FutureProvider.autoDispose.family<List<AttendanceRecord>, int>((ref, id) {
      return ref.watch(workflowRepositoryProvider).loadChildAttendance(id);
    });

/// `GET /parents/children/{id}/results`.
final childResultsProvider =
    FutureProvider.autoDispose.family<List<ExamResult>, int>((ref, id) {
      return ref.watch(workflowRepositoryProvider).loadChildResults(id);
    });
