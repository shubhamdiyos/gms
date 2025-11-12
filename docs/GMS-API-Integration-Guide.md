# GMS Frontend API Integration Guide

## Authentication Flow

### Login Process
1. **Endpoint**: POST /api/v1/auth/login
2. **Request Payload**:
   ```json
   {
     "username": "string",
     "password": "string"
   }
   ```
3. **Response**:
   ```json
   {
     "accessToken": "string",
     "tokenType": "Bearer",
     "expiresIn": "integer",
     "requirePasswordChange": "boolean"
   }
   ```
4. **Implementation**:
   - Validate username format and password length
   - Handle 401 responses for invalid credentials
   - Store JWT token in HTTP-only cookie or localStorage
   - Redirect based on user roles
   - If requirePasswordChange is true, redirect to password change page

### Password Change
1. **Endpoint**: POST /api/v1/auth/change-password
2. **Request Payload**:
   ```json
   {
     "oldPassword": "string",
     "newPassword": "string"
   }
   ```
3. **Response**: 200 OK or error status
4. **Implementation**:
   - Validate old password against server
   - Apply password strength rules for new password
   - Update requirePasswordChange flag in user state
   - Redirect to dashboard after successful change

### Employee Self-Registration
1. **Endpoint**: POST /api/v1/auth/self-register
2. **Request Payload**:
   ```json
   {
     "empId": "integer",
     "schoolId": "integer",
     "username": "string",
     "password": "string"
   }
   ```
3. **Response**: 200 OK or error status
4. **Implementation**:
   - Validate employee exists and belongs to school
   - Check username uniqueness
   - Apply password strength validation
   - Show success message and redirect to login

## School Management

### Create School
1. **Endpoint**: POST /api/v1/schools/create
2. **Request Payload**:
   ```json
   {
     "schoolName": "string",
     "schoolCode": "string",
     "address": "string",
     "phone": "string",
     "email": "string",
     "principalName": "string",
     "establishedYear": "integer",
     "boardAffiliation": "string",
     "adminFullName": "string",
     "adminEmail": "string"
   }
   ```
3. **Response**:
   ```json
   {
     "id": "integer",
     "schoolId": "string",
     "schoolName": "string",
     "schoolCode": "string",
     "status": "string",
     "enabled": "boolean"
   }
   ```
4. **Implementation**:
   - Validate school code uniqueness
   - Validate email formats
   - Apply length restrictions on text fields
   - Show success message with created school details

### Update School
1. **Endpoint**: PUT /api/v1/schools/update
2. **Request Payload**: Same as create, but without admin fields
3. **Response**: Same as create
4. **Implementation**:
   - Only allow updating of non-admin fields
   - Validate data before submission
   - Show success message after update

### List Schools
1. **Endpoint**: GET /api/v1/schools
2. **Response**:
   ```json
   [
     {
       "id": "integer",
       "schoolName": "string",
       "schoolCode": "string",
       "address": "string",
       "phone": "string",
       "email": "string",
       "principalName": "string",
       "establishedYear": "integer",
       "boardAffiliation": "string",
       "status": "string",
       "enabled": "boolean"
     }
   ]
   ```
3. **Implementation**:
   - Implement client-side filtering and search
   - Pagination for large datasets
   - Sorting by different fields

### Get School by ID
1. **Endpoint**: GET /api/v1/schools/{id}
2. **Response**: Single school object
3. **Implementation**:
   - Detailed view of school information
   - Links to related entities (users, classrooms, etc.)

### Toggle School Status
1. **Endpoint**: PATCH /api/v1/schools/toggle
2. **Query Parameters**:
   - id: integer
   - isActive: boolean
3. **Response**: Updated school object
4. **Implementation**:
   - Confirmation dialog before toggling
   - Update UI immediately with optimistic update
   - Handle errors and rollback if needed

## User Management

### Create User
1. **Endpoint**: POST /api/v1/users/create
2. **Request Payload**:
   ```json
   {
     "username": "string",
     "email": "string",
     "password": "string",
     "fullName": "string",
     "phoneNumber": "string",
     "roles": ["string"]
   }
   ```
3. **Response**:
   ```json
   {
     "id": "integer",
     "username": "string",
     "email": "string",
     "fullName": "string",
     "phoneNumber": "string",
     "roles": ["string"],
     "enabled": "boolean",
     "requirePasswordChange": "boolean"
   }
   ```
4. **Implementation**:
   - Role selection with multi-select component
   - Password strength validation
   - Email uniqueness validation
   - Success message with user details

### Update User
1. **Endpoint**: PUT /api/v1/users/update
2. **Request Payload**: Same as create but with id field
3. **Response**: Updated user object
4. **Implementation**:
   - Prevent changing email/username for some roles
   - Conditional form fields based on user role
   - Password field only when explicitly updating password

### List Users
1. **Endpoint**: GET /api/v1/users
2. **Response**: Array of user objects
3. **Implementation**:
   - Filter by role, status, school
   - Search by name, email, username
   - Pagination for performance
   - Bulk actions (enable/disable users)

### Get Current User Profile
1. **Endpoint**: GET /api/v1/users/me
2. **Response**: User object with additional details
3. **Implementation**:
   - Display user profile information
   - Edit profile functionality
   - Change password option

### Update Current User Profile
1. **Endpoint**: PUT /api/v1/users/me
2. **Request Payload**:
   ```json
   {
     "fullName": "string",
     "phoneNumber": "string"
   }
   ```
3. **Response**: Updated user object
4. **Implementation**:
   - Limited field updates for self-editing
   - Real-time validation
   - Success feedback

## Classroom Management

### Create Classroom
1. **Endpoint**: POST /api/v1/classes/create
2. **Request Payload**:
   ```json
   {
     "className": "string",
     "classCode": "string",
     "description": "string",
     "capacity": "integer"
   }
   ```
3. **Response**:
   ```json
   {
     "id": "integer",
     "className": "string",
     "classCode": "string",
     "description": "string",
     "capacity": "integer",
     "status": "string",
     "enabled": "boolean"
   }
   ```
4. **Implementation**:
   - Validate class code uniqueness
   - Capacity validation (positive integer)
   - Success message with classroom details

### Update Classroom
1. **Endpoint**: PUT /api/v1/classes/update
2. **Request Payload**: Same as create but with id field
3. **Response**: Updated classroom object
4. **Implementation**:
   - Prevent changing class code after creation
   - Real-time validation
   - Success feedback

### List Classrooms
1. **Endpoint**: GET /api/v1/classes
2. **Response**: Array of classroom objects
3. **Implementation**:
   - Filter by status, capacity
   - Search by name, code
   - Sort by name, capacity
   - Display capacity utilization

### Get Classroom by ID
1. **Endpoint**: GET /api/v1/classes/{id}
2. **Response**: Single classroom object
3. **Implementation**:
   - Detailed view with related information
   - Links to students, subjects, timetable

### Toggle Classroom Status
1. **Endpoint**: PATCH /api/v1/classes/toggle
2. **Query Parameters**:
   - id: integer
   - isActive: boolean
3. **Response**: Updated classroom object
4. **Implementation**:
   - Confirmation for active classrooms with students
   - Optimistic update with rollback on error

### Get Assigned Classrooms for Teacher
1. **Endpoint**: GET /api/v1/classes/teacher/assigned
2. **Response**: Array of classroom response objects
3. **Implementation**:
   - Teacher-specific classroom list
   - Links to class rosters and timetable

## Student Management

### Create Student
1. **Endpoint**: POST /api/v1/students/create
2. **Request Payload**:
   ```json
   {
     "firstName": "string",
     "lastName": "string",
     "email": "string",
     "phoneNumber": "string",
     "dateOfBirth": "string (date)",
     "gender": "string",
     "classroomId": "integer",
     "sectionId": "integer"
   }
   ```
3. **Response**:
   ```json
   {
     "id": "integer",
     "firstName": "string",
     "lastName": "string",
     "email": "string",
     "phoneNumber": "string",
     "dateOfBirth": "string (date)",
     "gender": "string",
     "classroomId": "integer",
     "sectionId": "integer",
     "status": "string",
     "enabled": "boolean"
   }
   ```
4. **Implementation**:
   - Date picker for date of birth
   - Gender selection dropdown
   - Classroom and section selection
   - Email uniqueness validation

### Update Student
1. **Endpoint**: PUT /api/v1/students/update
2. **Request Payload**: Same as create but with id field
3. **Response**: Updated student object
4. **Implementation**:
   - Conditional fields based on user role
   - Real-time validation
   - Success feedback

### List Students
1. **Endpoint**: GET /api/v1/students
2. **Response**: Array of student objects
3. **Implementation**:
   - Filter by class, section, status
   - Search by name, email
   - Pagination for large datasets
   - Export to CSV functionality

### List Student Admissions
1. **Endpoint**: GET /api/v1/students/admissions
2. **Response**: Array of student admission objects
3. **Implementation**:
   - Admission workflow tracking
   - Status indicators
   - Links to student profiles

### Toggle Student Status
1. **Endpoint**: PATCH /api/v1/students/toggle
2. **Query Parameters**:
   - id: integer
   - isActive: boolean
3. **Response**: Updated student object
4. **Implementation**:
   - Confirmation dialog for deactivation
   - Optimistic update
   - Related data cleanup notifications

### Get Student Profile
1. **Endpoint**: GET /api/v1/students/profile
2. **Response**: Student response object
3. **Implementation**:
   - Student-specific dashboard
   - Academic information display
   - Links to related features (attendance, results, fees)

## Attendance Management

### Record Individual Attendance
1. **Endpoint**: POST /api/v1/attendance
2. **Request Payload**:
   ```json
   {
     "studentId": "integer",
     "date": "string (date)",
     "status": "string",
     "remarks": "string"
   }
   ```
3. **Response**:
   ```json
   {
     "id": "integer",
     "studentId": "integer",
     "date": "string (date)",
     "status": "string",
     "remarks": "string"
   }
   ```
4. **Implementation**:
   - Date picker with default to today
   - Status selection (Present, Absent, Late, Holiday)
   - Remarks field for additional information
   - Success feedback

### Record Bulk Attendance
1. **Endpoint**: POST /api/v1/attendance/bulk
2. **Request Payload**:
   ```json
   {
     "classroomId": "integer",
     "date": "string (date)",
     "attendanceItems": [
       {
         "studentId": "integer",
         "status": "string"
       }
     ]
   }
   ```
3. **Response**: Array of attendance response objects
4. **Implementation**:
   - Classroom selection
   - Student list with checkboxes
   - Quick selection buttons (All Present, All Absent)
   - Date picker
   - Success feedback with count

### Update Attendance
1. **Endpoint**: PUT /api/v1/attendance/{id}
2. **Request Payload**: Same as individual attendance
3. **Response**: Updated attendance object
4. **Implementation**:
   - Edit existing attendance records
   - Audit trail display
   - Confirmation for changes

### Delete Attendance
1. **Endpoint**: DELETE /api/v1/attendance/{id}
2. **Response**: 204 No Content
3. **Implementation**:
   - Confirmation dialog
   - Success feedback
   - List refresh

### Get Attendance for Student
1. **Endpoint**: GET /api/v1/attendance/student/{studentId}
2. **Query Parameters**:
   - from: date
   - to: date
3. **Response**: Array of attendance objects
4. **Implementation**:
   - Date range picker
   - Monthly calendar view
   - Summary statistics
   - Export functionality

### Get Attendance for Classroom
1. **Endpoint**: GET /api/v1/attendance/classroom/{classroomId}
2. **Query Parameters**:
   - date: date
3. **Response**: Array of attendance objects
4. **Implementation**:
   - Daily attendance view
   - Student list with status indicators
   - Quick edit functionality

### Get Attendance by Date Range
1. **Endpoint**: GET /api/v1/attendance/range
2. **Query Parameters**:
   - startDate: date
   - endDate: date
3. **Response**: Array of attendance objects
4. **Implementation**:
   - School-wide attendance report
   - Filtering by class, status
   - Chart visualization
   - Export to spreadsheet

### Get Attendance by Student and Date Range
1. **Endpoint**: GET /api/v1/attendance/student/{studentId}/range
2. **Query Parameters**:
   - startDate: date
   - endDate: date
3. **Response**: Array of attendance objects
4. **Implementation**:
   - Detailed student attendance history
   - Attendance percentage calculation
   - Pattern analysis

## Exam Management

### Create Exam
1. **Endpoint**: POST /api/v1/exams
2. **Request Payload**:
   ```json
   {
     "name": "string",
     "type": "string",
     "academicYear": "string",
     "startDate": "string (date)",
     "endDate": "string (date)",
     "maxMarks": "number"
   }
   ```
3. **Response**:
   ```json
   {
     "id": "integer",
     "name": "string",
     "type": "string",
     "academicYear": "string",
     "startDate": "string (date)",
     "endDate": "string (date)",
     "maxMarks": "number",
     "status": "string"
   }
   ```
4. **Implementation**:
   - Exam type selection (Midterm, Final, etc.)
   - Date range picker with validation
   - Academic year selection
   - Success feedback

### Update Exam
1. **Endpoint**: PUT /api/v1/exams/{id}
2. **Request Payload**: Same as create
3. **Response**: Updated exam object
4. **Implementation**:
   - Prevent changes after student registration
   - Real-time validation
   - Success feedback

### Delete Exam
1. **Endpoint**: DELETE /api/v1/exams/{id}
2. **Response**: 204 No Content
3. **Implementation**:
   - Confirmation dialog with impact warning
   - Check for dependencies (registrations, results)
   - Success feedback

### List Exams
1. **Endpoint**: GET /api/v1/exams
2. **Response**: Array of exam objects
3. **Implementation**:
   - Filter by academic year, type, status
   - Search by name
   - Sort by date
   - Status indicators

### Get Exam by ID
1. **Endpoint**: GET /api/v1/exams/{id}
2. **Response**: Single exam object
3. **Implementation**:
   - Detailed exam information
   - Links to related entities (subjects, students)
   - Action buttons (edit, delete, schedule)

### Get Exams by Academic Year
1. **Endpoint**: GET /api/v1/exams/academic-year
2. **Query Parameters**:
   - academicYear: string
3. **Response**: Array of exam objects
4. **Implementation**:
   - Academic year filter
   - Timeline visualization
   - Quick navigation

## Financial Management

### Create Fee
1. **Endpoint**: POST /api/v1/fees
2. **Request Payload**:
   ```json
   {
     "name": "string",
     "description": "string",
     "category": "string",
     "academicYear": "string",
     "classGrade": "string",
     "amount": "number",
     "mandatory": "boolean"
   }
   ```
3. **Response**:
   ```json
   {
     "id": "integer",
     "name": "string",
     "description": "string",
     "category": "string",
     "academicYear": "string",
     "classGrade": "string",
     "amount": "number",
     "mandatory": "boolean",
     "status": "string"
   }
   ```
4. **Implementation**:
   - Fee category selection
   - Academic year and class selection
   - Mandatory flag toggle
   - Amount validation
   - Success feedback

### Update Fee
1. **Endpoint**: PUT /api/v1/fees/{id}
2. **Request Payload**: Same as create
3. **Response**: Updated fee object
4. **Implementation**:
   - Prevent changes after student assignment
   - Real-time validation
   - Success feedback

### Delete Fee
1. **Endpoint**: DELETE /api/v1/fees/{id}
2. **Response**: 204 No Content
3. **Implementation**:
   - Confirmation dialog with impact warning
   - Check for dependencies (student fees, payments)
   - Success feedback

### List Fees
1. **Endpoint**: GET /api/v1/fees
2. **Response**: Array of fee objects
3. **Implementation**:
   - Filter by academic year, category, class
   - Search by name
   - Sort by amount, date
   - Status indicators

### Get Fee by ID
1. **Endpoint**: GET /api/v1/fees/{id}
2. **Response**: Single fee object
3. **Implementation**:
   - Detailed fee information
   - Links to related entities (student fees)
   - Action buttons (edit, delete)

### Get Fees by Academic Year
1. **Endpoint**: GET /api/v1/fees/academic-year
2. **Query Parameters**:
   - academicYear: string
3. **Response**: Array of fee objects
4. **Implementation**:
   - Academic year filter
   - Summary by category
   - Export functionality

### Get Total Fee Amount by Academic Year
1. **Endpoint**: GET /api/v1/fees/total-amount
2. **Query Parameters**:
   - academicYear: string
3. **Response**: Number (total amount)
4. **Implementation**:
   - Financial summary display
   - Comparison with previous years
   - Visualization

### Create Fee Payment
1. **Endpoint**: POST /api/v1/fee-payments
2. **Request Payload**:
   ```json
   {
     "studentFeeId": "integer",
     "amount": "number",
     "paymentDate": "string (date)",
     "paymentMethod": "string",
     "transactionId": "string",
     "remarks": "string"
   }
   ```
3. **Response**:
   ```json
   {
     "id": "integer",
     "studentFeeId": "integer",
     "amount": "number",
     "paymentDate": "string (date)",
     "paymentMethod": "string",
     "transactionId": "string",
     "remarks": "string",
     "status": "string"
   }
   ```
4. **Implementation**:
   - Student fee selection
   - Payment method selection
   - Amount validation (not exceeding balance)
   - Transaction ID field
   - Success feedback

### Update Fee Payment
1. **Endpoint**: PUT /api/v1/fee-payments/{id}
2. **Request Payload**: Same as create
3. **Response**: Updated fee payment object
4. **Implementation**:
   - Limited edit capability
   - Audit trail display
   - Success feedback

### Delete Fee Payment
1. **Endpoint**: DELETE /api/v1/fee-payments/{id}
2. **Response**: 204 No Content
3. **Implementation**:
   - Confirmation dialog
   - Impact warning
   - Success feedback

### List Fee Payments
1. **Endpoint**: GET /api/v1/fee-payments
2. **Response**: Array of fee payment objects
3. **Implementation**:
   - Filter by date range, method, status
   - Search by student name, transaction ID
   - Sort by date, amount
   - Export functionality

### Get Fee Payment by ID
1. **Endpoint**: GET /api/v1/fee-payments/{id}
2. **Response**: Single fee payment object
3. **Implementation**:
   - Detailed payment information
   - Links to related entities (student fee)
   - Receipt generation option

### Get Fee Payments by Student
1. **Endpoint**: GET /api/v1/fee-payments/student/{studentId}
2. **Response**: Array of fee payment objects
3. **Implementation**:
   - Student-specific payment history
   - Balance calculation
   - Payment trend visualization

### Get Fee Payments by Date Range
1. **Endpoint**: GET /api/v1/fee-payments/date-range
2. **Query Parameters**:
   - startDate: date
   - endDate: date
3. **Response**: Array of fee payment objects
4. **Implementation**:
   - School-wide payment report
   - Summary by payment method
   - Export functionality

### Get Total Payments by Date Range
1. **Endpoint**: GET /api/v1/fee-payments/total-payments
2. **Query Parameters**:
   - startDate: date
   - endDate: date
3. **Response**: Number (total amount)
4. **Implementation**:
   - Financial dashboard metric
   - Trend analysis
   - Comparison with targets

### Get Count of Payments by Date
1. **Endpoint**: GET /api/v1/fee-payments/payment-count
2. **Query Parameters**:
   - paymentDate: date
3. **Response**: Number (count)
4. **Implementation**:
   - Daily transaction count
   - Visualization
   - Peak day identification

## HTTP Client Implementation

### Axios Configuration
```javascript
// services/httpClient.js
import axios from 'axios';

const httpClient = axios.create({
  baseURL: process.env.REACT_APP_API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor
httpClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('authToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor
httpClient.interceptors.response.use(
  (response) => {
    return response;
  },
  (error) => {
    if (error.response?.status === 401) {
      // Handle unauthorized access
      localStorage.removeItem('authToken');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default httpClient;
```

### Error Handling
1. **Network Errors**: Display user-friendly messages
2. **400 Errors**: Display validation errors
3. **401 Errors**: Redirect to login
4. **403 Errors**: Show permission denied message
5. **404 Errors**: Show not found message
6. **500 Errors**: Show server error message

### Loading States
1. **Global Loading**: Show spinner for all requests
2. **Component Loading**: Show skeleton loaders for specific components
3. **Button Loading**: Show loading state on action buttons
4. **Form Loading**: Disable form during submission

### Retry Logic
1. **Automatic Retry**: Retry failed requests 3 times
2. **Exponential Backoff**: Increase delay between retries
3. **Selective Retry**: Only retry GET requests automatically

This API integration guide provides comprehensive details for implementing the frontend components that interact with the GMS backend API, ensuring a seamless user experience with proper error handling, loading states, and validation.