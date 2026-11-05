import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gms_mobile/core/api/api_client.dart';
import 'package:gms_mobile/core/api/api_exceptions.dart';
import 'package:gms_mobile/core/constants/api_constants.dart';
import 'package:gms_mobile/features/dashboard/data/models/dashboard_models.dart';
import 'package:gms_mobile/features/dashboard/data/repositories/dashboard_repository.dart';
import 'package:mocktail/mocktail.dart';

class MockApiClient extends Mock implements ApiClient {}

void main() {
  late MockApiClient apiClient;
  late DashboardRepository repository;

  setUpAll(() {
    registerFallbackValue(Options());
  });

  setUp(() {
    apiClient = MockApiClient();
    repository = DashboardRepository(apiClient: apiClient);
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
        options: any(named: 'options'),
      ),
    ).thenAnswer((invocation) async {
      final path = invocation.positionalArguments.first as String;
      return handler(path);
    });
  }

  group('admin dashboard', () {
    test('decodes the school totals', () async {
      stubGet(
        (path) => response(ApiConstants.schoolDashboard, {
          'schoolId': 2,
          'totalStudents': 40,
          'totalEmployees': 6,
          'totalClassrooms': 4,
          'totalSections': 8,
          'totalExams': 2,
          'feesCollectedAmount': 1000,
          'feesUncollectedAmount': 3000,
        }),
      );

      final dashboard = await repository.loadAdminDashboard();

      expect(dashboard.schoolId, 2);
      expect(dashboard.totalStudents, 40);
      expect(dashboard.collectionRate, 25);
    });

    test('maps a 403 to a ForbiddenException', () async {
      stubGet(
        (path) => response(
          path,
          {'message': 'Forbidden'},
          statusCode: 403,
        ),
      );

      expect(
        repository.loadAdminDashboard(),
        throwsA(isA<ForbiddenException>()),
      );
    });

    test('maps a 401 to an UnauthorizedException', () async {
      stubGet(
        (path) => response(path, {}, statusCode: 401),
      );

      expect(
        repository.loadAdminDashboard(),
        throwsA(isA<UnauthorizedException>()),
      );
    });
  });

  test('teacher dashboard treats a missing timetable as empty', () async {
    stubGet((path) {
      return switch (path) {
        ApiConstants.teacherProfile => response(path, {
          'id': 5,
          'fullName': 'Asha Rao',
          'email': 'asha@example.com',
          'designation': 'Teacher',
          'department': 'Science',
        }),
        ApiConstants.teacherClasses => response(path, [
          {'id': 1, 'name': 'Grade 5 A', 'grade': '5', 'section': 'A'},
        ]),
        ApiConstants.teacherStudents => response(path, [
          {
            'id': 9,
            'studentId': 'STU-9',
            'firstName': 'Meera',
            'lastName': 'Kumar',
          },
        ]),
        _ => response(path, {'message': 'Not found'}, statusCode: 404),
      };
    });

    final dashboard = await repository.loadTeacherDashboard();

    expect(dashboard.profile.fullName, 'Asha Rao');
    expect(dashboard.classes.single.name, 'Grade 5 A');
    expect(dashboard.students.single.fullName, 'Meera Kumar');
    expect(dashboard.timetable, isEmpty);
  });

  test('student dashboard combines profile, attendance, fees and results',
      () async {
    stubGet((path) {
      return switch (path) {
        ApiConstants.studentProfile => response(path, {
          'id': 9,
          'studentId': 'STU-9',
          'firstName': 'Meera',
          'lastName': 'Kumar',
        }),
        ApiConstants.studentClass => response(path, {
          'id': 1,
          'name': 'Grade 5 A',
          'grade': '5',
          'section': 'A',
        }),
        ApiConstants.studentAttendance => response(path, [
          {'date': '2026-11-04', 'status': 'PRESENT'},
          {'date': '2026-11-03', 'status': 'ABSENT'},
        ]),
        ApiConstants.studentResults => response(path, [
          {
            'id': 1,
            'subjectName': 'Mathematics',
            'examName': 'Midterm',
            'percentage': 88,
            'grade': 'A',
          },
        ]),
        ApiConstants.studentFees => response(path, [
          {
            'id': 1,
            'status': 'PENDING',
            'totalAmount': 500,
            'amountPaid': 0,
            'balanceAmount': 500,
            'feeType': 'Tuition',
          },
        ]),
        ApiConstants.studentTimetable => response(path, [
          {
            'id': 1,
            'slots': [
              {
                'dayOfWeek': 'MONDAY',
                'period': 1,
                'assignment': {
                  'subject': {'name': 'Mathematics'},
                },
              },
            ],
          },
        ]),
        _ => response(path, {}, statusCode: 404),
      };
    });

    final dashboard = await repository.loadStudentDashboard();

    expect(dashboard.profile.fullName, 'Meera Kumar');
    expect(dashboard.classroom?.name, 'Grade 5 A');
    expect(dashboard.attendance.rate, 50);
    expect(dashboard.results.single.grade, 'A');
    expect(dashboard.fees.totalOutstanding, 500);
    expect(dashboard.timetable.single.subjectName, 'Mathematics');
  });

  test('parent dashboard loads every child in parallel', () async {
    stubGet((path) {
      if (path == ApiConstants.parentProfile) {
        return response(path, {
          'id': 3,
          'firstName': 'Ravi',
          'lastName': 'Kumar',
          'students': [
            {
              'id': 9,
              'studentId': 'STU-9',
              'firstName': 'Meera',
              'lastName': 'Kumar',
            },
            {
              'id': 10,
              'studentId': 'STU-10',
              'firstName': 'Arjun',
              'lastName': 'Kumar',
            },
          ],
        });
      }
      if (path.contains('/attendance')) {
        return response(path, [
          {'date': '2026-11-04', 'status': 'PRESENT'},
        ]);
      }
      if (path.contains('/results')) {
        return response(path, [
          {'id': 1, 'subjectName': 'Science', 'grade': 'B'},
        ]);
      }
      if (path.contains('/fees')) {
        return response(path, [
          {
            'id': 1,
            'status': 'OVERDUE',
            'totalAmount': 200,
            'amountPaid': 50,
            'balanceAmount': 150,
          },
        ]);
      }
      return response(path, {}, statusCode: 404);
    });

    final dashboard = await repository.loadParentDashboard();

    expect(dashboard.profile.fullName, 'Ravi Kumar');
    expect(dashboard.children, hasLength(2));
    expect(dashboard.children.first.child.fullName, 'Meera Kumar');
    expect(dashboard.children.first.attendance.present, 1);
    expect(dashboard.children.last.fees.totalOutstanding, 150);
  });

  test('accountant dashboard decodes the financial aggregates', () async {
    stubGet(
      (path) => response(path, {
        'totalFeeExpected': 1500,
        'totalFeeCollected': 400,
        'totalFeeOutstanding': 1100,
        'feeCollectionRate': 26.67,
        'totalPayments': 3,
        'paymentsToday': 1,
      }),
    );

    final summary = await repository.loadAccountantDashboard();

    expect(summary.totalFeeExpected, 1500);
    expect(summary.totalPayments, 3);
    expect(summary.paymentsToday, 1);
  });
}
