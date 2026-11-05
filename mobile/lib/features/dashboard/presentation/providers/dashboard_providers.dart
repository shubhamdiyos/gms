import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../auth/presentation/providers/auth_provider.dart';
import '../../data/models/dashboard_models.dart';
import '../../data/repositories/dashboard_repository.dart';

/// Repository provider shared by every role dashboard.
final dashboardRepositoryProvider = Provider<DashboardRepository>((ref) {
  return DashboardRepository(apiClient: ref.watch(apiClientProvider));
});

/// `GET /dashboard/school` for administrators.
final adminDashboardProvider =
    FutureProvider.autoDispose<SchoolDashboard>((ref) {
      return ref.watch(dashboardRepositoryProvider).loadAdminDashboard();
    });

/// `GET /financial-dashboard` for accountants.
final accountantDashboardProvider =
    FutureProvider.autoDispose<FinancialSummary>((ref) {
      return ref.watch(dashboardRepositoryProvider).loadAccountantDashboard();
    });

/// Teacher portal: profile, classes, students and timetable.
final teacherDashboardProvider =
    FutureProvider.autoDispose<TeacherDashboardData>((ref) {
      return ref.watch(dashboardRepositoryProvider).loadTeacherDashboard();
    });

/// Student portal: profile, attendance, results, fees and timetable.
final studentDashboardProvider =
    FutureProvider.autoDispose<StudentDashboardData>((ref) {
      return ref.watch(dashboardRepositoryProvider).loadStudentDashboard();
    });

/// Parent portal: profile plus each child's attendance, results and fees.
final parentDashboardProvider =
    FutureProvider.autoDispose<ParentDashboardData>((ref) {
      return ref.watch(dashboardRepositoryProvider).loadParentDashboard();
    });
