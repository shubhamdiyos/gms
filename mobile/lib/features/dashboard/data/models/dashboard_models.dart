/// Read-only models for the role dashboards.
///
/// Every field mirrors a field the backend actually returns. Nothing here is
/// synthesized: a metric the API does not expose stays absent rather than
/// being rendered as a zero, which is why the financial summary keeps the
/// unsupported expense/profit fields out entirely.
library;

double asDouble(dynamic value) {
  if (value == null) return 0;
  if (value is num) return value.toDouble();
  return double.tryParse(value.toString()) ?? 0;
}

int asInt(dynamic value) {
  if (value == null) return 0;
  if (value is int) return value;
  if (value is num) return value.toInt();
  return int.tryParse(value.toString()) ?? 0;
}

DateTime? asDateTime(dynamic value) {
  if (value == null) return null;
  return DateTime.tryParse(value.toString());
}

Map<String, dynamic> asMap(dynamic value) {
  if (value is Map<String, dynamic>) return value;
  if (value is Map) return Map<String, dynamic>.from(value);
  return const {};
}

List<Map<String, dynamic>> asMapList(dynamic value) {
  if (value is List) {
    return value.whereType<Map>().map(Map<String, dynamic>.from).toList();
  }
  return const [];
}

/// The four fields every person-shaped response in the portal shares.
String _fullName(String? first, String? last) {
  final name = [first, last].where((part) => part != null && part.isNotEmpty);
  return name.isEmpty ? 'Unnamed' : name.join(' ');
}

class SchoolDashboard {
  final int schoolId;
  final int totalStudents;
  final int totalEmployees;
  final int totalClassrooms;
  final int totalSections;
  final int totalExams;
  final double feesCollectedAmount;
  final double feesUncollectedAmount;
  final DateTime? generatedAt;

  const SchoolDashboard({
    required this.schoolId,
    required this.totalStudents,
    required this.totalEmployees,
    required this.totalClassrooms,
    required this.totalSections,
    required this.totalExams,
    required this.feesCollectedAmount,
    required this.feesUncollectedAmount,
    this.generatedAt,
  });

  factory SchoolDashboard.fromJson(Map<String, dynamic> json) {
    return SchoolDashboard(
      schoolId: asInt(json['schoolId']),
      totalStudents: asInt(json['totalStudents']),
      totalEmployees: asInt(json['totalEmployees']),
      totalClassrooms: asInt(json['totalClassrooms']),
      totalSections: asInt(json['totalSections']),
      totalExams: asInt(json['totalExams']),
      feesCollectedAmount: asDouble(json['feesCollectedAmount']),
      feesUncollectedAmount: asDouble(json['feesUncollectedAmount']),
      generatedAt: asDateTime(json['generatedAt']),
    );
  }

  double get totalFees => feesCollectedAmount + feesUncollectedAmount;

  double? get collectionRate =>
      totalFees == 0 ? null : feesCollectedAmount / totalFees * 100;
}

class EmployeeProfile {
  final int id;
  final String fullName;
  final String? email;
  final String? designation;
  final String? department;

  const EmployeeProfile({
    required this.id,
    required this.fullName,
    this.email,
    this.designation,
    this.department,
  });

  factory EmployeeProfile.fromJson(Map<String, dynamic> json) {
    return EmployeeProfile(
      id: asInt(json['id']),
      fullName: json['fullName']?.toString() ?? 'Teacher',
      email: json['email']?.toString(),
      designation: json['designation']?.toString(),
      department: json['department']?.toString(),
    );
  }
}

class ClassroomInfo {
  final int id;
  final String name;
  final String? grade;
  final String? section;

  const ClassroomInfo({
    required this.id,
    required this.name,
    this.grade,
    this.section,
  });

  factory ClassroomInfo.fromJson(Map<String, dynamic> json) {
    return ClassroomInfo(
      id: asInt(json['id']),
      name: json['name']?.toString() ?? 'Classroom',
      grade: json['grade']?.toString(),
      section: json['section']?.toString(),
    );
  }
}

class StudentSummary {
  final int id;
  final String studentId;
  final String fullName;
  final String? email;
  final int? classId;
  final int? sectionId;

  const StudentSummary({
    required this.id,
    required this.studentId,
    required this.fullName,
    this.email,
    this.classId,
    this.sectionId,
  });

  factory StudentSummary.fromJson(Map<String, dynamic> json) {
    return StudentSummary(
      id: asInt(json['id']),
      studentId: json['studentId']?.toString() ?? '',
      fullName: _fullName(
        json['firstName']?.toString(),
        json['lastName']?.toString(),
      ),
      email: json['email']?.toString(),
      classId: json['classId'] == null ? null : asInt(json['classId']),
      sectionId: json['sectionId'] == null ? null : asInt(json['sectionId']),
    );
  }
}

class TimetableSlot {
  final String dayOfWeek;
  final int period;
  final String subjectName;
  final String? className;
  final String? teacherName;

  const TimetableSlot({
    required this.dayOfWeek,
    required this.period,
    required this.subjectName,
    this.className,
    this.teacherName,
  });

  factory TimetableSlot.fromJson(Map<String, dynamic> json) {
    final assignment = asMap(json['assignment']);
    final subject = asMap(assignment['subject']);
    final classroom = asMap(assignment['classroom']);
    final teacher = asMap(assignment['teacher']);
    return TimetableSlot(
      dayOfWeek: json['dayOfWeek']?.toString() ?? '',
      period: asInt(json['period']),
      subjectName: subject['name']?.toString() ?? 'Free period',
      className: classroom['name']?.toString(),
      teacherName: teacher['fullName']?.toString(),
    );
  }

  bool isOn(String dayKey) {
    final own = dayOfWeek.trim().toUpperCase();
    if (own.isEmpty) return false;
    return own == dayKey || own.startsWith(dayKey.substring(0, 3));
  }
}

/// Accepts either a single timetable object or a list of them, because the
/// student and parent endpoints answer a list while the teacher endpoint
/// answers one object.
List<TimetableSlot> parseTimetableSlots(dynamic value) {
  if (value is List) {
    return value.expand(parseTimetableSlots).toList();
  }
  if (value is Map) {
    return asMapList(value['slots']).map(TimetableSlot.fromJson).toList();
  }
  return const [];
}

class AttendanceEntry {
  final DateTime? date;
  final String status;
  final String? remarks;

  const AttendanceEntry({this.date, required this.status, this.remarks});

  factory AttendanceEntry.fromJson(Map<String, dynamic> json) {
    return AttendanceEntry(
      date: asDateTime(json['date']),
      status: (json['status']?.toString() ?? '').toUpperCase(),
      remarks: json['remarks']?.toString(),
    );
  }
}

class AttendanceSummary {
  final List<AttendanceEntry> entries;

  const AttendanceSummary(this.entries);

  factory AttendanceSummary.fromJson(dynamic value) {
    final entries = asMapList(value).map(AttendanceEntry.fromJson).toList();
    entries.sort((a, b) {
      final left = a.date ?? DateTime(1970);
      final right = b.date ?? DateTime(1970);
      return right.compareTo(left);
    });
    return AttendanceSummary(entries);
  }

  int get present =>
      entries.where((entry) => entry.status == 'PRESENT').length;

  int get late => entries.where((entry) => entry.status == 'LATE').length;

  int get absent =>
      entries.where((entry) => entry.status == 'ABSENT').length;

  int get recorded => present + late + absent;

  /// `null` when no attendance has been recorded in the window, so the UI can
  /// say "no attendance yet" instead of claiming 0%.
  double? get rate =>
      recorded == 0 ? null : (present + late) / recorded * 100;
}

class ExamResult {
  final int id;
  final String? examName;
  final String? subjectName;
  final int? obtainedMarks;
  final String? grade;
  final double? percentage;
  final String? status;

  const ExamResult({
    required this.id,
    this.examName,
    this.subjectName,
    this.obtainedMarks,
    this.grade,
    this.percentage,
    this.status,
  });

  factory ExamResult.fromJson(Map<String, dynamic> json) {
    return ExamResult(
      id: asInt(json['id']),
      examName: json['examName']?.toString(),
      subjectName: json['subjectName']?.toString(),
      obtainedMarks:
          json['obtainedMarks'] == null ? null : asInt(json['obtainedMarks']),
      grade: json['grade']?.toString(),
      percentage:
          json['percentage'] == null ? null : asDouble(json['percentage']),
      status: json['status']?.toString(),
    );
  }
}

class FeeRecord {
  final int id;
  final String status;
  final double totalAmount;
  final double amountPaid;
  final double balanceAmount;
  final DateTime? dueDate;
  final String? feeType;
  final String? description;
  final String? academicYear;
  final String? studentName;

  const FeeRecord({
    required this.id,
    required this.status,
    required this.totalAmount,
    required this.amountPaid,
    required this.balanceAmount,
    this.dueDate,
    this.feeType,
    this.description,
    this.academicYear,
    this.studentName,
  });

  factory FeeRecord.fromJson(Map<String, dynamic> json) {
    final total = asDouble(json['totalAmount']);
    final paid = asDouble(json['amountPaid']);
    return FeeRecord(
      id: asInt(json['id']),
      status: (json['status']?.toString() ?? '').toUpperCase(),
      totalAmount: total,
      amountPaid: paid,
      balanceAmount: json['balanceAmount'] == null
          ? (total - paid).clamp(0, double.infinity).toDouble()
          : asDouble(json['balanceAmount']),
      dueDate: asDateTime(json['dueDate']),
      feeType: json['feeType']?.toString(),
      description: json['description']?.toString(),
      academicYear: json['academicYear']?.toString(),
      studentName: json['studentName']?.toString(),
    );
  }

  bool get isOutstanding =>
      const {'PENDING', 'PARTIAL', 'OVERDUE'}.contains(status);

  String get label {
    final type = feeType ?? description;
    return type == null || type.isEmpty ? 'Fee' : type;
  }
}

extension FeeRecordTotals on List<FeeRecord> {
  double get totalBilled =>
      fold(0, (sum, fee) => sum + fee.totalAmount);

  double get totalPaid => fold(0, (sum, fee) => sum + fee.amountPaid);

  double get totalOutstanding => fold(
    0,
    (sum, fee) => sum + (fee.isOutstanding ? fee.balanceAmount : 0),
  );

  List<FeeRecord> get outstanding =>
      where((fee) => fee.isOutstanding).toList();
}

class ParentProfile {
  final int id;
  final String firstName;
  final String lastName;
  final String? email;
  final String? phoneNumber;
  final List<StudentSummary> children;

  const ParentProfile({
    required this.id,
    required this.firstName,
    required this.lastName,
    this.email,
    this.phoneNumber,
    this.children = const [],
  });

  factory ParentProfile.fromJson(Map<String, dynamic> json) {
    final students = asMapList(json['students'])
        .map(StudentSummary.fromJson)
        .toList();
    return ParentProfile(
      id: asInt(json['id']),
      firstName: json['firstName']?.toString() ?? '',
      lastName: json['lastName']?.toString() ?? '',
      email: json['email']?.toString(),
      phoneNumber: json['phoneNumber']?.toString(),
      children: students,
    );
  }

  String get fullName => _fullName(firstName, lastName);
}

/// The accountant's read of `/financial-dashboard`.
///
/// Only fields the endpoint actually populates are modelled. `totalExpenses`,
/// `netProfit`, `totalAssets`, `totalLiabilities` and `pendingApprovals` stay
/// out because the backend returns `null` for them — showing them would mean
/// inventing numbers.
class FinancialSummary {
  final double totalFeeExpected;
  final double totalFeeCollected;
  final double totalFeeOutstanding;
  final double feeCollectionRate;
  final int totalPayments;
  final double averagePaymentAmount;
  final int paymentsToday;
  final int paymentsThisWeek;
  final int paymentsThisMonth;
  final int totalStudents;
  final int studentsWithPendingFees;
  final int studentsWithOverdueFees;
  final double averageFeePerStudent;
  final int overdueInvoices;
  final int upcomingDueDates;
  final DateTime? lastUpdated;

  const FinancialSummary({
    required this.totalFeeExpected,
    required this.totalFeeCollected,
    required this.totalFeeOutstanding,
    required this.feeCollectionRate,
    required this.totalPayments,
    required this.averagePaymentAmount,
    required this.paymentsToday,
    required this.paymentsThisWeek,
    required this.paymentsThisMonth,
    required this.totalStudents,
    required this.studentsWithPendingFees,
    required this.studentsWithOverdueFees,
    required this.averageFeePerStudent,
    required this.overdueInvoices,
    required this.upcomingDueDates,
    this.lastUpdated,
  });

  factory FinancialSummary.fromJson(Map<String, dynamic> json) {
    return FinancialSummary(
      totalFeeExpected: asDouble(json['totalFeeExpected']),
      totalFeeCollected: asDouble(json['totalFeeCollected']),
      totalFeeOutstanding: asDouble(json['totalFeeOutstanding']),
      feeCollectionRate: asDouble(json['feeCollectionRate']),
      totalPayments: asInt(json['totalPayments']),
      averagePaymentAmount: asDouble(json['averagePaymentAmount']),
      paymentsToday: asInt(json['paymentsToday']),
      paymentsThisWeek: asInt(json['paymentsThisWeek']),
      paymentsThisMonth: asInt(json['paymentsThisMonth']),
      totalStudents: asInt(json['totalStudents']),
      studentsWithPendingFees: asInt(json['studentsWithPendingFees']),
      studentsWithOverdueFees: asInt(json['studentsWithOverdueFees']),
      averageFeePerStudent: asDouble(json['averageFeePerStudent']),
      overdueInvoices: asInt(json['overdueInvoices']),
      upcomingDueDates: asInt(json['upcomingDueDates']),
      lastUpdated: asDateTime(json['lastUpdated']),
    );
  }
}

class TeacherDashboardData {
  final EmployeeProfile profile;
  final List<ClassroomInfo> classes;
  final List<StudentSummary> students;
  final List<TimetableSlot> timetable;

  const TeacherDashboardData({
    required this.profile,
    required this.classes,
    required this.students,
    required this.timetable,
  });

  List<TimetableSlot> get todaySlots {
    final key = todayKey();
    final slots = timetable.where((slot) => slot.isOn(key)).toList();
    slots.sort((a, b) => a.period.compareTo(b.period));
    return slots;
  }

  int get weeklyPeriods => timetable.length;
}

class StudentDashboardData {
  final StudentSummary profile;
  final ClassroomInfo? classroom;
  final AttendanceSummary attendance;
  final List<ExamResult> results;
  final List<FeeRecord> fees;
  final List<TimetableSlot> timetable;

  const StudentDashboardData({
    required this.profile,
    this.classroom,
    required this.attendance,
    required this.results,
    required this.fees,
    required this.timetable,
  });

  List<TimetableSlot> get todaySlots {
    final key = todayKey();
    final slots = timetable.where((slot) => slot.isOn(key)).toList();
    slots.sort((a, b) => a.period.compareTo(b.period));
    return slots;
  }
}

class ChildDashboardData {
  final StudentSummary child;
  final AttendanceSummary attendance;
  final List<ExamResult> results;
  final List<FeeRecord> fees;

  const ChildDashboardData({
    required this.child,
    required this.attendance,
    required this.results,
    required this.fees,
  });
}

class ParentDashboardData {
  final ParentProfile profile;
  final List<ChildDashboardData> children;

  const ParentDashboardData({required this.profile, required this.children});
}

const _dayKeys = <String>[
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
];

/// Today's day name, matching the backend's `dayOfWeek` values.
String todayKey() => _dayKeys[DateTime.now().weekday - 1];

String formatDate(DateTime? date) {
  if (date == null) return '—';
  const months = [
    'Jan',
    'Feb',
    'Mar',
    'Apr',
    'May',
    'Jun',
    'Jul',
    'Aug',
    'Sep',
    'Oct',
    'Nov',
    'Dec',
  ];
  return '${date.day.toString().padLeft(2, '0')} '
      '${months[date.month - 1]} ${date.year}';
}

String formatMoney(num amount) {
  final negative = amount < 0;
  final fixed = amount.abs().toStringAsFixed(2);
  final parts = fixed.split('.');
  final digits = parts[0];
  return '${negative ? '-' : ''}₹${_groupIndian(digits)}.${parts[1]}';
}

/// Groups digits in the Indian convention: the last three, then pairs
/// (`12,34,567`), which is how the amounts are read on a fee receipt.
String _groupIndian(String digits) {
  if (digits.length <= 3) return digits;
  final lastThree = digits.substring(digits.length - 3);
  final rest = digits.substring(0, digits.length - 3);
  final buffer = StringBuffer();
  for (var i = 0; i < rest.length; i++) {
    if (i > 0 && (rest.length - i) % 2 == 0) {
      buffer.write(',');
    }
    buffer.write(rest[i]);
  }
  return '$buffer,$lastThree';
}

String formatPercent(double? value) {
  if (value == null) return '—';
  return '${value.toStringAsFixed(1)}%';
}
