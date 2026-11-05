import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gms_mobile/core/api/api_exceptions.dart';
import 'package:gms_mobile/core/theme/app_theme.dart';
import 'package:gms_mobile/features/auth/domain/entities/user.dart';
import 'package:gms_mobile/features/auth/domain/enums/user_role.dart';
import 'package:gms_mobile/features/auth/presentation/providers/auth_provider.dart';
import 'package:gms_mobile/features/dashboard/data/models/dashboard_models.dart';
import 'package:gms_mobile/features/dashboard/presentation/providers/dashboard_providers.dart';
import 'package:gms_mobile/features/dashboard/presentation/screens/role_dashboard_screen.dart';

const _adminData = SchoolDashboard(
  schoolId: 1,
  totalStudents: 120,
  totalEmployees: 15,
  totalClassrooms: 9,
  totalSections: 18,
  totalExams: 4,
  feesCollectedAmount: 2500,
  feesUncollectedAmount: 7500,
);

const _teacherData = TeacherDashboardData(
  profile: EmployeeProfile(
    id: 2,
    fullName: 'Asha Rao',
    designation: 'Mathematics Teacher',
    department: 'Science',
  ),
  classes: [
    ClassroomInfo(id: 1, name: 'Grade 5 A', grade: '5', section: 'A'),
  ],
  students: [
    StudentSummary(id: 9, studentId: 'STU-9', fullName: 'Meera Kumar'),
  ],
  timetable: [],
);

const _studentData = StudentDashboardData(
  profile: StudentSummary(
    id: 9,
    studentId: 'STU-9',
    fullName: 'Meera Kumar',
  ),
  classroom: ClassroomInfo(id: 1, name: 'Grade 5 A'),
  attendance: AttendanceSummary([
    AttendanceEntry(status: 'PRESENT'),
    AttendanceEntry(status: 'ABSENT'),
  ]),
  results: [],
  fees: [],
  timetable: [],
);

const _parentData = ParentDashboardData(
  profile: ParentProfile(
    id: 3,
    firstName: 'Ravi',
    lastName: 'Kumar',
    children: [
      StudentSummary(id: 9, studentId: 'STU-9', fullName: 'Meera Kumar'),
    ],
  ),
  children: [
    ChildDashboardData(
      child: StudentSummary(
        id: 9,
        studentId: 'STU-9',
        fullName: 'Meera Kumar',
      ),
      attendance: AttendanceSummary([
        AttendanceEntry(status: 'PRESENT'),
      ]),
      results: [],
      fees: [],
    ),
  ],
);

const _accountantData = FinancialSummary(
  totalFeeExpected: 1500,
  totalFeeCollected: 400,
  totalFeeOutstanding: 1100,
  feeCollectionRate: 26.67,
  totalPayments: 3,
  averagePaymentAmount: 133.33,
  paymentsToday: 1,
  paymentsThisWeek: 2,
  paymentsThisMonth: 3,
  totalStudents: 2,
  studentsWithPendingFees: 2,
  studentsWithOverdueFees: 1,
  averageFeePerStudent: 750,
  overdueInvoices: 1,
  upcomingDueDates: 1,
);

User _userFor(UserRole role) {
  return User(
    id: '9',
    username: 'meera',
    fullName: 'Meera Kumar',
    role: role,
  );
}

Widget _app({
  required UserRole role,
  required List<Override> overrides,
}) {
  return ProviderScope(
    overrides: [
      currentRoleProvider.overrideWithValue(role),
      currentUserProvider.overrideWithValue(_userFor(role)),
      ...overrides,
    ],
    child: MaterialApp(
      theme: AppTheme.lightTheme,
      home: const RoleDashboardScreen(),
    ),
  );
}

void main() {
  testWidgets('administrator sees live school totals', (tester) async {
    await tester.pumpWidget(
      _app(
        role: UserRole.admin,
        overrides: [
          adminDashboardProvider.overrideWith((ref) async => _adminData),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.widgetWithText(AppBar, 'Administrator'), findsOneWidget);
    expect(find.text('120'), findsOneWidget);
    expect(find.text('Fees collected'), findsOneWidget);
    expect(find.text('₹7,500.00'), findsWidgets);
    expect(find.text('Collection rate'), findsOneWidget);
  });

  testWidgets('administrator sees a loading state first', (tester) async {
    final completer = Completer<SchoolDashboard>();
    await tester.pumpWidget(
      _app(
        role: UserRole.admin,
        overrides: [
          adminDashboardProvider.overrideWith((ref) => completer.future),
        ],
      ),
    );
    await tester.pump();

    expect(find.text('Loading your dashboard…'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);

    completer.complete(_adminData);
    await tester.pumpAndSettle();
    expect(find.text('120'), findsOneWidget);
  });

  testWidgets('administrator sees an error state with a retry action',
      (tester) async {
    await tester.pumpWidget(
      _app(
        role: UserRole.admin,
        overrides: [
          adminDashboardProvider.overrideWith(
            (ref) async => throw const NetworkException(
              message: 'No network connection',
            ),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Could not load the dashboard'), findsOneWidget);
    expect(find.text('No network connection'), findsOneWidget);
    expect(find.text('Try again'), findsOneWidget);
  });

  testWidgets('teacher sees assigned classes and students', (tester) async {
    await tester.pumpWidget(
      _app(
        role: UserRole.teacher,
        overrides: [
          teacherDashboardProvider.overrideWith((ref) async => _teacherData),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.widgetWithText(AppBar, 'Teacher'), findsOneWidget);
    expect(find.text('Grade 5 A'), findsWidgets);
    expect(find.text('Meera Kumar'), findsWidgets);
    expect(find.text('Asha Rao'), findsWidgets);
    expect(find.text('1'), findsWidgets);
  });

  testWidgets('student sees attendance and empty fee/result states',
      (tester) async {
    await tester.pumpWidget(
      _app(
        role: UserRole.student,
        overrides: [
          studentDashboardProvider.overrideWith((ref) async => _studentData),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.widgetWithText(AppBar, 'Student'), findsOneWidget);
    expect(find.text('Attendance'), findsOneWidget);
    expect(find.text('50.0%'), findsWidgets);
    expect(find.text('No fees have been assigned yet.'), findsOneWidget);
    expect(find.text('No results have been published yet.'), findsOneWidget);
  });

  testWidgets('parent sees one section per linked child', (tester) async {
    await tester.pumpWidget(
      _app(
        role: UserRole.parent,
        overrides: [
          parentDashboardProvider.overrideWith((ref) async => _parentData),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.widgetWithText(AppBar, 'Parent'), findsOneWidget);
    expect(find.text('Meera Kumar'), findsWidgets);
    expect(find.text('Fees billed'), findsOneWidget);
    expect(find.text('Contact'), findsOneWidget);
  });

  testWidgets('accountant sees fee collection aggregates', (tester) async {
    await tester.pumpWidget(
      _app(
        role: UserRole.accountant,
        overrides: [
          accountantDashboardProvider.overrideWith(
            (ref) async => _accountantData,
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.widgetWithText(AppBar, 'Accountant'), findsOneWidget);
    expect(find.text('Fees expected'), findsOneWidget);
    expect(find.text('₹1,500.00'), findsWidgets);
    expect(find.text('Payment activity'), findsOneWidget);
  });

  testWidgets('an unsupported role is sent back to the web product',
      (tester) async {
    await tester.pumpWidget(
      _app(role: UserRole.superadmin, overrides: const []),
    );
    await tester.pumpAndSettle();

    expect(find.text('This role signs in on the web'), findsOneWidget);
    expect(find.text('Sign out'), findsOneWidget);
  });
}
