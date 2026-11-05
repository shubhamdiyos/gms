/// Models for the M0 workflow screens.
///
/// Every shape here is taken from a response the backend actually returns:
/// `Page<StudentResponse>` from `/students/search`, the `Student` entity from
/// `/students/{id}`, `AttendanceResponse` from `/attendance/*`,
/// `StudentFeeResponse` from `/student-fees`, `FeePaymentResponse` from
/// `/fee-payments` and `TimetableResponse` from `/timetables/*`.
library;

import 'package:equatable/equatable.dart';
import '../../../dashboard/data/models/dashboard_models.dart';

/// A page of `StudentResponse` rows.
class StudentPage {
  final List<StudentSummary> students;
  final int totalElements;
  final int totalPages;
  final int page;
  final int size;

  const StudentPage({
    required this.students,
    required this.totalElements,
    required this.totalPages,
    required this.page,
    required this.size,
  });

  factory StudentPage.fromJson(Map<String, dynamic> json) {
    final content = asMapList(json['content'])
        .map(StudentSummary.fromJson)
        .toList();
    final total = asInt(json['totalElements']);
    final size = asInt(json['size']);
    final totalPages = json['totalPages'] == null
        ? (size == 0 ? 0 : (total / size).ceil())
        : asInt(json['totalPages']);
    return StudentPage(
      students: content,
      totalElements: total == 0 ? content.length : total,
      totalPages: totalPages,
      page: asInt(json['number']),
      size: size == 0 ? content.length : size,
    );
  }

  static const empty = StudentPage(
    students: [],
    totalElements: 0,
    totalPages: 0,
    page: 0,
    size: 0,
  );

  bool get hasPrevious => page > 0;

  bool get hasNext => page + 1 < totalPages;
}

/// The free-text and page selection the search endpoint accepts.
class StudentQuery extends Equatable {
  final String query;
  final int? classId;
  final int page;
  final int size;

  const StudentQuery({
    this.query = '',
    this.classId,
    this.page = 0,
    this.size = 20,
  });

  StudentQuery copyWith({String? query, int? classId, int? page, int? size}) {
    return StudentQuery(
      query: query ?? this.query,
      classId: classId ?? this.classId,
      page: page ?? this.page,
      size: size ?? this.size,
    );
  }

  @override
  List<Object?> get props => [query, classId, page, size];
}

/// One student as `GET /students/{id}` returns them.
///
/// The entity spells the name as `first_name`/`last_name` plus `full_name`,
/// and the classroom as `class_id`; both spellings are read so the same model
/// works for the entity and for `StudentResponse`.
class StudentProfile {
  final int id;
  final String studentId;
  final String fullName;
  final String? email;
  final String? phoneNumber;
  final DateTime? dateOfBirth;
  final String? gender;
  final String status;
  final int? classId;
  final int? sectionId;

  const StudentProfile({
    required this.id,
    required this.studentId,
    required this.fullName,
    this.email,
    this.phoneNumber,
    this.dateOfBirth,
    this.gender,
    this.status = '1',
    this.classId,
    this.sectionId,
  });

  factory StudentProfile.fromJson(Map<String, dynamic> json) {
    final first = json['firstName']?.toString();
    final last = json['lastName']?.toString();
    final joined = [first, last]
        .where((part) => part != null && part.isNotEmpty)
        .join(' ');
    final fullName = (json['fullName'] ?? json['full_name'])?.toString();
    return StudentProfile(
      id: asInt(json['id']),
      studentId: json['studentId']?.toString() ?? '',
      fullName: (fullName == null || fullName.isEmpty)
          ? (joined.isEmpty ? 'Unnamed' : joined)
          : fullName,
      email: json['email']?.toString(),
      phoneNumber: json['phoneNumber']?.toString(),
      dateOfBirth: asDateTime(json['dateOfBirth']),
      gender: json['gender']?.toString(),
      status: json['status']?.toString() ?? '1',
      classId: json['classId'] != null
          ? asInt(json['classId'])
          : (json['class_id'] != null ? asInt(json['class_id']) : null),
      sectionId: json['sectionId'] != null
          ? asInt(json['sectionId'])
          : (json['section_id'] != null ? asInt(json['section_id']) : null),
    );
  }

  bool get isActive => status == '1';
}

/// One `AttendanceResponse`: who, which period, which day, what status.
class AttendanceRecord {
  final int id;
  final int? studentId;
  final String studentName;
  final int? timetableSlotId;
  final String? subjectName;
  final int? period;
  final DateTime? date;
  final String status;
  final String? remarks;

  const AttendanceRecord({
    required this.id,
    this.studentId,
    required this.studentName,
    this.timetableSlotId,
    this.subjectName,
    this.period,
    this.date,
    required this.status,
    this.remarks,
  });

  factory AttendanceRecord.fromJson(Map<String, dynamic> json) {
    final student = asMap(json['student']);
    final slot = asMap(json['timetableSlot']);
    final assignment = asMap(slot['assignment']);
    final subject = asMap(assignment['subject']);
    return AttendanceRecord(
      id: asInt(json['id']),
      studentId: student['id'] == null ? null : asInt(student['id']),
      studentName: student['fullName']?.toString() ?? 'Student',
      timetableSlotId: slot['id'] == null ? null : asInt(slot['id']),
      subjectName: subject['name']?.toString(),
      period: slot['period'] == null ? null : asInt(slot['period']),
      date: asDateTime(json['date']),
      status: (json['status']?.toString() ?? '').toUpperCase(),
      remarks: json['remarks']?.toString(),
    );
  }

  AttendanceRecord copyWith({String? status, String? remarks}) {
    return AttendanceRecord(
      id: id,
      studentId: studentId,
      studentName: studentName,
      timetableSlotId: timetableSlotId,
      subjectName: subjectName,
      period: period,
      date: date,
      status: status ?? this.status,
      remarks: remarks ?? this.remarks,
    );
  }
}

/// What the user picked for one student in the roll call.
class AttendanceMark {
  final StudentSummary student;
  String status;
  String? remarks;

  AttendanceMark({
    required this.student,
    this.status = 'PRESENT',
    this.remarks,
  });

  Map<String, dynamic> toJson() => {
    'studentId': student.id,
    'status': status,
    if (remarks != null && remarks!.isNotEmpty) 'remarks': remarks,
  };
}

/// The pupils of one classroom plus the attendance already recorded that day.
class RollCallData {
  final List<StudentSummary> students;
  final List<AttendanceRecord> existing;

  const RollCallData({required this.students, required this.existing});

  AttendanceRecord? existingFor(int? slotId, int studentId) {
    for (final record in existing) {
      if (record.studentId == studentId &&
          (slotId == null || record.timetableSlotId == slotId)) {
        return record;
      }
    }
    return null;
  }
}

/// A timetable with its classroom, as `/timetables/classroom/{id}` returns it.
class TimetableWeek {
  final int? classroomId;
  final String? classroomName;
  final String? academicYear;
  final List<TimetableSlot> slots;

  const TimetableWeek({
    this.classroomId,
    this.classroomName,
    this.academicYear,
    required this.slots,
  });

  factory TimetableWeek.fromJson(Map<String, dynamic> json) {
    return TimetableWeek(
      classroomId: json['classroomId'] == null
          ? null
          : asInt(json['classroomId']),
      classroomName: json['classroomName']?.toString(),
      academicYear: json['academicYear']?.toString(),
      slots: asMapList(json['slots']).map(TimetableSlot.fromJson).toList(),
    );
  }
}

/// The amount, method and reference a collection records.
class FeePaymentInput {
  final int studentFeeId;
  final double amount;
  final DateTime paymentDate;
  final String paymentMethod;
  final String paymentReference;
  final String? remarks;

  const FeePaymentInput({
    required this.studentFeeId,
    required this.amount,
    required this.paymentDate,
    required this.paymentMethod,
    required this.paymentReference,
    this.remarks,
  });

  static const methods = [
    'CASH',
    'BANK_TRANSFER',
    'CARD',
    'CHEQUE',
    'ONLINE',
  ];

  Map<String, dynamic> toJson() => {
    'studentFeeId': studentFeeId,
    'amount': amount.toStringAsFixed(2),
    'paymentDate': formatIsoDate(paymentDate),
    'paymentMethod': paymentMethod,
    'paymentReference': paymentReference,
    if (remarks != null && remarks!.isNotEmpty) 'remarks': remarks,
  };
}

/// `FeePaymentResponse` after the collection is written.
class PaymentReceipt {
  final int id;
  final int studentFeeId;
  final double amount;
  final DateTime? paymentDate;
  final String paymentMethod;
  final String paymentReference;
  final String? studentName;
  final String? feeType;
  final String? status;

  const PaymentReceipt({
    required this.id,
    required this.studentFeeId,
    required this.amount,
    this.paymentDate,
    required this.paymentMethod,
    required this.paymentReference,
    this.studentName,
    this.feeType,
    this.status,
  });

  factory PaymentReceipt.fromJson(Map<String, dynamic> json) {
    return PaymentReceipt(
      id: asInt(json['id']),
      studentFeeId: asInt(json['studentFeeId']),
      amount: asDouble(json['amount']),
      paymentDate: asDateTime(json['paymentDate']),
      paymentMethod: json['paymentMethod']?.toString() ?? '',
      paymentReference: json['paymentReference']?.toString() ?? '',
      studentName: json['studentName']?.toString(),
      feeType: json['feeType']?.toString(),
      status: json['status']?.toString(),
    );
  }
}

/// A student with the three reads their detail screen needs.
class StudentDetailsBundle {
  final StudentProfile profile;
  final List<AttendanceRecord> attendance;
  final List<ExamResult> results;
  final List<FeeRecord> fees;

  const StudentDetailsBundle({
    required this.profile,
    required this.attendance,
    required this.results,
    required this.fees,
  });

  double get totalBilled => fees.totalBilled;

  double get totalOutstanding => fees.totalOutstanding;
}

/// `yyyy-MM-dd`, the format every date parameter and body field uses.
String formatIsoDate(DateTime date) {
  final month = date.month.toString().padLeft(2, '0');
  final day = date.day.toString().padLeft(2, '0');
  return '${date.year}-$month-$day';
}

DateTime startOfDay(DateTime date) =>
    DateTime(date.year, date.month, date.day);
