/// API constants for the GMS mobile application.
///
/// Centralizes all API endpoint paths and header names to prevent
/// typos and ensure consistency across the application.
class ApiConstants {
  ApiConstants._();

  static const String apiVersion = '/api/v1';

  // Authentication endpoints
  static const String authBase = '$apiVersion/auth';
  static const String login = '$authBase/login';
  static const String logout = '$authBase/logout';
  static const String csrfToken = '$authBase/csrf-token';
  static const String changePassword = '$authBase/change-password';

  // User endpoints
  static const String usersBase = '$apiVersion/users';
  static const String currentUser = '$usersBase/me';

  // Dashboard endpoints
  static const String dashboardBase = '$apiVersion/dashboard';
  static const String schoolDashboard = '$dashboardBase/school';
  static const String financialDashboard = '$apiVersion/financial-dashboard';

  // Teacher portal endpoints
  static const String teachersBase = '$apiVersion/teachers';
  static const String teacherProfile = '$teachersBase/profile';
  static const String teacherClasses = '$teachersBase/classes';
  static const String teacherStudents = '$teachersBase/students';
  static const String teacherTimetable = '$apiVersion/timetables/teacher/me';

  // Student portal endpoints
  static const String studentsBase = '$apiVersion/students';
  static const String studentProfile = '$studentsBase/profile';
  static const String studentClass = '$studentsBase/my-classes';
  static const String studentAttendance = '$studentsBase/my-attendance';
  static const String studentResults = '$studentsBase/my-results';
  static const String studentFees = '$studentsBase/my-fees';
  static const String studentTimetable = '$studentsBase/my-timetable';

  // Parent portal endpoints
  static const String parentsBase = '$apiVersion/parents';
  static const String parentProfile = '$parentsBase/profile';
  static const String parentChildren = '$parentsBase/children';
  static const String parentChildAttendance = '$parentChildren/{studentId}/attendance';
  static const String parentChildResults = '$parentChildren/{studentId}/results';
  static const String parentChildFees = '$parentChildren/{studentId}/fees';

  // Workflow endpoints
  static const String classrooms = '$apiVersion/classrooms';
  static const String assignedClassrooms = '$classrooms/teacher/assigned';
  static const String classroomTimetable =
      '$apiVersion/timetables/classroom/{classroomId}';
  static const String studentSearch = '$studentsBase/search';
  static const String studentById = '$studentsBase/{id}';
  static const String attendanceBase = '$apiVersion/attendance';
  static const String attendanceBulk = '$attendanceBase/bulk';
  static const String attendanceForClassroom =
      '$attendanceBase/classroom/{classroomId}';
  static const String attendanceForStudent =
      '$attendanceBase/student/{studentId}';
  static const String studentFeesBase = '$apiVersion/student-fees';
  static const String studentFeesForStudent =
      '$studentFeesBase/student/{studentId}';
  static const String feePayments = '$apiVersion/fee-payments';
  static const String resultsBase = '$apiVersion/results';
  static const String resultsForStudent = '$resultsBase/student/{studentId}';

  // Header names
  static const String headerAuthorization = 'Authorization';
  static const String headerCsrfToken = 'X-XSRF-TOKEN';
  static const String headerContentType = 'Content-Type';
  static const String headerAccept = 'Accept';

  // Cookie names
  static const String cookieAuth = 'gms_auth';
  static const String cookieCsrf = 'XSRF-TOKEN';

  // Content types
  static const String contentTypeJson = 'application/json';
}
