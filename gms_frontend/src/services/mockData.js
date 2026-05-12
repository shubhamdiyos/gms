/**
 * Mock Data Store
 * Central source of mock data for every API endpoint.
 * Active only when VITE_MOCK_AUTH=true — zero impact in production.
 */

// ─── Schools ──────────────────────────────────────────────────────────────────
export const schools = [
  { id: 1, schoolName: 'Sunrise Public School',       schoolCode: 'SPS001', address: '12 MG Road, Bengaluru', city: 'Bengaluru', state: 'Karnataka', country: 'India', phone: '080-23456789', email: 'info@sunrise.edu', status: '1', createdAt: '2024-01-10', principalName: 'Rajesh Kumar', boardAffiliation: 'CBSE', establishedYear: 2005 },
  { id: 2, schoolName: 'Delhi Heritage Academy',      schoolCode: 'DHA002', address: '45 Connaught Place, New Delhi', city: 'New Delhi', state: 'Delhi', country: 'India', phone: '011-23456789', email: 'info@dha.edu', status: '1', createdAt: '2024-02-15', principalName: 'Sunita Mehra', boardAffiliation: 'CBSE', establishedYear: 2010 },
  { id: 3, schoolName: 'Mumbai International School', schoolCode: 'MIS003', address: '7 Marine Drive, Mumbai', city: 'Mumbai', state: 'Maharashtra', country: 'India', phone: '022-23456789', email: 'info@mis.edu', status: '0', createdAt: '2024-03-20', principalName: 'Anil Desai', boardAffiliation: 'IB', establishedYear: 1998 },
  { id: 4, schoolName: 'Chennai Scholars Institute',  schoolCode: 'CSI004', address: '22 Anna Salai, Chennai', city: 'Chennai', state: 'Tamil Nadu', country: 'India', phone: '044-23456789', email: 'info@csi.edu', status: '1', createdAt: '2024-04-05', principalName: 'Priya Rajan', boardAffiliation: 'ICSE', establishedYear: 2012 },
];

// ─── Academic Years ────────────────────────────────────────────────────────────
export const academicYears = [
  { id: 1, name: '2024-25', startDate: '2024-04-01', endDate: '2025-03-31', isCurrent: true, schoolId: 1 },
  { id: 2, name: '2023-24', startDate: '2023-04-01', endDate: '2024-03-31', isCurrent: false, schoolId: 1 },
  { id: 3, name: '2022-23', startDate: '2022-04-01', endDate: '2023-03-31', isCurrent: false, schoolId: 1 },
];

// ─── Classrooms ────────────────────────────────────────────────────────────────
export const classrooms = [
  { id: 1, name: 'Class 10-A', grade: 10, section: 'A', capacity: 40, schoolId: 1, academicYearId: 1, classTeacherId: 1 },
  { id: 2, name: 'Class 10-B', grade: 10, section: 'B', capacity: 38, schoolId: 1, academicYearId: 1, classTeacherId: 2 },
  { id: 3, name: 'Class 9-A',  grade: 9,  section: 'A', capacity: 42, schoolId: 1, academicYearId: 1, classTeacherId: 3 },
  { id: 4, name: 'Class 9-B',  grade: 9,  section: 'B', capacity: 40, schoolId: 1, academicYearId: 1, classTeacherId: 4 },
  { id: 5, name: 'Class 8-A',  grade: 8,  section: 'A', capacity: 45, schoolId: 1, academicYearId: 1, classTeacherId: 5 },
];

// ─── Sections ──────────────────────────────────────────────────────────────────
export const sections = [
  { id: 1, name: 'A', classroomId: 1 },
  { id: 2, name: 'B', classroomId: 2 },
  { id: 3, name: 'C', classroomId: 3 },
];

// ─── Subjects ──────────────────────────────────────────────────────────────────
export const subjects = [
  { id: 1, name: 'Mathematics',        code: 'MATH10', grade: 10, schoolId: 1 },
  { id: 2, name: 'Physics',            code: 'PHY10',  grade: 10, schoolId: 1 },
  { id: 3, name: 'Chemistry',          code: 'CHEM10', grade: 10, schoolId: 1 },
  { id: 4, name: 'English Literature', code: 'ENG10',  grade: 10, schoolId: 1 },
  { id: 5, name: 'History',            code: 'HIST10', grade: 10, schoolId: 1 },
  { id: 6, name: 'Computer Science',   code: 'CS10',   grade: 10, schoolId: 1 },
  { id: 7, name: 'Biology',            code: 'BIO9',   grade: 9,  schoolId: 1 },
  { id: 8, name: 'Geography',          code: 'GEO9',   grade: 9,  schoolId: 1 },
];

// ─── Employees ─────────────────────────────────────────────────────────────────
export const employees = [
  { id: 1, fullName: 'Rajesh Kumar',    email: 'rajesh@sunrise.edu',   phone: '9876543210', designation: 'Principal',    department: 'Administration', isActive: true, joiningDate: '2020-06-01', schoolId: 1 },
  { id: 2, fullName: 'Priya Sharma',    email: 'priya@sunrise.edu',    phone: '9876543211', designation: 'Math Teacher', department: 'Academics',      isActive: true, joiningDate: '2021-07-15', schoolId: 1 },
  { id: 3, fullName: 'Anita Verma',     email: 'anita@sunrise.edu',    phone: '9876543212', designation: 'Science Teacher', department: 'Academics',   isActive: true, joiningDate: '2021-08-01', schoolId: 1 },
  { id: 4, fullName: 'Suresh Patil',    email: 'suresh@sunrise.edu',   phone: '9876543213', designation: 'English Teacher', department: 'Academics',   isActive: true, joiningDate: '2022-01-10', schoolId: 1 },
  { id: 5, fullName: 'Meena Iyer',      email: 'meena@sunrise.edu',    phone: '9876543214', designation: 'History Teacher', department: 'Academics',   isActive: false, joiningDate: '2022-03-20', schoolId: 1 },
  { id: 6, fullName: 'Vikram Singh',    email: 'vikram@sunrise.edu',   phone: '9876543215', designation: 'CS Teacher',    department: 'Academics',      isActive: true, joiningDate: '2023-06-01', schoolId: 1 },
  { id: 7, fullName: 'Deepa Nair',      email: 'deepa@sunrise.edu',    phone: '9876543216', designation: 'Accountant',   department: 'Finance',        isActive: true, joiningDate: '2020-09-15', schoolId: 1 },
];

// ─── Students ──────────────────────────────────────────────────────────────────
export const students = [
  { id: 1,  fullName: 'Arjun Mehta',       rollNo: 'S001', email: 'arjun@student.edu',   phone: '9111111111', dateOfBirth: '2008-05-12', gender: 'Male',   classroomId: 1, className: 'Class 10-A', isActive: true,  parentName: 'Ramesh Mehta',   admissionDate: '2020-06-01' },
  { id: 2,  fullName: 'Sneha Patel',        rollNo: 'S002', email: 'sneha@student.edu',   phone: '9111111112', dateOfBirth: '2008-07-22', gender: 'Female', classroomId: 1, className: 'Class 10-A', isActive: true,  parentName: 'Sunil Patel',    admissionDate: '2020-06-01' },
  { id: 3,  fullName: 'Rahul Gupta',        rollNo: 'S003', email: 'rahul@student.edu',   phone: '9111111113', dateOfBirth: '2008-03-15', gender: 'Male',   classroomId: 1, className: 'Class 10-A', isActive: true,  parentName: 'Kamlesh Gupta',  admissionDate: '2020-06-01' },
  { id: 4,  fullName: 'Kavya Reddy',        rollNo: 'S004', email: 'kavya@student.edu',   phone: '9111111114', dateOfBirth: '2008-11-30', gender: 'Female', classroomId: 2, className: 'Class 10-B', isActive: true,  parentName: 'Ravi Reddy',     admissionDate: '2021-06-01' },
  { id: 5,  fullName: 'Aditya Joshi',       rollNo: 'S005', email: 'aditya@student.edu',  phone: '9111111115', dateOfBirth: '2009-01-18', gender: 'Male',   classroomId: 3, className: 'Class 9-A',  isActive: true,  parentName: 'Dinesh Joshi',   admissionDate: '2021-06-01' },
  { id: 6,  fullName: 'Pooja Singh',        rollNo: 'S006', email: 'pooja@student.edu',   phone: '9111111116', dateOfBirth: '2009-04-25', gender: 'Female', classroomId: 3, className: 'Class 9-A',  isActive: true,  parentName: 'Harpal Singh',   admissionDate: '2021-06-01' },
  { id: 7,  fullName: 'Nikhil Sharma',      rollNo: 'S007', email: 'nikhil@student.edu',  phone: '9111111117', dateOfBirth: '2009-08-09', gender: 'Male',   classroomId: 4, className: 'Class 9-B',  isActive: false, parentName: 'Anil Sharma',    admissionDate: '2022-06-01' },
  { id: 8,  fullName: 'Riya Kapoor',        rollNo: 'S008', email: 'riya@student.edu',    phone: '9111111118', dateOfBirth: '2010-02-14', gender: 'Female', classroomId: 5, className: 'Class 8-A',  isActive: true,  parentName: 'Sanjay Kapoor',  admissionDate: '2022-06-01' },
  { id: 9,  fullName: 'Vivek Nair',         rollNo: 'S009', email: 'vivek@student.edu',   phone: '9111111119', dateOfBirth: '2010-06-30', gender: 'Male',   classroomId: 5, className: 'Class 8-A',  isActive: true,  parentName: 'Mohan Nair',     admissionDate: '2022-06-01' },
  { id: 10, fullName: 'Ishaan Choudhary',   rollNo: 'S010', email: 'ishaan@student.edu',  phone: '9111111120', dateOfBirth: '2010-09-05', gender: 'Male',   classroomId: 5, className: 'Class 8-A',  isActive: true,  parentName: 'Girish Choudhary', admissionDate: '2022-06-01' },
];

// ─── Attendance ────────────────────────────────────────────────────────────────
const today = new Date().toISOString().split('T')[0];
export const attendanceRecords = [
  { id: 1, studentId: 1, studentName: 'Arjun Mehta',     classroomId: 1, date: today, status: 'PRESENT', remarks: '' },
  { id: 2, studentId: 2, studentName: 'Sneha Patel',      classroomId: 1, date: today, status: 'PRESENT', remarks: '' },
  { id: 3, studentId: 3, studentName: 'Rahul Gupta',      classroomId: 1, date: today, status: 'ABSENT',  remarks: 'Sick leave' },
];

export const myAttendance = [
  { date: '2024-11-01', status: 'PRESENT' }, { date: '2024-11-04', status: 'PRESENT' },
  { date: '2024-11-05', status: 'ABSENT'  }, { date: '2024-11-06', status: 'PRESENT' },
  { date: '2024-11-07', status: 'PRESENT' }, { date: '2024-11-08', status: 'PRESENT' },
  { date: '2024-11-11', status: 'PRESENT' }, { date: '2024-11-12', status: 'PRESENT' },
  { date: '2024-11-13', status: 'LATE'    }, { date: '2024-11-14', status: 'PRESENT' },
];

// ─── Fees ──────────────────────────────────────────────────────────────────────
export const fees = [
  { id: 1, name: 'Tuition Fee',      amount: 12000, dueDate: '2024-04-30', frequency: 'QUARTERLY', schoolId: 1, academicYearId: 1 },
  { id: 2, name: 'Sports Fee',       amount: 2000,  dueDate: '2024-04-30', frequency: 'ANNUAL',    schoolId: 1, academicYearId: 1 },
  { id: 3, name: 'Lab Fee',          amount: 3000,  dueDate: '2024-04-30', frequency: 'ANNUAL',    schoolId: 1, academicYearId: 1 },
  { id: 4, name: 'Transport Fee',    amount: 5000,  dueDate: '2024-04-30', frequency: 'QUARTERLY', schoolId: 1, academicYearId: 1 },
  { id: 5, name: 'Library Fee',      amount: 1000,  dueDate: '2024-04-30', frequency: 'ANNUAL',    schoolId: 1, academicYearId: 1 },
];

export const myFees = [
  { id: 1, feeName: 'Tuition Fee Q1', amount: 12000, dueDate: '2024-04-30', paidDate: '2024-04-25', status: 'PAID',    transactionId: 'TXN001' },
  { id: 2, feeName: 'Sports Fee',     amount: 2000,  dueDate: '2024-04-30', paidDate: '2024-04-28', status: 'PAID',    transactionId: 'TXN002' },
  { id: 3, feeName: 'Tuition Fee Q2', amount: 12000, dueDate: '2024-07-31', paidDate: null,          status: 'PENDING', transactionId: null },
  { id: 4, feeName: 'Lab Fee',        amount: 3000,  dueDate: '2024-04-30', paidDate: null,          status: 'OVERDUE', transactionId: null },
];

// ─── Exams ─────────────────────────────────────────────────────────────────────
export const exams = [
  { id: 1, name: 'Unit Test 1',    type: 'UNIT_TEST',  startDate: '2024-07-10', endDate: '2024-07-15', totalMarks: 25,  passingMarks: 10, academicYearId: 1, schoolId: 1 },
  { id: 2, name: 'Mid-Term Exam',  type: 'MID_TERM',   startDate: '2024-09-01', endDate: '2024-09-10', totalMarks: 100, passingMarks: 40, academicYearId: 1, schoolId: 1 },
  { id: 3, name: 'Unit Test 2',    type: 'UNIT_TEST',  startDate: '2024-10-10', endDate: '2024-10-15', totalMarks: 25,  passingMarks: 10, academicYearId: 1, schoolId: 1 },
  { id: 4, name: 'Final Exam',     type: 'FINAL',      startDate: '2025-02-01', endDate: '2025-02-15', totalMarks: 100, passingMarks: 40, academicYearId: 1, schoolId: 1 },
];

// ─── Results ───────────────────────────────────────────────────────────────────
export const results = [
  { id: 1, studentId: 1, studentName: 'Arjun Mehta',  examId: 2, examName: 'Mid-Term Exam', subjectId: 1, subjectName: 'Mathematics', marksObtained: 87, totalMarks: 100, grade: 'A', remarks: 'Excellent' },
  { id: 2, studentId: 1, studentName: 'Arjun Mehta',  examId: 2, examName: 'Mid-Term Exam', subjectId: 2, subjectName: 'Physics',      marksObtained: 79, totalMarks: 100, grade: 'B', remarks: 'Good' },
  { id: 3, studentId: 1, studentName: 'Arjun Mehta',  examId: 2, examName: 'Mid-Term Exam', subjectId: 3, subjectName: 'Chemistry',    marksObtained: 92, totalMarks: 100, grade: 'A+', remarks: 'Outstanding' },
  { id: 4, studentId: 2, studentName: 'Sneha Patel',   examId: 2, examName: 'Mid-Term Exam', subjectId: 1, subjectName: 'Mathematics', marksObtained: 95, totalMarks: 100, grade: 'A+', remarks: 'Outstanding' },
  { id: 5, studentId: 2, studentName: 'Sneha Patel',   examId: 2, examName: 'Mid-Term Exam', subjectId: 2, subjectName: 'Physics',      marksObtained: 88, totalMarks: 100, grade: 'A', remarks: 'Excellent' },
];

export const myResults = [
  { examName: 'Mid-Term Exam', subjectName: 'Mathematics',        marksObtained: 87, totalMarks: 100, grade: 'A',  percentage: 87 },
  { examName: 'Mid-Term Exam', subjectName: 'Physics',            marksObtained: 79, totalMarks: 100, grade: 'B',  percentage: 79 },
  { examName: 'Mid-Term Exam', subjectName: 'Chemistry',          marksObtained: 92, totalMarks: 100, grade: 'A+', percentage: 92 },
  { examName: 'Mid-Term Exam', subjectName: 'English Literature', marksObtained: 81, totalMarks: 100, grade: 'A',  percentage: 81 },
  { examName: 'Unit Test 1',   subjectName: 'Mathematics',        marksObtained: 22, totalMarks: 25,  grade: 'A+', percentage: 88 },
  { examName: 'Unit Test 1',   subjectName: 'Physics',            marksObtained: 18, totalMarks: 25,  grade: 'B',  percentage: 72 },
];

// ─── Notifications ─────────────────────────────────────────────────────────────
export const notifications = [
  { id: 1, title: 'Mid-Term Results Published',   message: 'Mid-term exam results are now available in the results section.', type: 'INFO',    isRead: false, createdAt: '2024-09-20T10:00:00' },
  { id: 2, title: 'Fee Payment Reminder',          message: 'Q2 tuition fee is due on July 31st. Please pay to avoid late charges.', type: 'WARNING', isRead: false, createdAt: '2024-07-20T09:00:00' },
  { id: 3, title: 'School Holiday - Diwali',       message: 'School will remain closed from Oct 31 to Nov 3 for Diwali holidays.', type: 'INFO',    isRead: true,  createdAt: '2024-10-25T08:00:00' },
  { id: 4, title: 'Parent-Teacher Meeting',        message: 'PTM scheduled for November 15th. Please confirm your attendance.', type: 'INFO',    isRead: false, createdAt: '2024-11-01T07:00:00' },
  { id: 5, title: 'Annual Sports Day',             message: 'Annual sports day on December 5th. Students must register for events by Nov 25.', type: 'INFO', isRead: true, createdAt: '2024-11-10T06:00:00' },
];

// ─── Announcements ─────────────────────────────────────────────────────────────
export const announcements = [
  { id: 1, title: 'Annual Day Celebration',          content: 'Annual day will be celebrated on December 20th. All students and parents are invited.', isPublished: true,  publishedAt: '2024-11-01', targetAudience: 'ALL',     schoolId: 1 },
  { id: 2, title: 'Exam Schedule Released',           content: 'Final exam schedule for 2024-25 has been released. Check the academic calendar.', isPublished: true,  publishedAt: '2024-11-05', targetAudience: 'STUDENTS', schoolId: 1 },
  { id: 3, title: 'New Library Books Available',      content: 'New collection of science and literature books have been added to the school library.', isPublished: true, publishedAt: '2024-11-08', targetAudience: 'ALL',     schoolId: 1 },
  { id: 4, title: 'Staff Training Workshop',          content: 'Mandatory training workshop for all teachers on Nov 22nd. Venue: Conference Hall.', isPublished: false, publishedAt: null,          targetAudience: 'TEACHERS', schoolId: 1 },
];

// ─── Teacher-specific ──────────────────────────────────────────────────────────
export const teacherProfile = {
  id: 3, fullName: 'Anita Verma', email: 'anita@sunrise.edu', phone: '9876543212',
  designation: 'Science Teacher', department: 'Academics', schoolId: 1,
  subjects: ['Physics', 'Chemistry'], classes: ['Class 10-A', 'Class 10-B'],
};

export const teacherClasses = [
  { id: 1, name: 'Class 10-A', grade: 10, section: 'A', studentCount: 38, subject: 'Physics' },
  { id: 2, name: 'Class 10-B', grade: 10, section: 'B', studentCount: 36, subject: 'Chemistry' },
  { id: 3, name: 'Class 9-A',  grade: 9,  section: 'A', studentCount: 40, subject: 'Physics' },
];

export const teacherStudents = students.slice(0, 6);

export const teacherTimetable = [
  { id: 1, day: 'Monday',    period: 1, startTime: '08:00', endTime: '08:45', subject: 'Physics',   classroom: 'Class 10-A' },
  { id: 2, day: 'Monday',    period: 2, startTime: '08:50', endTime: '09:35', subject: 'Chemistry', classroom: 'Class 10-B' },
  { id: 3, day: 'Tuesday',   period: 1, startTime: '08:00', endTime: '08:45', subject: 'Physics',   classroom: 'Class 9-A' },
  { id: 4, day: 'Wednesday', period: 3, startTime: '09:40', endTime: '10:25', subject: 'Chemistry', classroom: 'Class 10-A' },
  { id: 5, day: 'Thursday',  period: 2, startTime: '08:50', endTime: '09:35', subject: 'Physics',   classroom: 'Class 10-B' },
  { id: 6, day: 'Friday',    period: 4, startTime: '10:30', endTime: '11:15', subject: 'Chemistry', classroom: 'Class 9-A' },
];

// ─── Student-specific ─────────────────────────────────────────────────────────
export const studentProfile = {
  id: 1, fullName: 'Arjun Mehta', rollNo: 'S001', email: 'arjun@student.edu',
  phone: '9111111111', dateOfBirth: '2008-05-12', gender: 'Male',
  className: 'Class 10-A', schoolName: 'Sunrise Public School',
  parentName: 'Ramesh Mehta', parentPhone: '9888888888', address: '14 Park Street, Bengaluru',
  admissionDate: '2020-06-01', bloodGroup: 'O+',
};

export const myClasses = [{ id: 1, name: 'Class 10-A', grade: 10, section: 'A', classTeacher: 'Priya Sharma' }];

export const mySubjects = subjects.filter(s => s.grade === 10);

export const myTimetable = [
  { day: 'Monday',    period: 1, startTime: '08:00', endTime: '08:45', subject: 'Mathematics', teacher: 'Priya Sharma' },
  { day: 'Monday',    period: 2, startTime: '08:50', endTime: '09:35', subject: 'Physics',      teacher: 'Anita Verma' },
  { day: 'Monday',    period: 3, startTime: '09:40', endTime: '10:25', subject: 'English',      teacher: 'Suresh Patil' },
  { day: 'Tuesday',   period: 1, startTime: '08:00', endTime: '08:45', subject: 'Chemistry',    teacher: 'Anita Verma' },
  { day: 'Tuesday',   period: 2, startTime: '08:50', endTime: '09:35', subject: 'Mathematics',  teacher: 'Priya Sharma' },
  { day: 'Wednesday', period: 1, startTime: '08:00', endTime: '08:45', subject: 'History',      teacher: 'Meena Iyer' },
  { day: 'Thursday',  period: 2, startTime: '08:50', endTime: '09:35', subject: 'CS',           teacher: 'Vikram Singh' },
  { day: 'Friday',    period: 1, startTime: '08:00', endTime: '08:45', subject: 'Physics',      teacher: 'Anita Verma' },
];

// ─── Parent-specific ───────────────────────────────────────────────────────────
export const parentChildren = [
  { ...students[0], attendancePercent: 92, pendingFees: 12000 },
  { ...students[4], attendancePercent: 88, pendingFees: 0 },
];

export const childAttendance = myAttendance;
export const childResults    = myResults;
export const childFees       = myFees;

// ─── Student fees by student ───────────────────────────────────────────────────
export const studentFeesByStudent = myFees;
