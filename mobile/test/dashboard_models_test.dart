import 'package:flutter_test/flutter_test.dart';
import 'package:gms_mobile/features/dashboard/data/models/dashboard_models.dart';

void main() {
  group('SchoolDashboard', () {
    test('parses the school totals and computes the collection rate', () {
      final dashboard = SchoolDashboard.fromJson({
        'schoolId': 7,
        'totalStudents': 120,
        'totalEmployees': 15,
        'totalClassrooms': 9,
        'totalSections': 18,
        'totalExams': 4,
        'feesCollectedAmount': 2500.50,
        'feesUncollectedAmount': 7499.50,
        'generatedAt': '2026-11-05T10:15:30Z',
      });

      expect(dashboard.schoolId, 7);
      expect(dashboard.totalStudents, 120);
      expect(dashboard.totalExams, 4);
      expect(dashboard.feesCollectedAmount, 2500.50);
      expect(dashboard.totalFees, 10000);
      expect(dashboard.collectionRate, closeTo(25.005, 0.001));
      expect(dashboard.generatedAt, isNotNull);
    });

    test('does not invent a rate when no fees exist', () {
      final dashboard = SchoolDashboard.fromJson({
        'schoolId': 1,
        'feesCollectedAmount': null,
        'feesUncollectedAmount': null,
      });

      expect(dashboard.feesCollectedAmount, 0);
      expect(dashboard.collectionRate, isNull);
    });
  });

  group('Timetable parsing', () {
    const slotJson = {
      'dayOfWeek': 'MONDAY',
      'period': 2,
      'assignment': {
        'subject': {'name': 'Mathematics'},
        'classroom': {'name': 'Grade 5 A'},
        'teacher': {'fullName': 'Asha Rao'},
      },
    };

    test('reads a single timetable object', () {
      final slots = parseTimetableSlots({
        'id': 1,
        'slots': [slotJson],
      });

      expect(slots, hasLength(1));
      expect(slots.single.subjectName, 'Mathematics');
      expect(slots.single.className, 'Grade 5 A');
      expect(slots.single.teacherName, 'Asha Rao');
      expect(slots.single.period, 2);
    });

    test('reads a list of timetable objects', () {
      final slots = parseTimetableSlots([
        {
          'id': 1,
          'slots': [slotJson],
        },
        {
          'id': 2,
          'slots': [
            {'dayOfWeek': 'TUESDAY', 'period': 1, 'assignment': null},
          ],
        },
      ]);

      expect(slots, hasLength(2));
      expect(slots.last.subjectName, 'Free period');
    });

    test('matches the day case-insensitively', () {
      const slot = TimetableSlot(
        dayOfWeek: 'Monday',
        period: 1,
        subjectName: 'Science',
      );

      expect(slot.isOn('MONDAY'), isTrue);
      expect(slot.isOn('TUESDAY'), isFalse);
    });
  });

  group('AttendanceSummary', () {
    test('reports an honest null rate when nothing is recorded', () {
      final summary = AttendanceSummary.fromJson([]);

      expect(summary.recorded, 0);
      expect(summary.rate, isNull);
    });

    test('counts present, late and absent days', () {
      final summary = AttendanceSummary.fromJson([
        {'date': '2026-11-04', 'status': 'PRESENT'},
        {'date': '2026-11-03', 'status': 'present'},
        {'date': '2026-11-02', 'status': 'LATE'},
        {'date': '2026-11-01', 'status': 'ABSENT'},
      ]);

      expect(summary.present, 2);
      expect(summary.late, 1);
      expect(summary.absent, 1);
      expect(summary.recorded, 4);
      expect(summary.rate, 75);
      expect(summary.entries.first.status, 'PRESENT');
    });
  });

  group('FeeRecord', () {
    test('derives the balance when the API omits it', () {
      final fee = FeeRecord.fromJson({
        'id': 3,
        'status': 'PARTIAL',
        'totalAmount': 1000,
        'amountPaid': 400,
      });

      expect(fee.balanceAmount, 600);
      expect(fee.isOutstanding, isTrue);
      expect(fee.label, 'Fee');
    });

    test('totals billed, paid and outstanding across records', () {
      final fees = [
        FeeRecord.fromJson({
          'id': 1,
          'status': 'PAID',
          'totalAmount': 500,
          'amountPaid': 500,
        }),
        FeeRecord.fromJson({
          'id': 2,
          'status': 'PENDING',
          'totalAmount': 800,
          'amountPaid': 0,
          'balanceAmount': 800,
          'feeType': 'Tuition',
        }),
        FeeRecord.fromJson({
          'id': 3,
          'status': 'WAIVED',
          'totalAmount': 200,
          'amountPaid': 200,
        }),
      ];

      expect(fees.totalBilled, 1500);
      expect(fees.totalPaid, 700);
      expect(fees.totalOutstanding, 800);
      expect(fees.outstanding.single.label, 'Tuition');
    });
  });

  group('ParentProfile', () {
    test('reads the linked children from the profile response', () {
      final parent = ParentProfile.fromJson({
        'id': 4,
        'firstName': 'Ravi',
        'lastName': 'Kumar',
        'email': 'ravi@example.com',
        'students': [
          {
            'id': 10,
            'studentId': 'STU-10',
            'firstName': 'Meera',
            'lastName': 'Kumar',
          },
        ],
      });

      expect(parent.fullName, 'Ravi Kumar');
      expect(parent.children.single.fullName, 'Meera Kumar');
      expect(parent.children.single.studentId, 'STU-10');
    });
  });

  group('FinancialSummary', () {
    test('keeps unsupported placeholders out of the model', () {
      final summary = FinancialSummary.fromJson({
        'totalFeeExpected': 1500,
        'totalFeeCollected': 400,
        'totalFeeOutstanding': 1100,
        'feeCollectionRate': 26.67,
        'totalPayments': 3,
        'averagePaymentAmount': 133.33,
        'paymentsToday': 1,
        'paymentsThisWeek': 2,
        'paymentsThisMonth': 3,
        'totalStudents': 2,
        'studentsWithPendingFees': 2,
        'studentsWithOverdueFees': 1,
        'averageFeePerStudent': 750,
        'overdueInvoices': 1,
        'upcomingDueDates': 1,
        'totalExpenses': null,
        'netProfit': null,
        'lastUpdated': '2026-11-05',
      });

      expect(summary.totalFeeExpected, 1500);
      expect(summary.totalPayments, 3);
      expect(summary.studentsWithOverdueFees, 1);
      expect(summary.lastUpdated, DateTime(2026, 11, 5));
    });

    test('defaults absent counts to zero', () {
      final summary = FinancialSummary.fromJson({});

      expect(summary.totalFeeExpected, 0);
      expect(summary.paymentsToday, 0);
      expect(summary.lastUpdated, isNull);
    });
  });

  group('formatting', () {
    test('groups money in the Indian digit convention', () {
      expect(formatMoney(0), '₹0.00');
      expect(formatMoney(1234.5), '₹1,234.50');
      expect(formatMoney(1234567.89), '₹12,34,567.89');
    });

    test('formats percentages and dates', () {
      expect(formatPercent(null), '—');
      expect(formatPercent(93.456), '93.5%');
      expect(formatDate(DateTime(2026, 1, 9)), '09 Jan 2026');
      expect(formatDate(null), '—');
    });
  });
}
