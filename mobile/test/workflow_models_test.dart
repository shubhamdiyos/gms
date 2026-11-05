import 'package:flutter_test/flutter_test.dart';
import 'package:gms_mobile/features/dashboard/data/models/dashboard_models.dart';
import 'package:gms_mobile/features/workflows/data/models/workflow_models.dart';

void main() {
  group('StudentPage', () {
    test('reads the Spring Page envelope', () {
      final page = StudentPage.fromJson({
        'content': [
          {
            'id': 9,
            'studentId': 'STU-9',
            'firstName': 'Meera',
            'lastName': 'Kumar',
            'classId': 1,
          },
        ],
        'totalElements': 41,
        'totalPages': 3,
        'number': 0,
        'size': 20,
      });

      expect(page.students.single.fullName, 'Meera Kumar');
      expect(page.students.single.classId, 1);
      expect(page.totalElements, 41);
      expect(page.totalPages, 3);
      expect(page.hasNext, isTrue);
      expect(page.hasPrevious, isFalse);
    });

    test('derives the page count when the server omits it', () {
      final page = StudentPage.fromJson({
        'content': [],
        'totalElements': 45,
        'number': 1,
        'size': 20,
      });

      expect(page.totalPages, 3);
      expect(page.hasPrevious, isTrue);
      expect(page.hasNext, isTrue);
    });
  });

  group('StudentProfile', () {
    test('prefers the entity full_name and class_id spellings', () {
      final profile = StudentProfile.fromJson({
        'id': 4,
        'studentId': 'STD004',
        'firstName': 'Ravi',
        'lastName': 'Shah',
        'full_name': 'Ravi Shah',
        'class_id': 7,
        'section_id': 3,
        'status': '1',
      });

      expect(profile.fullName, 'Ravi Shah');
      expect(profile.classId, 7);
      expect(profile.sectionId, 3);
      expect(profile.isActive, isTrue);
    });

    test('falls back to the student response spellings', () {
      final profile = StudentProfile.fromJson({
        'id': 4,
        'firstName': 'Ravi',
        'lastName': 'Shah',
        'classId': 7,
        'status': '0',
      });

      expect(profile.fullName, 'Ravi Shah');
      expect(profile.classId, 7);
      expect(profile.isActive, isFalse);
    });
  });

  test('AttendanceRecord reads the nested student and slot', () {
    final record = AttendanceRecord.fromJson({
      'id': 11,
      'student': {'id': 9, 'fullName': 'Meera Kumar'},
      'timetableSlot': {
        'id': 5,
        'dayOfWeek': 'MONDAY',
        'period': 2,
        'assignment': {
          'subject': {'name': 'Mathematics'},
        },
      },
      'status': 'late',
      'date': '2026-11-02',
      'remarks': 'Bus delay',
    });

    expect(record.studentId, 9);
    expect(record.studentName, 'Meera Kumar');
    expect(record.timetableSlotId, 5);
    expect(record.subjectName, 'Mathematics');
    expect(record.period, 2);
    expect(record.status, 'LATE');
    expect(record.date, DateTime(2026, 11, 2));
    expect(record.remarks, 'Bus delay');
  });

  test('TimetableWeek carries slot ids for the roll call', () {
    final week = TimetableWeek.fromJson({
      'classroomId': 3,
      'classroomName': 'Grade 5 A',
      'academicYear': '2026-27',
      'slots': [
        {
          'id': 51,
          'dayOfWeek': 'MONDAY',
          'period': 1,
          'assignment': {
            'subject': {'name': 'Science'},
            'classroom': {'id': 3, 'name': 'Grade 5 A'},
            'teacher': {'fullName': 'Asha Rao'},
          },
        },
      ],
    });

    expect(week.classroomId, 3);
    expect(week.slots.single.id, 51);
    expect(week.slots.single.classroomId, 3);
    expect(week.slots.single.subjectName, 'Science');
    expect(week.slots.single.teacherName, 'Asha Rao');
  });

  test('FeeRecord reads the student id the ledger needs', () {
    final fee = FeeRecord.fromJson({
      'id': 8,
      'studentId': 9,
      'status': 'PARTIAL',
      'totalAmount': 1000,
      'amountPaid': 400,
      'balanceAmount': 600,
      'feeType': 'Tuition',
    });

    expect(fee.studentId, 9);
    expect(fee.isOutstanding, isTrue);
    expect(fee.balanceAmount, 600);
  });

  group('FeePaymentInput', () {
    test('serializes the fields the endpoint validates', () {
      final input = FeePaymentInput(
        studentFeeId: 8,
        amount: 600,
        paymentDate: DateTime(2026, 11, 6),
        paymentMethod: 'CASH',
        paymentReference: 'RCPT-1',
        remarks: 'Front desk',
      );

      expect(input.toJson(), {
        'studentFeeId': 8,
        'amount': '600.00',
        'paymentDate': '2026-11-06',
        'paymentMethod': 'CASH',
        'paymentReference': 'RCPT-1',
        'remarks': 'Front desk',
      });
    });

    test('leaves an empty remark out of the body', () {
      final input = FeePaymentInput(
        studentFeeId: 8,
        amount: 12.5,
        paymentDate: DateTime(2026, 1, 2),
        paymentMethod: 'ONLINE',
        paymentReference: 'TXN-9',
      );

      expect(input.toJson().containsKey('remarks'), isFalse);
      expect(input.toJson()['amount'], '12.50');
      expect(input.toJson()['paymentDate'], '2026-01-02');
    });
  });

  test('PaymentReceipt decodes the server receipt', () {
    final receipt = PaymentReceipt.fromJson({
      'id': 21,
      'studentFeeId': 8,
      'amount': 600,
      'paymentDate': '2026-11-06',
      'paymentMethod': 'CASH',
      'paymentReference': 'RCPT-1',
      'studentName': 'Meera Kumar',
      'feeType': 'Tuition',
    });

    expect(receipt.id, 21);
    expect(receipt.amount, 600);
    expect(receipt.studentName, 'Meera Kumar');
    expect(receipt.paymentDate, DateTime(2026, 11, 6));
  });

  test('RollCallData finds the existing mark for a slot', () {
    final data = RollCallData(
      students: const [],
      existing: [
        AttendanceRecord(
          id: 1,
          studentId: 9,
          studentName: 'Meera',
          timetableSlotId: 5,
          status: 'ABSENT',
        ),
        AttendanceRecord(
          id: 2,
          studentId: 9,
          studentName: 'Meera',
          timetableSlotId: 6,
          status: 'PRESENT',
        ),
      ],
    );

    expect(data.existingFor(5, 9)?.status, 'ABSENT');
    expect(data.existingFor(6, 9)?.status, 'PRESENT');
    expect(data.existingFor(7, 9), isNull);
  });

  test('formatIsoDate pads single-digit months and days', () {
    expect(formatIsoDate(DateTime(2026, 1, 2)), '2026-01-02');
  });
}
