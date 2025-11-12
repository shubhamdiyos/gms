# GMS (Global/School Management System) - Frontend Development Guide

## Project Overview

GMS is a comprehensive School Management System built with a Spring Boot backend and PostgreSQL database. The system follows a multi-tenant architecture where all data operations are school-bounded for tenant isolation. It supports 5 user roles: SUPERADMIN, ADMIN, TEACHER, STUDENT, and PARENT, each with specific permissions and access levels.

This guide provides detailed instructions for building a modern, responsive, and attractive React.js frontend that integrates seamlessly with the existing backend API.

## Technology Stack

### Frontend Framework
- **React.js** (v18+) with TypeScript
- **State Management**: Redux Toolkit
- **UI Library**: Material-UI (MUI) or Tailwind CSS
- **Routing**: React Router v6
- **HTTP Client**: Axios
- **Form Handling**: React Hook Form with Yup validation
- **Charts**: Chart.js or Recharts
- **Build Tool**: Vite.js
- **Testing**: Jest, React Testing Library, Cypress

### Design System
- **Primary Color**: #1976d2 (Blue) - Trust and professionalism
- **Secondary Color**: #388e3c (Green) - Growth and education
- **Accent Color**: #f57c00 (Orange) - Highlights and calls-to-action
- **Neutral Colors**: #f5f5f5 (Light gray) for backgrounds, #212121 (Dark gray) for text
- **Typography**: Roboto font family
- **Spacing**: Consistent 8px grid system (8, 16, 24, 32px spacing)

## Project Structure

```
src/
├── assets/                 # Static assets (images, icons, etc.)
├── components/             # Reusable UI components
│   ├── common/             # Shared components (buttons, inputs, cards)
│   ├── layout/             # Layout components (header, sidebar, footer)
│   ├── forms/              # Form components
│   └── charts/             # Chart components
├── features/               # Feature-based modules
│   ├── auth/               # Authentication module
│   ├── dashboard/          # Dashboard components
│   ├── schools/            # School management
│   ├── students/           # Student management
│   ├── employees/          # Employee management
│   ├── attendance/         # Attendance tracking
│   ├── fees/               # Fee management
│   ├── exams/              # Exam management
│   └── communications/     # Notifications & announcements
├── hooks/                  # Custom React hooks
├── services/               # API service layer
├── store/                  # Redux store configuration
├── types/                  # TypeScript type definitions
├── utils/                  # Utility functions and helpers
├── constants/              # Application constants
└── App.tsx                 # Main application component
```

## Authentication & Security

### JWT Token Management
```typescript
// services/auth.service.ts
export class AuthService {
  private static TOKEN_KEY = 'gms_token';
  
  static setToken(token: string): void {
    localStorage.setItem(this.TOKEN_KEY, token);
  }
  
  static getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }
  
  static removeToken(): void {
    localStorage.removeItem(this.TOKEN_KEY);
  }
  
  static decodeToken(token: string): any {
    return jwt_decode(token);
  }
}
```

### API Client Setup
```typescript
// services/api.client.ts
const apiClient = axios.create({
  baseURL: 'http://localhost:8080/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor
apiClient.interceptors.request.use((config) => {
  const token = AuthService.getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      AuthService.removeToken();
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);
```

## Role-Based Dashboard Pages

### 1. SuperAdmin Dashboard (`/superadmin`)
**Key Features:**
- Schools overview with statistics
- Create new school functionality
- System-wide analytics
- Recent activities feed

**API Endpoints:**
- `GET /api/v1/schools` - List all schools
- `POST /api/v1/schools` - Create new school
- `PUT /api/v1/schools/{id}` - Update school
- `DELETE /api/v1/schools/{id}` - Delete school

### 2. Admin Dashboard (`/admin`)
**Key Features:**
- School overview dashboard
- Student/Employee management
- Academic structure setup
- Fee management

**API Endpoints:**
- `GET /api/v1/students` - List students
- `GET /api/v1/employees` - List employees
- `GET /api/v1/classrooms` - List classrooms
- `GET /api/v1/subjects` - List subjects

### 3. Teacher Dashboard (`/teacher`)
**Key Features:**
- My classes overview
- Attendance marking
- Grade entry
- Student progress tracking

**API Endpoints:**
- `GET /api/v1/teachers/profile` - Teacher profile
- `GET /api/v1/teachers/classes` - Assigned classes
- `POST /api/v1/attendance` - Mark attendance
- `GET /api/v1/teachers/students` - Class students

### 4. Student Dashboard (`/student`)
**Key Features:**
- Personal academic information
- Attendance history
- Grades and results
- Fee status

**API Endpoints:**
- `GET /api/v1/students/profile` - Student profile
- `GET /api/v1/students/my-attendance` - Attendance history
- `GET /api/v1/students/my-results` - Academic results
- `GET /api/v1/students/my-fees` - Fee information

### 5. Parent Dashboard (`/parent`)
**Key Features:**
- Children overview
- Academic progress monitoring
- Attendance tracking
- Fee payment status

**API Endpoints:**
- `GET /api/v1/parents/profile` - Parent profile
- `GET /api/v1/parents/children` - Children information

## Core Components Implementation

### 1. Login Component
```typescript
// features/auth/Login.tsx
export const Login: React.FC = () => {
  const { register, handleSubmit, formState: { errors } } = useForm<LoginForm>();
  const [login, { isLoading }] = useLoginMutation();
  const navigate = useNavigate();
  
  const onSubmit = async (data: LoginForm) => {
    try {
      const result = await login(data).unwrap();
      AuthService.setToken(result.token);
      
      // Role-based redirect
      const userRole = result.roles[0];
      switch (userRole) {
        case 'SUPERADMIN':
          navigate('/superadmin');
          break;
        case 'ADMIN':
          navigate('/admin');
          break;
        case 'TEACHER':
          navigate('/teacher');
          break;
        case 'STUDENT':
          navigate('/student');
          break;
        case 'PARENT':
          navigate('/parent');
          break;
        default:
          navigate('/');
      }
    } catch (error) {
      toast.error('Login failed');
    }
  };
  
  return (
    <Container maxWidth="sm">
      <Paper elevation={3} sx={{ p: 4, mt: 8 }}>
        <Typography variant="h4" align="center" gutterBottom>
          GMS Login
        </Typography>
        <form onSubmit={handleSubmit(onSubmit)}>
          <TextField
            {...register('username', { required: 'Username is required' })}
            fullWidth
            label="Username"
            error={!!errors.username}
            helperText={errors.username?.message}
            margin="normal"
          />
          <TextField
            {...register('password', { required: 'Password is required' })}
            fullWidth
            label="Password"
            type="password"
            error={!!errors.password}
            helperText={errors.password?.message}
            margin="normal"
          />
          <Button
            type="submit"
            fullWidth
            variant="contained"
            sx={{ mt: 3 }}
            disabled={isLoading}
          >
            {isLoading ? 'Logging in...' : 'Login'}
          </Button>
        </form>
      </Paper>
    </Container>
  );
};
```

### 2. Protected Route Component
```typescript
// components/common/ProtectedRoute.tsx
interface ProtectedRouteProps {
  children: React.ReactNode;
  requiredRoles: string[];
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({
  children,
  requiredRoles,
}) => {
  const token = AuthService.getToken();
  
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  
  try {
    const decodedToken = AuthService.decodeToken(token);
    const userRoles = decodedToken.roles || [];
    
    const hasRequiredRole = requiredRoles.some(role => 
      userRoles.includes(role)
    );
    
    if (!hasRequiredRole) {
      return <Navigate to="/unauthorized" replace />;
    }
    
    return <>{children}</>;
  } catch (error) {
    AuthService.removeToken();
    return <Navigate to="/login" replace />;
  }
};
```

### 3. Data Table Component
```typescript
// components/common/DataTable.tsx
interface DataTableProps<T> {
  data: T[];
  columns: GridColDef[];
  loading?: boolean;
  onAdd?: () => void;
  onEdit?: (row: T) => void;
  onDelete?: (id: string) => void;
}

export const DataTable = <T extends { id: string }>({
  data,
  columns,
  loading,
  onAdd,
  onEdit,
  onDelete,
}: DataTableProps<T>) => {
  const actionColumn: GridColDef = {
    field: 'actions',
    headerName: 'Actions',
    width: 150,
    renderCell: (params) => (
      <Box>
        {onEdit && (
          <IconButton onClick={() => onEdit(params.row)}>
            <EditIcon />
          </IconButton>
        )}
        {onDelete && (
          <IconButton onClick={() => onDelete(params.row.id)}>
            <DeleteIcon />
          </IconButton>
        )}
      </Box>
    ),
  };
  
  const columnsWithActions = onEdit || onDelete 
    ? [...columns, actionColumn] 
    : columns;
  
  return (
    <Box>
      {onAdd && (
        <Box sx={{ mb: 2 }}>
          <Button variant="contained" onClick={onAdd}>
            Add New
          </Button>
        </Box>
      )}
      <DataGrid
        rows={data}
        columns={columnsWithActions}
        loading={loading}
        autoHeight
        disableRowSelectionOnClick
        pageSizeOptions={[10, 25, 50]}
        initialState={{
          pagination: { paginationModel: { pageSize: 10 } },
        }}
      />
    </Box>
  );
};
```

## Development Phases

### Phase 1: Foundation Setup (Week 1)
- [ ] Project initialization with Vite + TypeScript
- [ ] Install and configure dependencies
- [ ] Set up folder structure
- [ ] Configure ESLint, Prettier, and Husky
- [ ] Set up Redux store with RTK Query
- [ ] Implement authentication system
- [ ] Create protected routes

### Phase 2: Core Admin Features (Week 2-3)
- [ ] SuperAdmin: Schools management
- [ ] Admin: Student management
- [ ] Admin: Employee management
- [ ] Admin: Classroom and Subject management
- [ ] Common components (DataTable, Forms, Modals)

### Phase 3: Academic Operations (Week 4-5)
- [ ] Teacher: Dashboard and class management
- [ ] Teacher: Attendance marking system
- [ ] Student: Personal dashboard
- [ ] Student: Attendance and grades view
- [ ] Parent: Children monitoring

### Phase 4: Advanced Features (Week 6-7)
- [ ] Fee management system
- [ ] Exam and results module
- [ ] Communication system (notifications, announcements)
- [ ] Reports and analytics

### Phase 5: Polish & Deploy (Week 8)
- [ ] UI/UX improvements
- [ ] Performance optimization
- [ ] Testing (unit, integration, e2e)
- [ ] Build optimization and deployment

## Key Implementation Notes

### State Management
- Use Redux Toolkit for global state
- RTK Query for API calls and caching
- Local component state for UI-only data

### Form Handling
- React Hook Form for all forms
- Yup for validation schemas
- Custom form components for consistency

### Error Handling
- Global error boundary
- Toast notifications for user feedback
- Proper loading states

### Performance
- Code splitting by routes
- Lazy loading for heavy components
- Memoization for expensive calculations

### Testing Strategy
- Unit tests for components and hooks
- Integration tests for API calls
- E2E tests for critical user flows

This guide provides a complete roadmap for building the GMS frontend that integrates seamlessly with your existing backend API.
│   ├── dashboard/          # Dashboard module
│   ├── schools/            # School management
│   ├── users/              # User management
│   ├── classrooms/         # Classroom management
│   ├── subjects/           # Subject management
│   ├── students/           # Student management
│   ├── employees/          # Employee management
│   ├── attendance/         # Attendance management
│   ├── exams/              # Exam management
│   ├── results/            # Result management
│   ├── fees/              # Fee management
│   ├── payments/           # Payment management
│   ├── timetable/          # Timetable management
│   └── reports/            # Reporting module
├── hooks/                  # Custom React hooks
├── store/                  # Redux store
│   ├── slices/             # Redux slices for each feature
│   └── index.js            # Store configuration
├── services/               # API service layer
├── utils/                  # Utility functions
├── routes/                 # Route configuration
├── constants/              # Application constants
├── themes/                 # MUI theme configuration
└── App.js                  # Main application component
```

## Authentication System

### Login Process
- **Endpoint**: POST /api/v1/auth/login
- **Request Payload**:
  ```json
  {
    "username": "string",
    "password": "string"
  }
  ```
- **Response**:
  ```json
  {
    "accessToken": "string",
    "tokenType": "Bearer",
    "expiresIn": "integer",
    "requirePasswordChange": "boolean"
  }
  ```

### Implementation Requirements
1. Create a clean, professional login page with school-themed background
2. Implement form validation for username and password fields
3. Handle 401 responses for invalid credentials with user-friendly error messages
4. Store JWT token securely in HTTP-only cookies or localStorage
5. Redirect users based on their roles after successful login
6. If `requirePasswordChange` is true, redirect to password change page

### Password Change
- **Endpoint**: POST /api/v1/auth/change-password
- **Request Payload**:
  ```json
  {
    "oldPassword": "string",
    "newPassword": "string"
  }
  ```

### Employee Self-Registration
- **Endpoint**: POST /api/v1/auth/self-register
- **Request Payload**:
  ```json
  {
    "empId": "integer",
    "schoolId": "integer",
    "username": "string",
    "password": "string"
  }
  ```

## Role-Based Interfaces

### SUPERADMIN Dashboard
- **School Management**:
  - School list with search and filtering capabilities
  - Create/Edit school forms with all required fields
  - Toggle school status functionality
  - View school details with admin information
  - Automatic email of admin credentials upon school creation

### ADMIN Dashboard
- **Dashboard Overview**:
  - Key metrics cards (total students, teachers, classes)
  - Recent activity feed
  - Quick action buttons (create student, teacher, class)

- **User Management**:
  - User list with role-based filtering
  - Create/Edit user forms
  - Profile management
  - Role assignment interface

- **Academic Management**:
  - Classroom management (create, edit, delete)
  - Subject management
  - Section management
  - Academic year setup

- **Employee Management**:
  - Teacher list with subject/classroom assignments
  - Employee profiles with detailed information
  - Self-registration approval workflow

- **Student Management**:
  - Student list with class/section information
  - Detailed student profiles
  - Admission workflow
  - Parent-student relationship management

- **Assessment System**:
  - Exam creation and scheduling
  - Exam-subject linking interface
  - Student registration for exams
  - Result entry and management
  - Grading scheme configuration

- **Attendance System**:
  - Individual and bulk attendance recording
  - Attendance reports by date/class/student
  - Calendar view for attendance tracking

- **Financial Management**:
  - Fee structure creation and management
  - Student fee assignment
  - Payment recording interface
  - Financial dashboard with charts
  - Invoice and receipt management

### TEACHER Dashboard
- **Class Management**:
  - Assigned classes list
  - Class rosters
  - Subject-specific views

- **Attendance**:
  - Quick attendance recording for assigned classes
  - Attendance history view

- **Assessment**:
  - Exam scheduling for assigned subjects
  - Result entry for assigned classes
  - Student performance tracking

- **Timetable**:
  - Personal timetable view
  - Class timetable access

### STUDENT Dashboard
- **Profile**:
  - Personal information display
  - Academic information
  - Contact details

- **Academics**:
  - Timetable view
  - Subject list
  - Attendance records
  - Exam schedule
  - Results and grades

- **Financials**:
  - Fee structure view
  - Payment history
  - Outstanding fees

### PARENT Dashboard
- **Child Profiles**:
  - Access to linked children's information
  - Switch between children

- **Academics**:
  - Child's attendance records
  - Exam schedules and results
  - Timetable access

- **Financials**:
  - Child's fee structure
  - Payment history
  - Payment initiation

## API Integration Requirements

### HTTP Client Implementation
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

## State Management Structure

### Redux Store
```javascript
// store/index.js
import { configureStore } from '@reduxjs/toolkit';
import authReducer from './slices/authSlice';
import schoolReducer from './slices/schoolSlice';
import userReducer from './slices/userSlice';
// ... other reducers

export const store = configureStore({
  reducer: {
    auth: authReducer,
    schools: schoolReducer,
    users: userReducer,
    // ... other reducers
  },
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
```

### Auth Slice
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

## Responsive Design Requirements

### Breakpoints
- **Mobile**: 0-767px
- **Tablet**: 768px-1199px
- **Desktop**: 1200px+

### Layout Guidelines
1. **Desktop (1200px+)**:
   - Full sidebar navigation
   - Multi-column layouts
   - Data-dense views
   - Keyboard shortcuts support

2. **Tablet (768px-1199px)**:
   - Collapsible sidebar
   - Adaptive grid layouts
   - Touch-friendly controls
   - Simplified navigation

3. **Mobile (0-767px)**:
   - Bottom navigation bar
   - Single-column layouts
   - Modal forms
   - Simplified interactions
   - Touch-optimized controls

## Component Design Principles

### Cards
- Elevated cards with consistent padding (16px) and subtle shadows
- Hover effects for interactive cards
- Consistent border radius (8px)

### Buttons
- MUI Button components with appropriate variants (contained, outlined, text)
- Consistent sizing (small, medium, large)
- Proper spacing between buttons
- Icon support for enhanced UX

### Forms
- Consistent field heights (40px)
- Proper labeling and validation
- Error handling with user-friendly messages
- Responsive layout for different screen sizes

### Tables
- Data tables with sorting, pagination, and responsive behavior
- Search and filter capabilities
- Action buttons for each row
- Loading states and empty states

### Modals
- Dialogs for forms, confirmations, and detailed views
- Consistent styling with the rest of the application
- Proper focus management
- Escape key and backdrop click handling

## Performance Requirements

### Loading Performance
- Code splitting by route and feature
- Lazy loading for non-critical components
- Image optimization with appropriate sizing
- Bundle size monitoring

### Runtime Performance
- Efficient re-rendering with React.memo and useMemo
- Virtualized lists for large datasets
- Debounced search inputs
- Proper cleanup of event listeners

## Accessibility Requirements

### WCAG Compliance
- Proper semantic HTML structure
- ARIA labels for interactive elements
- Keyboard navigation support
- Color contrast compliance
- Screen reader compatibility

### Internationalization
- Support for RTL languages
- Date/number formatting
- Text scaling support

## Security Considerations

### Frontend Security
- Input sanitization for all user data
- Protection against XSS attacks
- Secure token storage
- Route-based authorization

### Data Protection
- Mask sensitive information in UI
- Confirmation dialogs for destructive actions
- Audit trail for critical operations

## Testing Requirements

### Unit Testing
- Component testing with React Testing Library
- Redux logic testing
- Utility function testing

### Integration Testing
- API integration testing
- Form submission flows
- Navigation testing

### End-to-End Testing
- Critical user flows
- Role-based access testing
- Cross-browser compatibility

## Documentation Requirements

### Developer Documentation
- Component library documentation
- API integration guides
- State management patterns
- Testing strategies

### User Documentation
- Role-specific user guides
- Feature walkthroughs
- FAQ section
- Video tutorials for complex workflows

## Key Features to Highlight in UI

### Dashboard Visualizations
- School metrics with charts and graphs
- Attendance trends
- Financial summaries
- Academic performance indicators

### Workflow Optimizations
- Quick action buttons for common tasks
- Bulk operations for administrative tasks
- Smart defaults and auto-population
- Keyboard shortcuts for power users

### Notification System
- Real-time notifications for important events
- Email/SMS integration indicators
- Activity feed with filtering options

### Search and Filter Capabilities
- Global search across all entities
- Advanced filtering options
- Saved searches and filters
- Export functionality for reports

## Implementation Roadmap

### Phase 1: Foundation & Auth (Week 1)
1. Set up development environment
2. Implement project structure
3. Create authentication flows
4. Establish design system

### Phase 2: Core Architecture (Week 2)
1. Implement Redux store for all features
2. Create reusable component library
3. Set up API service layer
4. Implement routing structure

### Phase 3: SUPERADMIN Features (Week 3)
1. Implement school management for SUPERADMIN
2. Create dashboard for SUPERADMIN

### Phase 4: ADMIN Core Features (Week 4)
1. Implement user management for ADMIN
2. Implement classroom management for ADMIN
3. Create ADMIN dashboard

### Phase 5: Student & Employee Management (Week 5)
1. Implement student management for ADMIN
2. Implement employee management for ADMIN

### Phase 6: Attendance System (Week 6)
1. Implement attendance recording for ADMIN/TEACHER
2. Implement attendance reporting

### Phase 7: Assessment System (Week 7)
1. Implement exam management for ADMIN
2. Implement result management for ADMIN/TEACHER

### Phase 8: Financial System (Week 8)
1. Implement fee management for ADMIN
2. Implement payment management for ADMIN

### Phase 9: Role-Specific Interfaces (Week 9)
1. Implement TEACHER dashboard and features
2. Implement STUDENT dashboard and features
3. Implement PARENT dashboard and features

### Phase 10: Advanced Features & Polish (Week 10)
1. Implement advanced UI features
2. Optimize performance
3. Complete testing coverage
4. Fix bugs and polish UI

## Success Metrics

### Performance
- Page load times under 2 seconds
- API response handling under 500ms
- Bundle size under 2MB

### User Experience
- Task completion rates above 90%
- User satisfaction scores above 4/5
- Support ticket reduction by 50%

### Technical Quality
- Code coverage above 80%
- Zero critical security vulnerabilities
- WCAG AA compliance

## Backend API Endpoints Reference

### Authentication Endpoints
- POST /api/v1/auth/login - User authentication
- POST /api/v1/auth/login-username - Username-based authentication
- POST /api/v1/auth/change-password - Password change
- POST /api/v1/auth/self-register - Employee self-registration

### School Management Endpoints
- POST /api/v1/schools/create - Create school
- PUT /api/v1/schools/update - Update school
- GET /api/v1/schools - List schools
- GET /api/v1/schools/{id} - Get school by ID
- PATCH /api/v1/schools/toggle - Toggle school status

### User Management Endpoints
- POST /api/v1/users/create - Create user
- PUT /api/v1/users/update - Update user
- GET /api/v1/users - List users
- GET /api/v1/users/me - Get current user profile
- PUT /api/v1/users/me - Update current user profile

### Classroom Management Endpoints
- POST /api/v1/classes/create - Create classroom
- PUT /api/v1/classes/update - Update classroom
- GET /api/v1/classes - List classrooms
- GET /api/v1/classes/{id} - Get classroom by ID
- PATCH /api/v1/classes/toggle - Toggle classroom status
- GET /api/v1/classes/teacher/assigned - Get assigned classrooms for teacher

### Student Management Endpoints
- POST /api/v1/students/create - Create student
- PUT /api/v1/students/update - Update student
- GET /api/v1/students - List students
- GET /api/v1/students/admissions - List student admissions
- PATCH /api/v1/students/toggle - Toggle student status
- GET /api/v1/students/profile - Get student profile

### Attendance Management Endpoints
- POST /api/v1/attendance - Record attendance
- POST /api/v1/attendance/bulk - Record bulk attendance
- PUT /api/v1/attendance/{id} - Update attendance
- DELETE /api/v1/attendance/{id} - Delete attendance
- GET /api/v1/attendance/student/{studentId} - Get attendance for student
- GET /api/v1/attendance/classroom/{classroomId} - Get attendance for classroom
- GET /api/v1/attendance/range - Get attendance by date range
- GET /api/v1/attendance/student/{studentId}/range - Get attendance by student and date range

### Exam Management Endpoints
- POST /api/v1/exams - Create exam
- PUT /api/v1/exams/{id} - Update exam
- DELETE /api/v1/exams/{id} - Delete exam
- GET /api/v1/exams - List exams
- GET /api/v1/exams/{id} - Get exam by ID
- GET /api/v1/exams/academic-year - Get exams by academic year

### Financial Management Endpoints
- POST /api/v1/fees - Create fee
- PUT /api/v1/fees/{id} - Update fee
- DELETE /api/v1/fees/{id} - Delete fee
- GET /api/v1/fees - List fees
- GET /api/v1/fees/{id} - Get fee by ID
- GET /api/v1/fees/academic-year - Get fees by academic year
- GET /api/v1/fees/total-amount - Get total fee amount by academic year

- POST /api/v1/fee-payments - Create fee payment
- PUT /api/v1/fee-payments/{id} - Update fee payment
- DELETE /api/v1/fee-payments/{id} - Delete fee payment
- GET /api/v1/fee-payments - List fee payments
- GET /api/v1/fee-payments/{id} - Get fee payment by ID
- GET /api/v1/fee-payments/student/{studentId} - Get fee payments by student
- GET /api/v1/fee-payments/date-range - Get fee payments by date range
- GET /api/v1/fee-payments/total-payments - Get total payments by date range
- GET /api/v1/fee-payments/payment-count - Get count of payments by date

### Role-Based Access Control
- SUPERADMIN: Full system access
- ADMIN: School-level administration
- TEACHER: Classroom and subject-specific access
- STUDENT: Personal academic information access
- PARENT: Linked children's information access

### JWT Token Structure
- Subject (username)
- Roles array
- School ID
- Employee ID
- Expiration time

This comprehensive guide provides all the necessary information for frontend developers to build a modern, responsive, and attractive React.js frontend for the GMS application that integrates seamlessly with the existing backend API.