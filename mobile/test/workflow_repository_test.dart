import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gms_mobile/core/api/api_client.dart';
import 'package:gms_mobile/core/api/api_exceptions.dart';
import 'package:gms_mobile/core/api/interceptors/csrf_interceptor.dart';
import 'package:gms_mobile/core/constants/api_constants.dart';
import 'package:gms_mobile/features/dashboard/data/models/dashboard_models.dart';
import 'package:gms_mobile/features/workflows/data/models/workflow_models.dart';
import 'package:gms_mobile/features/workflows/data/repositories/workflow_repository.dart';
import 'package:mocktail/mocktail.dart';

class MockApiClient extends Mock implements ApiClient {}

class FakeCsrfInterceptor extends Fake implements CsrfInterceptor {
  @override
  String? get csrfToken => 'csrf-token';
}

void main() {
  late MockApiClient apiClient;
  late WorkflowRepository repository;

  setUpAll(() {
    registerFallbackValue(Options());
  });

  setUp(() {
    apiClient = MockApiClient();
    when(() => apiClient.csrfInterceptor).thenReturn(FakeCsrfInterceptor());
    repository = WorkflowRepository(apiClient: apiClient);
  });

  Response<dynamic> response(
    String path,
    dynamic data, {
    int statusCode = 200,
  }) {
    return Response<dynamic>(
      requestOptions: RequestOptions(path: path),
      statusCode: statusCode,
      data: data,
    );
  }

  void stubGet(Response<dynamic> Function(String path) handler) {
    when(
      () => apiClient.get<dynamic>(
        any(),
        queryParameters: any(named: 'queryParameters'),
        options: any(named: 'options'),
      ),
    ).thenAnswer((invocation) async {
      final path = invocation.positionalArguments.first as String;
      return handler(path);
    });
  }

  void stubPost(Response<dynamic> Function(String path, dynamic body) handler) {
    when(
      () => apiClient.post<dynamic>(
        any(),
        data: any(named: 'data'),
        options: any(named: 'options'),
      ),
    ).thenAnswer((invocation) async {
      final path = invocation.positionalArguments.first as String;
      final body = invocation.namedArguments[#data];
      return handler(path, body);
    });
  }

  test('searchStudents sends the query and page to the search endpoint',
      () async {
    stubGet(
      (path) => response(path, {
        'content': [
          {
            'id': 9,
            'studentId': 'STU-9',
            'firstName': 'Meera',
            'lastName': 'Kumar',
          },
        ],
        'totalElements': 1,
        'totalPages': 1,
        'number': 0,
        'size': 20,
      }),
    );

    final page = await repository.searchStudents(
      query: '  Meera ',
      classId: 3,
      page: 1,
    );

    final parameters = verify(
      () => apiClient.get<dynamic>(
        ApiConstants.studentSearch,
        queryParameters: captureAny(named: 'queryParameters'),
        options: any(named: 'options'),
      ),
    ).captured.single as Map<String, dynamic>;

    expect(parameters['query'], 'Meera');
    expect(parameters['classId'], 3);
    expect(parameters['page'], 1);
    expect(parameters['size'], 20);
    expect(page.students.single.fullName, 'Meera Kumar');
  });

  test('loadStudentDetails joins profile, attendance, results and fees',
      () async {
    stubGet((path) {
      if (path == '/api/v1/students/9') {
        return response(path, {
          'id': 9,
          'studentId': 'STU-9',
          'full_name': 'Meera Kumar',
          'class_id': 1,
        });
      }
      if (path == '/api/v1/attendance/student/9') {
        return response(path, [
          {'id': 1, 'status': 'PRESENT', 'date': '2026-11-04'},
        ]);
      }
      if (path == '/api/v1/results/student/9') {
        return response(path, [
          {'id': 2, 'subjectName': 'Science', 'obtainedMarks': 88},
        ]);
      }
      if (path == '/api/v1/student-fees/student/9') {
        return response(path, [
          {
            'id': 3,
            'studentId': 9,
            'status': 'PENDING',
            'totalAmount': 1000,
            'amountPaid': 0,
            'balanceAmount': 1000,
          },
        ]);
      }
      return response(path, {'message': 'Not found'}, statusCode: 404);
    });

    final bundle = await repository.loadStudentDetails(9);

    expect(bundle.profile.fullName, 'Meera Kumar');
    expect(bundle.attendance.single.status, 'PRESENT');
    expect(bundle.results.single.subjectName, 'Science');
    expect(bundle.totalOutstanding, 1000);
  });

  test('recordBulkAttendance posts the slot, date and marks', () async {
    stubPost(
      (path, body) => response(path, {
        'createdCount': 2,
        'skippedCount': 0,
      }),
    );

    await repository.recordBulkAttendance(
      timetableSlotId: 5,
      date: DateTime(2026, 11, 6),
      marks: [
        AttendanceMark(
          student: const StudentSummary(
            id: 9,
            studentId: 'STU-9',
            fullName: 'Meera Kumar',
          ),
          status: 'PRESENT',
        ),
        AttendanceMark(
          student: const StudentSummary(
            id: 10,
            studentId: 'STU-10',
            fullName: 'Ravi Shah',
          ),
          status: 'ABSENT',
          remarks: 'Sick',
        ),
      ],
    );

    final body = verify(
      () => apiClient.post<dynamic>(
        ApiConstants.attendanceBulk,
        data: captureAny(named: 'data'),
        options: any(named: 'options'),
      ),
    ).captured.single as Map<String, dynamic>;

    expect(body['timetableSlotId'], 5);
    expect(body['date'], '2026-11-06');
    final marks = body['attendance'] as List<dynamic>;
    expect(marks, hasLength(2));
    expect(marks.first, {'studentId': 9, 'status': 'PRESENT'});
    expect(marks.last, {
      'studentId': 10,
      'status': 'ABSENT',
      'remarks': 'Sick',
    });
  });

  test('recordPayment posts a validated payment and reads the receipt',
      () async {
    stubPost(
      (path, body) => response(path, {
        'id': 21,
        'studentFeeId': 8,
        'amount': 600,
        'paymentDate': '2026-11-06',
        'paymentMethod': 'CASH',
        'paymentReference': 'RCPT-1',
      }),
    );

    final receipt = await repository.recordPayment(
      FeePaymentInput(
        studentFeeId: 8,
        amount: 600,
        paymentDate: DateTime(2026, 11, 6),
        paymentMethod: 'CASH',
        paymentReference: 'RCPT-1',
      ),
    );

    final body = verify(
      () => apiClient.post<dynamic>(
        ApiConstants.feePayments,
        data: captureAny(named: 'data'),
        options: any(named: 'options'),
      ),
    ).captured.single as Map<String, dynamic>;
    expect(body['studentFeeId'], 8);
    expect(body['amount'], '600.00');
    expect(body['paymentReference'], 'RCPT-1');
    expect(receipt.id, 21);
    expect(receipt.amount, 600);
  });

  test('assigned classrooms use the teacher endpoint', () async {
    stubGet(
      (path) => response(path, [
        {'id': 1, 'name': 'Grade 5 A', 'grade': '5', 'section': 'A'},
      ]),
    );

    final rooms = await repository.loadAssignedClassrooms();

    verify(
      () => apiClient.get<dynamic>(
        ApiConstants.assignedClassrooms,
        queryParameters: any(named: 'queryParameters'),
        options: any(named: 'options'),
      ),
    ).called(1);
    expect(rooms.single.name, 'Grade 5 A');
  });

  test('a refused write maps to a ForbiddenException', () async {
    stubPost(
      (path, body) =>
          response(path, {'message': 'Forbidden'}, statusCode: 403),
    );

    expect(
      repository.recordPayment(
        FeePaymentInput(
          studentFeeId: 8,
          amount: 10,
          paymentDate: DateTime(2026, 11, 6),
          paymentMethod: 'CASH',
          paymentReference: 'RCPT-1',
        ),
      ),
      throwsA(isA<ForbiddenException>()),
    );
  });

  test('a failed read maps to a NotFoundException', () async {
    stubGet(
      (path) => response(path, {'message': 'Not found'}, statusCode: 404),
    );

    expect(
      repository.loadStudent(404),
      throwsA(isA<NotFoundException>()),
    );
  });

  test('child attendance uses the parent endpoint', () async {
    stubGet(
      (path) => response(path, [
        {'id': 1, 'status': 'PRESENT', 'date': '2026-11-04'},
      ]),
    );

    final records = await repository.loadChildAttendance(9);

    verify(
      () => apiClient.get<dynamic>(
        '/api/v1/parents/children/9/attendance',
        queryParameters: any(named: 'queryParameters'),
        options: any(named: 'options'),
      ),
    ).called(1);
    expect(records.single.status, 'PRESENT');
  });
}
