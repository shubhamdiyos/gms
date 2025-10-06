# GMS Frontend Development Handover Document

## Project Overview
This document serves as a comprehensive guide for the frontend development team working on the Global/School Management System (GMS). The system is a Spring Boot backend application that requires a modern, responsive React frontend with role-based access control for 5 user types: SUPERADMIN, ADMIN, TEACHER, STUDENT, and PARENT.

## Technology Stack
- **Framework**: React.js with TypeScript
- **State Management**: Redux Toolkit
- **UI Library**: Material-UI (MUI)
- **Routing**: React Router v6
- **HTTP Client**: Axios
- **Form Handling**: React Hook Form with Yup validation
- **Charts**: Chart.js or Recharts
- **Build Tool**: Vite.js
- **Testing**: Jest, React Testing Library, Cypress

## Design System
### Color Palette
- Primary: #1976d2 (Blue) - Trust and professionalism
- Secondary: #388e3c (Green) - Growth and education
- Accent: #f57c00 (Orange) - Highlights and calls-to-action
- Neutral: #f5f5f5 (Light gray) for backgrounds, #212121 (Dark gray) for text

### Typography
- Headings: Roboto Bold
- Body: Roboto Regular
- Base font size: 14px-16px with appropriate scaling

### Spacing
- Consistent 8px grid system (8, 16, 24, 32px spacing)

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

## Role-Based Interfaces

### Authentication Pages
1. **Login Page**
   - Email/username and password fields
   - "Remember me" checkbox
   - "Forgot password" link
   - Role-based redirection after login

2. **Password Change**
   - Old password, new password, confirm password fields
   - Password strength indicator
   - Validation rules display

### SUPERADMIN Dashboard
- School management (list, create, edit, toggle status)
- View school details with admin information

### ADMIN Dashboard
- Dashboard overview with key metrics
- User management (list, create, edit, profile management)
- Academic management (classrooms, subjects, sections, academic years)
- Employee management (teacher list, profiles, self-registration approval)
- Student management (list, profiles, admission workflow)
- Assessment system (exam creation, scheduling, results)
- Attendance system (individual and bulk recording)
- Financial management (fee structure, payments, dashboard)

### TEACHER Dashboard
- Assigned classes list and rosters
- Attendance recording for assigned classes
- Exam scheduling for assigned subjects
- Result entry for assigned classes
- Personal timetable view

### STUDENT Dashboard
- Personal profile information
- Academic information (timetable, subjects, attendance, exams, results)
- Financial information (fee structure, payment history, outstanding fees)

### PARENT Dashboard
- Access to linked children's information
- Child's academic information (attendance, exams, results)
- Child's financial information (fee structure, payment history, payment initiation)

## State Management
The application uses Redux Toolkit with the following slices:
1. authSlice - Authentication state (user, token, permissions)
2. schoolSlice - School data and operations
3. userSlice - User data and operations
4. classroomSlice - Classroom data and operations
5. studentSlice - Student data and operations
6. employeeSlice - Employee data and operations
7. attendanceSlice - Attendance data and operations
8. examSlice - Exam data and operations
9. resultSlice - Result data and operations
10. feeSlice - Fee data and operations
11. paymentSlice - Payment data and operations
12. uiSlice - UI state (loading, notifications, modals)

## API Integration
All API endpoints are documented in the API Integration Guide. Key integration points include:

### Authentication Flow
- POST /api/v1/auth/login - User authentication
- POST /api/v1/auth/login-username - Username-based authentication
- POST /api/v1/auth/change-password - Password change
- POST /api/v1/auth/self-register - Employee self-registration

### Core Entities
1. **Schools**: Create, update, list, toggle status
2. **Users**: Create, update, list, profile management
3. **Classrooms**: Create, update, list, toggle status, assigned classrooms
4. **Students**: Create, update, list, toggle status, profile access
5. **Attendance**: Record individual/bulk, update, delete, fetch by filters
6. **Exams**: Create, update, delete, list
7. **Fees & Payments**: Create, update, delete, list, fetch summaries

## Component Architecture
### Reusable Components
1. Layout Components (AppLayout, AuthLayout, DashboardLayout)
2. Form Components (FormWrapper, TextInput, SelectInput, DatePicker)
3. Data Display Components (DataTable, DataCard, InfoList, StatusBadge)
4. Navigation Components (Sidebar, Topbar, Breadcrumb, Tabs)
5. Feedback Components (Notification, AlertDialog, LoadingSpinner)
6. Chart Components (LineChart, BarChart, PieChart)

### Custom Hooks
1. useAuth - Authentication state and actions
2. useApi - Generic API call hook with loading/error handling
3. useForm - Form handling with validation
4. usePermissions - Role-based permission checking
5. usePagination - Pagination logic for data tables
6. useFilter - Filtering logic for data lists

## Responsive Design
- Desktop (1200px+): Full sidebar navigation, multi-column layouts
- Tablet (768px-1199px): Collapsible sidebar, adaptive layouts
- Mobile (0-767px): Bottom navigation, single-column layouts

## Error Handling
- Global error middleware for consistent error display
- Form validation with real-time feedback
- 401 responses with automatic logout
- Network error handling with retry logic

## Testing Strategy
1. Unit Testing - Component and Redux slice testing with Jest
2. Integration Testing - API integration and form submission flows
3. End-to-End Testing - Critical user flows with Cypress
4. Accessibility Testing - WCAG compliance verification

## Performance Optimization
1. Code splitting by route and feature
2. Lazy loading for non-critical components
3. Bundle size monitoring
4. Efficient re-rendering with React.memo and useMemo
5. Virtualized lists for large datasets

## Security Considerations
1. Input sanitization for all user data
2. Secure JWT token storage
3. Route-based authorization
4. Role-based UI access control
5. Protection against XSS attacks

## Development Guidelines
1. Follow the established folder structure
2. Use TypeScript for type safety
3. Implement responsive design using the provided breakpoints
4. Follow the design system for colors, typography, and spacing
5. Write unit tests for components and Redux slices
6. Use the provided custom hooks where applicable
7. Follow role-based access control patterns
8. Implement proper loading and error states for all API calls

## Success Metrics
1. Page load times under 2 seconds
2. Test coverage above 80%
3. WCAG AA compliance
4. Mobile responsiveness
5. Task completion rates above 90%

## Next Steps for Development Team
1. Review all documentation in the memory-bank directory
2. Set up development environment using the quick start guide
3. Begin with Phase 1: Foundation & Setup from the roadmap
4. Implement authentication flows first
5. Follow the component architecture for building reusable components
6. Use the state management plan for Redux implementation
7. Follow API integration guide for backend communication
8. Implement testing as features are developed
9. Follow responsive design guidelines for all components
10. Conduct regular code reviews to ensure consistency

## Support and Questions
For any questions or clarifications regarding the frontend implementation:
1. Review existing documentation in the memory-bank
2. Refer to the API documentation for backend endpoints
3. Follow established patterns in the codebase
4. Contact the technical lead for architectural decisions