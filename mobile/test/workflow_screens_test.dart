import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gms_mobile/core/api/api_exceptions.dart';
import 'package:gms_mobile/core/theme/app_theme.dart';
import 'package:gms_mobile/features/auth/domain/enums/user_role.dart';
import 'package:gms_mobile/features/auth/presentation/providers/auth_provider.dart';
import 'package:gms_mobile/features/dashboard/data/models/dashboard_models.dart';
import 'package:gms_mobile/features/workflows/data/models/workflow_models.dart';
import 'package:gms_mobile/features/workflows/data/repositories/workflow_repository.dart';
import 'package:gms_mobile/features/workflows/presentation/providers/workflow_providers.dart';
import 'package:gms_mobile/features/workflows/presentation/screens/child_attendance_screen.dart';
import 'package:gms_mobile/features/workflows/presentation/screens/fee_collection_screen.dart';
import 'package:gms_mobile/features/workflows/presentation/screens/mark_attendance_screen.dart';
import 'package:gms_mobile/features/workflows/presentation/screens/student_details_screen.dart';
import 'package:gms_mobile/features/workflows/presentation/screens/student_list_screen.dart';
import 'package:gms_mobile/features/workflows/presentation/screens/view_results_screen.dart';
import 'package:mocktail/mocktail.dart';

class MockWorkflowRepository extends Mock implements WorkflowRepository {}

const _student = StudentSummary(
  id: 9,
  studentId: 'STU-9',
  fullName: 'Meera Kumar',
  classId: 1,
);

const _page = StudentPage(
  students: [_student],
  totalElements: 1,
  totalPages: 1,
  page: 0,
  size: 20,
);

final _fee = FeeRecord(
  id: 8,
  studentId: 9,
  status: 'PENDING',
  totalAmount: 1000,
  amountPaid: 0,
  balanceAmount: 1000,
  feeType: 'Tuition',
  studentName: 'Meera Kumar',
);

final _paidFee = FeeRecord(
  id: 7,
  studentId: 9,
  status: 'PAID',
  totalAmount: 500,
  amountPaid: 500,
  balanceAmount: 0,
  feeType: 'Library',
  studentName: 'Meera Kumar',
);

Widget _app(Widget child, List<Override> overrides) {
  return ProviderScope(
    overrides: overrides,
    child: MaterialApp(theme: AppTheme.lightTheme, home: child),
  );
}

void main() {
  setUpAll(() {
    registerFallbackValue(DateTime(2026, 1, 1));
    registerFallbackValue(
      FeePaymentInput(
        studentFeeId: 1,
        amount: 1,
        paymentDate: DateTime(2026, 1, 1),
        paymentMethod: 'CASH',
        paymentReference: 'R',
      ),
    );
  });

  group('student list', () {
    testWidgets('renders the page and searches on submit', (tester) async {
      final queries = <String>[];
      await tester.pumpWidget(
        _app(
          const StudentListScreen(),
          [
            studentSearchProvider.overrideWith((ref, query) {
              queries.add(query.query);
              return Future.value(_page);
            }),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Meera Kumar'), findsOneWidget);
      expect(find.text('STU-9 · '), findsNothing);
      expect(find.textContaining('STU-9'), findsOneWidget);
      expect(find.text('1 found'), findsOneWidget);

      await tester.enterText(find.byType(TextField), 'Ravi');
      await tester.testTextInput.receiveAction(TextInputAction.search);
      await tester.pumpAndSettle();

      expect(queries.last, 'Ravi');
    });

    testWidgets('shows the empty state', (tester) async {
      await tester.pumpWidget(
        _app(
          const StudentListScreen(),
          [
            studentSearchProvider.overrideWith(
              (ref, query) => Future.value(StudentPage.empty),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('No students match this search.'), findsOneWidget);
    });

    testWidgets('shows the error state and retries', (tester) async {
      var calls = 0;
      await tester.pumpWidget(
        _app(
          const StudentListScreen(),
          [
            studentSearchProvider.overrideWith((ref, query) async {
              calls++;
              throw const NetworkException(message: 'Offline');
            }),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Could not load the student roll'), findsOneWidget);
      expect(find.text('Offline'), findsOneWidget);

      await tester.tap(find.text('Try again'));
      await tester.pumpAndSettle();
      expect(calls, greaterThan(1));
    });
  });

  testWidgets('student details renders profile, attendance, results and fees',
      (tester) async {
    const bundle = StudentDetailsBundle(
      profile: StudentProfile(
        id: 9,
        studentId: 'STU-9',
        fullName: 'Meera Kumar',
        email: 'meera@example.com',
        classId: 1,
        status: '1',
      ),
      attendance: [
        AttendanceRecord(
          id: 1,
          studentId: 9,
          studentName: 'Meera Kumar',
          subjectName: 'Science',
          period: 2,
          status: 'PRESENT',
          date: null,
        ),
        AttendanceRecord(
          id: 2,
          studentId: 9,
          studentName: 'Meera Kumar',
          subjectName: 'Science',
          period: 2,
          status: 'ABSENT',
          date: null,
        ),
      ],
      results: [
        ExamResult(
          id: 3,
          examName: 'Midterm',
          subjectName: 'Science',
          obtainedMarks: 88,
          grade: 'A',
        ),
      ],
      fees: [
        FeeRecord(
          id: 8,
          studentId: 9,
          status: 'PENDING',
          totalAmount: 1000,
          amountPaid: 0,
          balanceAmount: 1000,
          feeType: 'Tuition',
        ),
      ],
    );

    await tester.pumpWidget(
      _app(
        const StudentDetailsScreen(studentId: 9),
        [
          studentDetailsProvider.overrideWith((ref, id) async => bundle),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Meera Kumar'), findsOneWidget);
    expect(find.text('STU-9'), findsOneWidget);
    expect(find.text('Attendance rate 50.0%'), findsOneWidget);
    expect(find.text('Midterm'), findsOneWidget);
    expect(find.text('Tuition'), findsOneWidget);
    expect(find.text('₹1,000.00'), findsWidgets);
  });

  group('fee collection', () {
    testWidgets('filters to outstanding and collects a payment',
        (tester) async {
      final repository = MockWorkflowRepository();
      when(() => repository.recordPayment(any())).thenAnswer(
        (_) async => PaymentReceipt(
          id: 21,
          studentFeeId: 8,
          amount: 1000,
          paymentMethod: 'CASH',
          paymentReference: 'RCPT-1',
          studentName: 'Meera Kumar',
        ),
      );

      await tester.pumpWidget(
        _app(
          const FeeCollectionScreen(),
          [
            workflowRepositoryProvider.overrideWithValue(repository),
            feeLedgerProvider.overrideWith(
              (ref) async => [_fee, _paidFee],
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Tuition · ₹1,000.00 balance'), findsOneWidget);
      expect(find.text('Library · ₹0.00 balance'), findsNothing);

      await tester.tap(find.text('All fees'));
      await tester.pumpAndSettle();
      expect(find.text('Library · ₹0.00 balance'), findsOneWidget);

      await tester.tap(find.text('Collect').first);
      await tester.pumpAndSettle();
      expect(find.text('Record payment'), findsOneWidget);

      await tester.enterText(
        find.widgetWithText(TextField, 'Payment reference'),
        'RCPT-1',
      );
      await tester.tap(find.text('Record payment'));
      await tester.pumpAndSettle();

      verify(() => repository.recordPayment(any())).called(1);
      expect(
        find.text('Payment RCPT-1 recorded for Meera Kumar.'),
        findsOneWidget,
      );
    });

    testWidgets('requires a payment reference before saving', (tester) async {
      final repository = MockWorkflowRepository();

      await tester.pumpWidget(
        _app(
          const FeeCollectionScreen(),
          [
            workflowRepositoryProvider.overrideWithValue(repository),
            feeLedgerProvider.overrideWith((ref) async => [_fee]),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Collect'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Record payment'));
      await tester.pumpAndSettle();

      expect(find.text('A payment reference is required.'), findsOneWidget);
      verifyNever(() => repository.recordPayment(any()));
    });
  });

  testWidgets('mark attendance saves the pending students for the slot',
      (tester) async {
    final repository = MockWorkflowRepository();
    when(
      () => repository.recordBulkAttendance(
        timetableSlotId: any(named: 'timetableSlotId'),
        date: any(named: 'date'),
        marks: any(named: 'marks'),
      ),
    ).thenAnswer((_) async {});

    await tester.pumpWidget(
      _app(
        const MarkAttendanceScreen(),
        [
          currentRoleProvider.overrideWithValue(UserRole.teacher),
          workflowRepositoryProvider.overrideWithValue(repository),
          classroomOptionsProvider.overrideWith(
            (ref) async => [
              const ClassroomInfo(id: 1, name: 'Grade 5 A'),
            ],
          ),
          classroomTimetableProvider.overrideWith(
            (ref, id) async => const TimetableWeek(
              classroomId: 1,
              classroomName: 'Grade 5 A',
              slots: [
                TimetableSlot(
                  id: 51,
                  classroomId: 1,
                  dayOfWeek: 'MONDAY',
                  period: 1,
                  subjectName: 'Science',
                ),
              ],
            ),
          ),
          rollCallProvider.overrideWith(
            (ref, query) async => const RollCallData(
              students: [_student],
              existing: [],
            ),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Meera Kumar'), findsOneWidget);
    expect(find.text('Save attendance (1)'), findsOneWidget);

    await tester.tap(find.text('Save attendance (1)'));
    await tester.pumpAndSettle();

    verify(
      () => repository.recordBulkAttendance(
        timetableSlotId: 51,
        date: any(named: 'date'),
        marks: any(named: 'marks'),
      ),
    ).called(1);
    expect(find.text('Attendance saved for 1 student.'), findsOneWidget);
  });

  testWidgets('mark attendance shows already marked students read-only',
      (tester) async {
    await tester.pumpWidget(
      _app(
        const MarkAttendanceScreen(),
        [
          currentRoleProvider.overrideWithValue(UserRole.teacher),
          classroomOptionsProvider.overrideWith(
            (ref) async => [
              const ClassroomInfo(id: 1, name: 'Grade 5 A'),
            ],
          ),
          classroomTimetableProvider.overrideWith(
            (ref, id) async => const TimetableWeek(
              classroomId: 1,
              slots: [
                TimetableSlot(
                  id: 51,
                  classroomId: 1,
                  dayOfWeek: 'MONDAY',
                  period: 1,
                  subjectName: 'Science',
                ),
              ],
            ),
          ),
          rollCallProvider.overrideWith(
            (ref, query) async => const RollCallData(
              students: [_student],
              existing: [
                AttendanceRecord(
                  id: 1,
                  studentId: 9,
                  studentName: 'Meera Kumar',
                  timetableSlotId: 51,
                  status: 'ABSENT',
                ),
              ],
            ),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('This period is fully marked'), findsOneWidget);
    expect(find.text('ABSENT'), findsOneWidget);
    expect(find.text('1 student is already marked for this period and shown read-only.'),
        findsOneWidget);
  });

  testWidgets('student results render the published list', (tester) async {
    await tester.pumpWidget(
      _app(
        const ViewResultsScreen(),
        [
          currentRoleProvider.overrideWithValue(UserRole.student),
          myResultsProvider.overrideWith(
            (ref) async => const [
              ExamResult(
                id: 1,
                examName: 'Midterm',
                subjectName: 'Science',
                obtainedMarks: 88,
                grade: 'A',
                percentage: 88,
              ),
            ],
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Science'), findsOneWidget);
    expect(find.text('88 marks'), findsOneWidget);
    expect(find.text('A'), findsNWidgets(2));
    expect(find.text('88.0%'), findsWidgets);
  });

  testWidgets('parent results pick a child', (tester) async {
    await tester.pumpWidget(
      _app(
        const ViewResultsScreen(),
        [
          currentRoleProvider.overrideWithValue(UserRole.parent),
          parentChildrenProvider.overrideWith(
            (ref) async => [_student],
          ),
          childResultsProvider.overrideWith(
            (ref, id) async => const [
              ExamResult(
                id: 2,
                examName: 'Final',
                subjectName: 'Mathematics',
                obtainedMarks: 71,
                grade: 'B',
              ),
            ],
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Mathematics'), findsOneWidget);
    expect(find.text('Meera Kumar'), findsOneWidget);
  });

  testWidgets('child attendance summarises the records', (tester) async {
    await tester.pumpWidget(
      _app(
        const ChildAttendanceScreen(),
        [
          currentRoleProvider.overrideWithValue(UserRole.parent),
          parentChildrenProvider.overrideWith(
            (ref) async => [_student],
          ),
          childAttendanceProvider.overrideWith(
            (ref, id) async => [
              AttendanceRecord(
                id: 1,
                studentId: 9,
                studentName: 'Meera Kumar',
                subjectName: 'Science',
                period: 1,
                status: 'PRESENT',
                date: DateTime(2026, 11, 4),
              ),
              AttendanceRecord(
                id: 2,
                studentId: 9,
                studentName: 'Meera Kumar',
                subjectName: 'Science',
                period: 2,
                status: 'ABSENT',
                date: DateTime(2026, 11, 4),
              ),
            ],
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Attendance rate'), findsOneWidget);
    expect(find.text('50.0%'), findsOneWidget);
    expect(find.text('04 Nov 2026'), findsNWidgets(2));
    expect(find.text('PRESENT'), findsOneWidget);
    expect(find.text('ABSENT'), findsOneWidget);
  });
}
