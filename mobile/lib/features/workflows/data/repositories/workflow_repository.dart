import 'package:dio/dio.dart';
import '../../../../core/api/api_client.dart';
import '../../../../core/api/api_exceptions.dart';
import '../../../../core/constants/api_constants.dart';
import '../../../dashboard/data/models/dashboard_models.dart';
import '../models/workflow_models.dart';

/// Reads and writes the M0 workflow screens against the real backend.
///
/// Nothing is synthesized: a screen that cannot reach its endpoint reports
/// the failure instead of rendering a demo answer. Write methods fetch the
/// CSRF token first when the interceptor has none, because the token is not
/// persisted across a restored session and the interceptor fails closed.
class WorkflowRepository {
  final ApiClient _apiClient;

  WorkflowRepository({required ApiClient apiClient}) : _apiClient = apiClient;

  Future<StudentPage> searchStudents({
    String query = '',
    int? classId,
    int page = 0,
    int size = 20,
  }) async {
    final data = await _get(
      ApiConstants.studentSearch,
      queryParameters: {
        if (query.trim().isNotEmpty) 'query': query.trim(),
        'classId': ?classId,
        'page': page,
        'size': size,
      },
    );
    return StudentPage.fromJson(asMap(data));
  }

  Future<StudentProfile> loadStudent(int id) async {
    final data = await _get(
      ApiConstants.studentById.replaceFirst('{id}', id.toString()),
    );
    return StudentProfile.fromJson(asMap(data));
  }

  Future<List<ClassroomInfo>> loadClassrooms() async {
    final data = await _get(ApiConstants.classrooms);
    return asMapList(data).map(ClassroomInfo.fromJson).toList();
  }

  Future<List<ClassroomInfo>> loadAssignedClassrooms() async {
    final data = await _get(ApiConstants.assignedClassrooms);
    return asMapList(data).map(ClassroomInfo.fromJson).toList();
  }

  Future<TimetableWeek> loadTimetableForClassroom(int classroomId) async {
    final data = await _get(
      ApiConstants.classroomTimetable.replaceFirst(
        '{classroomId}',
        classroomId.toString(),
      ),
    );
    return TimetableWeek.fromJson(asMap(data));
  }

  Future<List<StudentSummary>> loadTeacherStudents() async {
    final data = await _get(ApiConstants.teacherStudents);
    return asMapList(data).map(StudentSummary.fromJson).toList();
  }

  Future<List<AttendanceRecord>> loadClassroomAttendance(
    int classroomId, {
    required DateTime date,
  }) async {
    final data = await _get(
      ApiConstants.attendanceForClassroom.replaceFirst(
        '{classroomId}',
        classroomId.toString(),
      ),
      queryParameters: {'date': formatIsoDate(date)},
    );
    return asMapList(data).map(AttendanceRecord.fromJson).toList();
  }

  Future<List<AttendanceRecord>> loadStudentAttendance(
    int studentId, {
    DateTime? from,
    DateTime? to,
  }) async {
    final end = startOfDay(to ?? DateTime.now());
    final start = startOfDay(from ?? end.subtract(const Duration(days: 30)));
    final data = await _get(
      ApiConstants.attendanceForStudent.replaceFirst(
        '{studentId}',
        studentId.toString(),
      ),
      queryParameters: {
        'from': formatIsoDate(start),
        'to': formatIsoDate(end),
      },
    );
    return asMapList(data).map(AttendanceRecord.fromJson).toList();
  }

  Future<void> recordBulkAttendance({
    required int timetableSlotId,
    required DateTime date,
    required List<AttendanceMark> marks,
  }) async {
    await _post(ApiConstants.attendanceBulk, {
      'timetableSlotId': timetableSlotId,
      'date': formatIsoDate(date),
      'attendance': marks.map((mark) => mark.toJson()).toList(),
    });
  }

  Future<List<FeeRecord>> loadAllStudentFees() async {
    final data = await _get(ApiConstants.studentFeesBase);
    return asMapList(data).map(FeeRecord.fromJson).toList();
  }

  Future<List<FeeRecord>> loadStudentFees(int studentId) async {
    final data = await _get(
      ApiConstants.studentFeesForStudent.replaceFirst(
        '{studentId}',
        studentId.toString(),
      ),
    );
    return asMapList(data).map(FeeRecord.fromJson).toList();
  }

  Future<List<FeeRecord>> loadMyFees() async {
    final data = await _get(ApiConstants.studentFees);
    return asMapList(data).map(FeeRecord.fromJson).toList();
  }

  Future<PaymentReceipt> recordPayment(FeePaymentInput input) async {
    final data = await _post(ApiConstants.feePayments, input.toJson());
    return PaymentReceipt.fromJson(asMap(data));
  }

  Future<List<ExamResult>> loadStudentResults(int studentId) async {
    final data = await _get(
      ApiConstants.resultsForStudent.replaceFirst(
        '{studentId}',
        studentId.toString(),
      ),
    );
    return asMapList(data).map(ExamResult.fromJson).toList();
  }

  Future<List<ExamResult>> loadMyResults() async {
    final data = await _get(ApiConstants.studentResults);
    return asMapList(data).map(ExamResult.fromJson).toList();
  }

  Future<List<StudentSummary>> loadChildren() async {
    final data = await _get(ApiConstants.parentChildren);
    return asMapList(data).map(StudentSummary.fromJson).toList();
  }

  Future<List<AttendanceRecord>> loadChildAttendance(int studentId) async {
    final data = await _get(
      ApiConstants.parentChildAttendance.replaceFirst(
        '{studentId}',
        studentId.toString(),
      ),
    );
    return asMapList(data).map(AttendanceRecord.fromJson).toList();
  }

  Future<List<ExamResult>> loadChildResults(int studentId) async {
    final data = await _get(
      ApiConstants.parentChildResults.replaceFirst(
        '{studentId}',
        studentId.toString(),
      ),
    );
    return asMapList(data).map(ExamResult.fromJson).toList();
  }

  Future<StudentDetailsBundle> loadStudentDetails(int id) async {
    final results = await Future.wait<dynamic>([
      loadStudent(id),
      loadStudentAttendance(id),
      loadStudentResults(id),
      loadStudentFees(id),
    ]);
    return StudentDetailsBundle(
      profile: results[0] as StudentProfile,
      attendance: results[1] as List<AttendanceRecord>,
      results: results[2] as List<ExamResult>,
      fees: results[3] as List<FeeRecord>,
    );
  }

  Future<dynamic> _get(
    String path, {
    Map<String, dynamic>? queryParameters,
  }) async {
    final response = await _send(
      () => _apiClient.get<dynamic>(
        path,
        queryParameters: queryParameters,
        options: Options(
          headers: {ApiConstants.headerAccept: ApiConstants.contentTypeJson},
        ),
      ),
      path,
    );
    _ensureSuccess(response, path);
    return response.data;
  }

  Future<dynamic> _post(String path, Map<String, dynamic> body) async {
    if (_apiClient.csrfInterceptor.csrfToken == null) {
      await _apiClient.fetchCsrfToken();
    }
    final response = await _send(
      () => _apiClient.post<dynamic>(
        path,
        data: body,
        options: Options(
          headers: {ApiConstants.headerAccept: ApiConstants.contentTypeJson},
        ),
      ),
      path,
    );
    _ensureSuccess(response, path);
    return response.data;
  }

  Future<Response<dynamic>> _send(
    Future<Response<dynamic>> Function() request,
    String path,
  ) async {
    try {
      return await request();
    } on DioException catch (error) {
      throw _mapDioError(error, path);
    }
  }

  void _ensureSuccess(Response<dynamic> response, String path) {
    final status = response.statusCode ?? 0;
    if (status >= 200 && status < 300) return;

    final message = _errorMessage(response.data) ?? 'Request to $path failed';
    switch (status) {
      case 400:
        throw BadRequestException(message: message, data: response.data);
      case 401:
        throw UnauthorizedException(message: message, data: response.data);
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

  ApiException _mapDioError(DioException error, String path) {
    final status = error.response?.statusCode;
    final message = _errorMessage(error.response?.data);
    switch (status) {
      case 400:
        return BadRequestException(
          message: message ?? 'The request was rejected',
          data: error.response?.data,
        );
      case 401:
        return UnauthorizedException(
          message: message ?? 'Your session has expired',
          data: error.response?.data,
        );
      case 403:
        return ForbiddenException(
          message: message ?? 'You do not have access to this action',
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
          message: message ?? error.message ?? 'Request to $path failed',
          data: error,
        );
    }
  }
}
