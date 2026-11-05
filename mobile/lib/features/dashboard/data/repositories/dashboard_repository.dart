import 'package:dio/dio.dart';
import '../../../../core/api/api_client.dart';
import '../../../../core/api/api_exceptions.dart';
import '../../../../core/constants/api_constants.dart';
import '../models/dashboard_models.dart';

/// Reads the role dashboards from the real backend.
///
/// Each method performs only the calls its dashboard needs and fails fast:
/// a dashboard that cannot load one of its sections reports the error and
/// offers a retry instead of rendering a partial page as if it were complete.
class DashboardRepository {
  final ApiClient _apiClient;

  DashboardRepository({required ApiClient apiClient}) : _apiClient = apiClient;

  Future<SchoolDashboard> loadAdminDashboard() async {
    final data = await _get(ApiConstants.schoolDashboard);
    return SchoolDashboard.fromJson(asMap(data));
  }

  Future<FinancialSummary> loadAccountantDashboard() async {
    final data = await _get(ApiConstants.financialDashboard);
    return FinancialSummary.fromJson(asMap(data));
  }

  Future<TeacherDashboardData> loadTeacherDashboard() async {
    final results = await Future.wait<dynamic>([
      _get(ApiConstants.teacherProfile),
      _get(ApiConstants.teacherClasses),
      _get(ApiConstants.teacherStudents),
      _getOrNull(ApiConstants.teacherTimetable),
    ]);

    return TeacherDashboardData(
      profile: EmployeeProfile.fromJson(asMap(results[0])),
      classes: asMapList(results[1]).map(ClassroomInfo.fromJson).toList(),
      students: asMapList(results[2]).map(StudentSummary.fromJson).toList(),
      timetable: parseTimetableSlots(results[3]),
    );
  }

  Future<StudentDashboardData> loadStudentDashboard() async {
    final results = await Future.wait<dynamic>([
      _get(ApiConstants.studentProfile),
      _getOrNull(ApiConstants.studentClass),
      _get(ApiConstants.studentAttendance),
      _get(ApiConstants.studentResults),
      _get(ApiConstants.studentFees),
      _getOrNull(ApiConstants.studentTimetable),
    ]);

    final classroom = results[1] == null
        ? null
        : ClassroomInfo.fromJson(asMap(results[1]));

    return StudentDashboardData(
      profile: StudentSummary.fromJson(asMap(results[0])),
      classroom: classroom,
      attendance: AttendanceSummary.fromJson(results[2]),
      results: asMapList(results[3]).map(ExamResult.fromJson).toList(),
      fees: asMapList(results[4]).map(FeeRecord.fromJson).toList(),
      timetable: parseTimetableSlots(results[5]),
    );
  }

  Future<ParentDashboardData> loadParentDashboard() async {
    final profileJson = asMap(await _get(ApiConstants.parentProfile));
    final profile = ParentProfile.fromJson(profileJson);

    final children = profile.children.isNotEmpty
        ? profile.children
        : asMapList(await _get(ApiConstants.parentChildren))
              .map(StudentSummary.fromJson)
              .toList();

    final childData = await Future.wait(
      children.map(_loadChildDashboard),
    );

    return ParentDashboardData(profile: profile, children: childData);
  }

  Future<ChildDashboardData> _loadChildDashboard(
    StudentSummary child,
  ) async {
    final attendancePath = ApiConstants.parentChildAttendance.replaceFirst(
      '{studentId}',
      child.id.toString(),
    );
    final resultsPath = ApiConstants.parentChildResults.replaceFirst(
      '{studentId}',
      child.id.toString(),
    );
    final feesPath = ApiConstants.parentChildFees.replaceFirst(
      '{studentId}',
      child.id.toString(),
    );

    final results = await Future.wait<dynamic>([
      _get(attendancePath),
      _get(resultsPath),
      _get(feesPath),
    ]);

    return ChildDashboardData(
      child: child,
      attendance: AttendanceSummary.fromJson(results[0]),
      results: asMapList(results[1]).map(ExamResult.fromJson).toList(),
      fees: asMapList(results[2]).map(FeeRecord.fromJson).toList(),
    );
  }

  Future<dynamic> _get(String path) async {
    final response = await _send(path);
    _ensureSuccess(response, path);
    return response.data;
  }

  /// A missing optional section (for example a class with no timetable yet)
  /// is an empty section, not a failed dashboard.
  Future<dynamic> _getOrNull(String path) async {
    final response = await _send(path);
    if (response.statusCode == 404) return null;
    _ensureSuccess(response, path);
    return response.data;
  }

  Future<Response<dynamic>> _send(String path) async {
    try {
      return await _apiClient.get<dynamic>(
        path,
        options: Options(
          headers: {ApiConstants.headerAccept: ApiConstants.contentTypeJson},
        ),
      );
    } on DioException catch (error) {
      throw _mapDioError(error);
    }
  }

  void _ensureSuccess(Response<dynamic> response, String path) {
    final status = response.statusCode ?? 0;
    if (status >= 200 && status < 300) return;

    final message = _errorMessage(response.data) ?? 'Request to $path failed';
    switch (status) {
      case 401:
        throw UnauthorizedException(
          message: message,
          data: response.data,
        );
      case 403:
        throw ForbiddenException(message: message, data: response.data);
      case 404:
        throw NotFoundException(message: message, data: response.data);
      default:
        throw ApiException(
          message: message,
          statusCode: status,
          data: response.data,
        );
    }
  }

  String? _errorMessage(dynamic data) {
    if (data is Map) {
      final message = data['message'] ?? data['error'];
      if (message != null) return message.toString();
    }
    return null;
  }

  ApiException _mapDioError(DioException error) {
    final status = error.response?.statusCode;
    final message = _errorMessage(error.response?.data);
    switch (status) {
      case 401:
        return UnauthorizedException(
          message: message ?? 'Your session has expired',
          data: error.response?.data,
        );
      case 403:
        return ForbiddenException(
          message: message ?? 'You do not have access to this dashboard',
          data: error.response?.data,
        );
      case 404:
        return NotFoundException(
          message: message ?? 'Resource not found',
          data: error.response?.data,
        );
    }
    if (status != null && status >= 500) {
      return ServerException(
        message: message ?? 'The server could not complete the request',
        statusCode: status,
        data: error.response?.data,
      );
    }
    switch (error.type) {
      case DioExceptionType.connectionTimeout:
      case DioExceptionType.sendTimeout:
      case DioExceptionType.receiveTimeout:
      case DioExceptionType.connectionError:
        return const NetworkException(
          message: 'Unable to reach the server. Check your connection.',
        );
      default:
        return ApiException(
          message: message ?? error.message ?? 'Request failed',
          data: error,
        );
    }
  }
}
