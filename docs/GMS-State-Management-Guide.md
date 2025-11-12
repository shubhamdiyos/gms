# GMS Frontend State Management Guide

## Redux Store Structure

### Store Organization
The Redux store will be organized using Redux Toolkit with a slice-based approach. Each major feature will have its own slice to maintain modularity and scalability.

```javascript
// store/index.js
import { configureStore } from '@reduxjs/toolkit';
import authReducer from './slices/authSlice';
import schoolReducer from './slices/schoolSlice';
import userReducer from './slices/userSlice';
import classroomReducer from './slices/classroomSlice';
import studentReducer from './slices/studentSlice';
import employeeReducer from './slices/employeeSlice';
import attendanceReducer from './slices/attendanceSlice';
import examReducer from './slices/examSlice';
import resultReducer from './slices/resultSlice';
import feeReducer from './slices/feeSlice';
import paymentReducer from './slices/paymentSlice';
import uiReducer from './slices/uiSlice';

export const store = configureStore({
  reducer: {
    auth: authReducer,
    schools: schoolReducer,
    users: userReducer,
    classrooms: classroomReducer,
    students: studentReducer,
    employees: employeeReducer,
    attendance: attendanceReducer,
    exams: examReducer,
    results: resultReducer,
    fees: feeReducer,
    payments: paymentReducer,
    ui: uiReducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({
      serializableCheck: {
        ignoredActions: ['persist/PERSIST'],
      },
    }),
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
```

## Authentication State (authSlice)

### State Structure
```javascript
interface AuthState {
  user: {
    id: number;
    username: string;
    email: string;
    fullName: string;
    roles: string[];
    schoolId: number | null;
    employeeId: number | null;
  } | null;
  token: string | null;
  isAuthenticated: boolean;
  loading: boolean;
  error: string | null;
  requirePasswordChange: boolean;
}
```

### Actions
1. **login**: Handle user login with email/username and password
2. **logout**: Clear authentication state and redirect to login
3. **refreshToken**: Refresh JWT token when expired
4. **changePassword**: Handle password change flow
5. **selfRegister**: Handle employee self-registration
6. **clearError**: Clear authentication errors

### Selectors
1. **selectCurrentUser**: Get current authenticated user
2. **selectIsAuthenticated**: Check if user is authenticated
3. **selectUserRoles**: Get user roles
4. **selectRequirePasswordChange**: Check if password change is required
5. **selectAuthLoading**: Get authentication loading state
6. **selectAuthError**: Get authentication error

## School Management State (schoolSlice)

### State Structure
```javascript
interface SchoolState {
  schools: School[];
  currentSchool: School | null;
  loading: boolean;
  error: string | null;
  pagination: {
    page: number;
    limit: number;
    total: number;
  };
  filters: {
    search: string;
    status: string;
  };
}
```

### Actions
1. **fetchSchools**: Fetch list of schools with pagination and filters
2. **fetchSchoolById**: Fetch specific school details
3. **createSchool**: Create new school
4. **updateSchool**: Update existing school
5. **toggleSchoolStatus**: Enable/disable school
6. **setFilters**: Update school list filters
7. **setCurrentPage**: Update pagination

### Selectors
1. **selectSchools**: Get all schools in state
2. **selectCurrentSchool**: Get currently viewed school
3. **selectSchoolLoading**: Get school loading state
4. **selectSchoolError**: Get school error
5. **selectSchoolPagination**: Get pagination info
6. **selectSchoolFilters**: Get current filters

## User Management State (userSlice)

### State Structure
```javascript
interface UserState {
  users: User[];
  currentUser: User | null;
  loading: boolean;
  error: string | null;
  pagination: {
    page: number;
    limit: number;
    total: number;
  };
  filters: {
    search: string;
    role: string;
    status: string;
  };
}
```

### Actions
1. **fetchUsers**: Fetch list of users with pagination and filters
2. **fetchUserById**: Fetch specific user details
3. **createUser**: Create new user
4. **updateUser**: Update existing user
5. **updateCurrentUser**: Update current user's profile
6. **toggleUserStatus**: Enable/disable user
7. **setFilters**: Update user list filters
8. **setCurrentPage**: Update pagination

### Selectors
1. **selectUsers**: Get all users in state
2. **selectCurrentUserProfile**: Get current user's profile
3. **selectUserLoading**: Get user loading state
4. **selectUserError**: Get user error
5. **selectUserPagination**: Get pagination info
6. **selectUserFilters**: Get current filters

## Classroom Management State (classroomSlice)

### State Structure
```javascript
interface ClassroomState {
  classrooms: Classroom[];
  currentClassroom: Classroom | null;
  loading: boolean;
  error: string | null;
  pagination: {
    page: number;
    limit: number;
    total: number;
  };
  filters: {
    search: string;
    status: string;
  };
}
```

### Actions
1. **fetchClassrooms**: Fetch list of classrooms with pagination and filters
2. **fetchClassroomById**: Fetch specific classroom details
3. **createClassroom**: Create new classroom
4. **updateClassroom**: Update existing classroom
5. **toggleClassroomStatus**: Enable/disable classroom
6. **fetchAssignedClassrooms**: Fetch classrooms assigned to current teacher
7. **setFilters**: Update classroom list filters
8. **setCurrentPage**: Update pagination

### Selectors
1. **selectClassrooms**: Get all classrooms in state
2. **selectCurrentClassroom**: Get currently viewed classroom
3. **selectClassroomLoading**: Get classroom loading state
4. **selectClassroomError**: Get classroom error
5. **selectClassroomPagination**: Get pagination info
6. **selectClassroomFilters**: Get current filters
7. **selectAssignedClassrooms**: Get classrooms assigned to current teacher

## Student Management State (studentSlice)

### State Structure
```javascript
interface StudentState {
  students: Student[];
  currentStudent: Student | null;
  admissions: StudentAdmission[];
  loading: boolean;
  error: string | null;
  pagination: {
    page: number;
    limit: number;
    total: number;
  };
  filters: {
    search: string;
    classroomId: number | null;
    sectionId: number | null;
    status: string;
  };
}
```

### Actions
1. **fetchStudents**: Fetch list of students with pagination and filters
2. **fetchStudentById**: Fetch specific student details
3. **createStudent**: Create new student
4. **updateStudent**: Update existing student
5. **toggleStudentStatus**: Enable/disable student
6. **fetchAdmissions**: Fetch student admissions
7. **fetchStudentProfile**: Fetch current student's profile
8. **setFilters**: Update student list filters
9. **setCurrentPage**: Update pagination

### Selectors
1. **selectStudents**: Get all students in state
2. **selectCurrentStudent**: Get currently viewed student
3. **selectStudentAdmissions**: Get student admissions
4. **selectStudentProfile**: Get current student's profile
5. **selectStudentLoading**: Get student loading state
6. **selectStudentError**: Get student error
7. **selectStudentPagination**: Get pagination info
8. **selectStudentFilters**: Get current filters

## Employee Management State (employeeSlice)

### State Structure
```javascript
interface EmployeeState {
  employees: Employee[];
  currentEmployee: Employee | null;
  loading: boolean;
  error: string | null;
  pagination: {
    page: number;
    limit: number;
    total: number;
  };
  filters: {
    search: string;
    department: string;
    designation: string;
    status: string;
  };
}
```

### Actions
1. **fetchEmployees**: Fetch list of employees with pagination and filters
2. **fetchEmployeeById**: Fetch specific employee details
3. **createEmployee**: Create new employee
4. **updateEmployee**: Update existing employee
5. **toggleEmployeeStatus**: Enable/disable employee
6. **setFilters**: Update employee list filters
7. **setCurrentPage**: Update pagination

### Selectors
1. **selectEmployees**: Get all employees in state
2. **selectCurrentEmployee**: Get currently viewed employee
3. **selectEmployeeLoading**: Get employee loading state
4. **selectEmployeeError**: Get employee error
5. **selectEmployeePagination**: Get pagination info
6. **selectEmployeeFilters**: Get current filters

## Attendance Management State (attendanceSlice)

### State Structure
```javascript
interface AttendanceState {
  attendanceRecords: Attendance[];
  currentRecord: Attendance | null;
  loading: boolean;
  error: string | null;
  filters: {
    startDate: string;
    endDate: string;
    studentId: number | null;
    classroomId: number | null;
  };
  summary: {
    present: number;
    absent: number;
    late: number;
    holiday: number;
  };
}
```

### Actions
1. **fetchAttendance**: Fetch attendance records with filters
2. **recordAttendance**: Record individual attendance
3. **recordBulkAttendance**: Record bulk attendance
4. **updateAttendance**: Update existing attendance record
5. **deleteAttendance**: Delete attendance record
6. **setFilters**: Update attendance filters
7. **fetchAttendanceSummary**: Fetch attendance summary

### Selectors
1. **selectAttendanceRecords**: Get attendance records
2. **selectCurrentAttendance**: Get current attendance record
3. **selectAttendanceLoading**: Get attendance loading state
4. **selectAttendanceError**: Get attendance error
5. **selectAttendanceFilters**: Get current filters
6. **selectAttendanceSummary**: Get attendance summary

## Exam Management State (examSlice)

### State Structure
```javascript
interface ExamState {
  exams: Exam[];
  currentExam: Exam | null;
  loading: boolean;
  error: string | null;
  pagination: {
    page: number;
    limit: number;
    total: number;
  };
  filters: {
    search: string;
    academicYear: string;
    type: string;
    status: string;
  };
}
```

### Actions
1. **fetchExams**: Fetch list of exams with pagination and filters
2. **fetchExamById**: Fetch specific exam details
3. **createExam**: Create new exam
4. **updateExam**: Update existing exam
5. **deleteExam**: Delete exam
6. **setFilters**: Update exam list filters
7. **setCurrentPage**: Update pagination

### Selectors
1. **selectExams**: Get all exams in state
2. **selectCurrentExam**: Get currently viewed exam
3. **selectExamLoading**: Get exam loading state
4. **selectExamError**: Get exam error
5. **selectExamPagination**: Get pagination info
6. **selectExamFilters**: Get current filters

## Result Management State (resultSlice)

### State Structure
```javascript
interface ResultState {
  results: Result[];
  currentResult: Result | null;
  loading: boolean;
  error: string | null;
  filters: {
    examId: number | null;
    studentId: number | null;
    subjectId: number | null;
  };
  gradeBook: {
    studentId: number;
    results: Result[];
  }[];
}
```

### Actions
1. **fetchResults**: Fetch results with filters
2. **fetchResultById**: Fetch specific result details
3. **createResult**: Create new result
4. **updateResult**: Update existing result
5. **deleteResult**: Delete result
6. **setFilters**: Update result filters
7. **fetchGradeBook**: Fetch grade book for student

### Selectors
1. **selectResults**: Get results
2. **selectCurrentResult**: Get current result
3. **selectResultLoading**: Get result loading state
4. **selectResultError**: Get result error
5. **selectResultFilters**: Get current filters
6. **selectGradeBook**: Get grade book data

## Fee Management State (feeSlice)

### State Structure
```javascript
interface FeeState {
  fees: Fee[];
  currentFee: Fee | null;
  studentFees: StudentFee[];
  currentStudentFee: StudentFee | null;
  loading: boolean;
  error: string | null;
  pagination: {
    page: number;
    limit: number;
    total: number;
  };
  filters: {
    search: string;
    academicYear: string;
    category: string;
    classGrade: string;
  };
  summary: {
    totalAmount: number;
    collectedAmount: number;
    outstandingAmount: number;
  };
}
```

### Actions
1. **fetchFees**: Fetch list of fees with pagination and filters
2. **fetchFeeById**: Fetch specific fee details
3. **createFee**: Create new fee
4. **updateFee**: Update existing fee
5. **deleteFee**: Delete fee
6. **fetchStudentFees**: Fetch student fees
7. **fetchFeeSummary**: Fetch fee summary by academic year
8. **setFilters**: Update fee list filters
9. **setCurrentPage**: Update pagination

### Selectors
1. **selectFees**: Get all fees in state
2. **selectCurrentFee**: Get currently viewed fee
3. **selectStudentFees**: Get student fees
4. **selectFeeLoading**: Get fee loading state
5. **selectFeeError**: Get fee error
6. **selectFeePagination**: Get pagination info
7. **selectFeeFilters**: Get current filters
8. **selectFeeSummary**: Get fee summary

## Payment Management State (paymentSlice)

### State Structure
```javascript
interface PaymentState {
  payments: FeePayment[];
  currentPayment: FeePayment | null;
  loading: boolean;
  error: string | null;
  pagination: {
    page: number;
    limit: number;
    total: number;
  };
  filters: {
    startDate: string;
    endDate: string;
    studentId: number | null;
    paymentMethod: string;
  };
  summary: {
    totalPayments: number;
    paymentCount: number;
  };
}
```

### Actions
1. **fetchPayments**: Fetch list of payments with pagination and filters
2. **fetchPaymentById**: Fetch specific payment details
3. **createPayment**: Create new payment
4. **updatePayment**: Update existing payment
5. **deletePayment**: Delete payment
6. **fetchPaymentSummary**: Fetch payment summary by date range
7. **setFilters**: Update payment list filters
8. **setCurrentPage**: Update pagination

### Selectors
1. **selectPayments**: Get all payments in state
2. **selectCurrentPayment**: Get currently viewed payment
3. **selectPaymentLoading**: Get payment loading state
4. **selectPaymentError**: Get payment error
5. **selectPaymentPagination**: Get pagination info
6. **selectPaymentFilters**: Get current filters
7. **selectPaymentSummary**: Get payment summary

## UI State (uiSlice)

### State Structure
```javascript
interface UIState {
  notifications: {
    id: string;
    type: 'success' | 'error' | 'warning' | 'info';
    message: string;
    duration: number;
  }[];
  loading: {
    global: boolean;
    [key: string]: boolean;
  };
  modals: {
    [key: string]: {
      open: boolean;
      data?: any;
    };
  };
  sidebar: {
    open: boolean;
  };
  theme: 'light' | 'dark';
}
```

### Actions
1. **showNotification**: Show toast notification
2. **hideNotification**: Hide specific notification
3. **setLoading**: Set loading state for specific action
4. **openModal**: Open modal with optional data
5. **closeModal**: Close specific modal
6. **toggleSidebar**: Toggle sidebar visibility
7. **setTheme**: Set application theme

### Selectors
1. **selectNotifications**: Get all notifications
2. **selectLoadingState**: Get loading state
3. **selectModalState**: Get modal state
4. **selectSidebarOpen**: Get sidebar open state
5. **selectTheme**: Get current theme

## State Persistence

### Persistence Configuration
```javascript
// store/persistence.js
import { persistReducer } from 'redux-persist';
import storage from 'redux-persist/lib/storage';

const authPersistConfig = {
  key: 'auth',
  storage,
  whitelist: ['token', 'isAuthenticated', 'requirePasswordChange']
};

const uiPersistConfig = {
  key: 'ui',
  storage,
  whitelist: ['theme']
};

export const persistedAuthReducer = persistReducer(authPersistConfig, authReducer);
export const persistedUIReducer = persistReducer(uiPersistConfig, uiReducer);
```

## Performance Optimization

### Memoization
```javascript
// hooks/useMemoizedSelectors.js
import { createSelector } from '@reduxjs/toolkit';

export const selectFilteredSchools = createSelector(
  [selectSchools, selectSchoolFilters],
  (schools, filters) => {
    return schools.filter(school => {
      // Apply filters
      return true;
    });
  }
);
```

### Code Splitting
```javascript
// slices/lazySlice.js
import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';

// Lazy-loaded slice for less frequently used features
export const lazySlice = createSlice({
  name: 'lazy',
  initialState: {},
  reducers: {},
  extraReducers: (builder) => {
    // Handle async actions
  }
});
```

## Error Handling

### Global Error Middleware
```javascript
// middleware/errorHandler.js
import { showNotification } from '../slices/uiSlice';

export const errorHandlingMiddleware = (store) => (next) => (action) => {
  if (action.type.endsWith('/rejected')) {
    const error = action.error.message;
    store.dispatch(showNotification({
      type: 'error',
      message: error,
      duration: 5000
    }));
  }
  return next(action);
};
```

## Testing Strategy

### Slice Testing
```javascript
// slices/__tests__/authSlice.test.js
import authSlice, { login, logout } from '../authSlice';

describe('authSlice', () => {
  const initialState = {
    user: null,
    token: null,
    isAuthenticated: false,
    loading: false,
    error: null,
    requirePasswordChange: false
  };

  it('should handle login', () => {
    const userData = { /* user data */ };
    const action = { type: login.fulfilled.type, payload: userData };
    const state = authSlice.reducer(initialState, action);
    expect(state.isAuthenticated).toBe(true);
  });

  it('should handle logout', () => {
    const action = { type: logout.type };
    const state = authSlice.reducer(initialState, action);
    expect(state.user).toBeNull();
    expect(state.token).toBeNull();
    expect(state.isAuthenticated).toBe(false);
  });
});
```

This state management guide provides a comprehensive structure for managing the application state in the GMS frontend, ensuring consistency, scalability, and maintainability across all features.