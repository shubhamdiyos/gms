# GMS Mobile M1 Implementation Plan

**Version:** 1.0  
**Date:** 2026-11-05  
**Status:** READY FOR APPROVAL  
**Prerequisites:** M0 Foundation ✅ COMPLETE

---

## Executive Summary

M1 implements the **authentication foundation and role-based dashboard framework** for all four mobile roles (ADMIN, TEACHER, STUDENT, PARENT). This milestone establishes the core navigation structure, session management, and role-specific dashboard screens that serve as the foundation for all subsequent feature development.

**M1 Scope:**
- ✅ Authentication flow (login, logout, session validation, password change)
- ✅ Role-based navigation structure
- ✅ Four role-specific dashboard screens
- ✅ Cookie-based session management with CSRF protection
- ✅ Backend API integration layer
- ✅ State management with Riverpod
- ✅ Routing with GoRouter and auth guards

**Out of Scope (M2+):**
- Feature-specific workflows (attendance marking, fee collection, etc.)
- Data visualization and charts
- Push notifications
- Offline support

---

## 1. Approved MVP Screen Inventory

### M1 Screens (4 dashboards + auth screens)

| # | Screen | Role | Priority | Design Reference |
|---|--------|------|----------|------------------|
| 1 | Login Screen | All | P0 | New (M0) |
| 2 | Change Password Screen | All (first login) | P0 | New (M0) |
| 3 | ADMIN Dashboard | ADMIN | P0 | Stitch: `5a43c1af7d3740f994885b182c739239` |
| 4 | TEACHER Dashboard | TEACHER | P0 | Stitch: `ad24999303744cbe8c61b8fdd2302f61` |
| 5 | STUDENT Dashboard | STUDENT | P0 | Stitch: `c9bc244168d9429eb1d2401eef143165` |
| 6 | PARENT Dashboard | PARENT | P0 | Stitch: `3d8f5426d1d544d18c62d8dd8da2c291` |

**Total M1 Screens:** 6 (2 auth + 4 dashboards)

### M2+ Screens (Future Milestones)

| Milestone | Screens | Roles |
|-----------|---------|-------|
| M2 | Student List, Student Details, Mark Attendance | ADMIN, TEACHER |
| M3 | Fee Collection, Payment History, Receipts | ACCOUNTANT, STUDENT, PARENT |
| M4 | Results View, Academic Progress, Report Cards | TEACHER, STUDENT, PARENT |
| M5 | Announcements, Notifications, Communication | All roles |
| M6 | Timetable, Schedule, Class Management | All roles |

---

## 2. Exact Role-to-Screen Mapping

### Role Accessibility Matrix

| Role | Login | Change Password | Dashboard | Navigation Tabs |
|------|-------|-----------------|-----------|-----------------|
| **ADMIN** | ✅ | ✅ (if required) | ✅ School Overview | Home, Students, Teachers, Academics, More |
| **TEACHER** | ✅ | ✅ (if required) | ✅ Class Schedule | Home, Classes, Attendance, Homework, More |
| **STUDENT** | ✅ | ✅ (if required) | ✅ Personal Academic | Home, Schedule, Results, Fees, More |
| **PARENT** | ✅ | ✅ (if required) | ✅ Child Overview | Home, Attendance, Results, Fees, More |
| **SUPERADMIN** | ❌ | ❌ | ❌ | Web-only (not supported on mobile) |
| **ACCOUNTANT** | ✅ | ✅ (if required) | ⏸️ M3 | Dashboard, Fees, Students, Reports, More |

### Role-Based Redirect Logic

```dart
// After successful login:
if (user.role == 'ADMIN') → /admin/dashboard
if (user.role == 'TEACHER') → /teacher/dashboard
if (user.role == 'STUDENT') → /student/dashboard
if (user.role == 'PARENT') → /parent/dashboard
if (user.role == 'SUPERADMIN') → Error: "Role not supported on mobile"
if (user.requirePasswordChange) → /change-password (blocks all other navigation)
```

---

## 3. Stitch Design References

### Design System
- **Project ID:** `7439120177506880457`
- **Design System ID:** `assets/5382227573212513983`
- **Full Documentation:** [GMS_MOBILE_DESIGN_DELIVERABLES.md](./GMS_MOBILE_DESIGN_DELIVERABLES.md)

### Screen-by-Screen References

#### 1. ADMIN Dashboard
- **Stitch Screen ID:** `5a43c1af7d3740f994885b182c739239`
- **HTML Export:** `projects/7439120177506880457/files/d3d5acec79fd4da9946af59741014b65:download`
- **Screenshot:** `projects/7439120177506880457/files/76aa202305b14b6e896b92f747504465:download`
- **Key Features:**
  - School overview metrics (total students, teachers, attendance, fees)
  - Weekly attendance trends chart
  - Recent activities feed
  - Quick access cards to all modules

#### 2. TEACHER Dashboard
- **Stitch Screen ID:** `ad24999303744cbe8c61b8fdd2302f61`
- **HTML Export:** `projects/7439120177506880457/files/eaded621edc8440395d972e497a9061c:download`
- **Screenshot:** `projects/7439120177506880457/files/1f6dfc0ecc5c4e618ca77f049b485c96:download`
- **Key Features:**
  - Teacher profile with class assignment
  - Today's schedule with time slots
  - Quick actions (mark attendance, assign homework)
  - Class performance metrics
  - Pending tasks with urgency indicators

#### 3. STUDENT Dashboard
- **Stitch Screen ID:** `c9bc244168d9429eb1d2401eef143165`
- **HTML Export:** `projects/7439120177506880457/files/c4cd000fe3a140e0a8719a4c3131edf6:download`
- **Screenshot:** `projects/7439120177506880457/files/823b51a12a034fef86728972b416ceab:download`
- **Key Features:**
  - Student profile with ID
  - Quick stats (attendance %, average score, fees status)
  - Today's schedule
  - Upcoming tests and assignments
  - Recent results with marks

#### 4. PARENT Dashboard
- **Stitch Screen ID:** `3d8f5426d1d544d18c62d8dd8da2c291`
- **HTML Export:** `projects/7439120177506880457/files/78343f4bc8d14bf789a0a1d7b9262169:download`
- **Screenshot:** `projects/7439120177506880457/files/921bd93d62f542528485fe19921863bc:download`
- **Key Features:**
  - Child selector (for multiple children)
  - Child profile summary
  - Attendance overview with percentage
  - Academic performance metrics
  - Upcoming tests and deadlines
  - Recent results
  - School announcements

---

## 4. Screen-to-Backend API Mapping

### Authentication APIs (All Roles)

| Screen | API Endpoint | Method | Purpose | Request | Response |
|--------|-------------|--------|---------|---------|----------|
| Login | `/api/v1/auth/csrf-token` | GET | Fetch CSRF token | - | `{token: string}` |
| Login | `/api/v1/auth/login` | POST | Authenticate user | `{username, password}` | `{user, requirePasswordChange}` |
| Change Password | `/api/v1/auth/change-password` | POST | Update password | `{oldPassword, newPassword}` | `{message}` |
| All Screens | `/api/v1/auth/logout` | POST | End session | - | `{message}` |
| All Screens | `/api/v1/users/me` | GET | Validate session | - | `{user}` |

### ADMIN Dashboard APIs

| Widget | API Endpoint | Method | Data |
|--------|-------------|--------|------|
| Total Students | `/api/v1/students?page=0&size=1` | GET | `page.totalElements` |
| Total Teachers | `/api/v1/teachers?page=0&size=1` | GET | `page.totalElements` |
| Attendance Today | `/api/v1/attendance?date={today}` | GET | Summary stats |
| Fee Collection | `/api/v1/fees/academic-year/{year}/total-amount` | GET | Total amount |
| Recent Activities | `/api/v1/announcements?sort=createdAt,desc&size=5` | GET | Latest 5 announcements |
| Attendance Trend | `/api/v1/attendance?startDate={weekAgo}&endDate={today}` | GET | Daily breakdown |

### TEACHER Dashboard APIs

| Widget | API Endpoint | Method | Data |
|--------|-------------|--------|------|
| My Profile | `/api/v1/teachers/me` | GET | Teacher details |
| Today's Classes | `/api/v1/teachers/me/classes?date={today}` | GET | Class schedule |
| Class Performance | `/api/v1/teachers/me/students/academic-summary` | GET | Aggregate metrics |
| Pending Tasks | `/api/v1/announcements?audience=CLASS&author=me&published=false` | GET | Unpublished announcements |

### STUDENT Dashboard APIs

| Widget | API Endpoint | Method | Data |
|--------|-------------|--------|------|
| My Profile | `/api/v1/students/me` | GET | Student details |
| Attendance % | `/api/v1/students/me/attendance/summary` | GET | Attendance percentage |
| Today's Schedule | `/api/v1/students/me/timetable?date={today}` | GET | Class schedule |
| Upcoming Tests | `/api/v1/students/me/exams?startDate={today}&sortBy=date` | GET | Future exams |
| Recent Results | `/api/v1/students/me/results?sort=examDate,desc&size=5` | GET | Latest 5 results |
| Fee Status | `/api/v1/students/me/fees` | GET | Outstanding fees |

### PARENT Dashboard APIs

| Widget | API Endpoint | Method | Data |
|--------|-------------|--------|------|
| My Children | `/api/v1/parents/me/children` | GET | List of children |
| Child Attendance | `/api/v1/parents/me/children/{childId}/attendance/summary` | GET | Attendance % per child |
| Child Results | `/api/v1/parents/me/children/{childId}/results?sort=examDate,desc&size=3` | GET | Latest 3 results |
| Upcoming Tests | `/api/v1/parents/me/children/{childId}/exams?startDate={today}` | GET | Future exams |
| Announcements | `/api/v1/parents/me/announcements?size=5` | GET | Latest 5 announcements |

---

## 5. Navigation and Access-Control Plan

### Navigation Architecture

```
App Start
  ↓
[Auth Check] → Not authenticated → /login
  ↓ (authenticated)
[Session Validation] → Invalid → /login (clear state)
  ↓ (valid)
[Password Change Check] → Required → /change-password (blocks all)
  ↓ (not required)
[Role-Based Redirect]
  ├─ ADMIN → /admin/dashboard
  ├─ TEACHER → /teacher/dashboard
  ├─ STUDENT → /student/dashboard
  └─ PARENT → /parent/dashboard
```

### Bottom Navigation Structure (Per Role)

#### ADMIN Navigation
```dart
BottomNavigationBar(
  items: [
    BottomNavigationBarItem(icon: Icons.dashboard, label: 'Home'),
    BottomNavigationBarItem(icon: Icons.people, label: 'Students'),
    BottomNavigationBarItem(icon: Icons.person, label: 'Teachers'),
    BottomNavigationBarItem(icon: Icons.school, label: 'Academics'),
    BottomNavigationBarItem(icon: Icons.more_horiz, label: 'More'),
  ]
)
```

#### TEACHER Navigation
```dart
BottomNavigationBar(
  items: [
    BottomNavigationBarItem(icon: Icons.home, label: 'Home'),
    BottomNavigationBarItem(icon: Icons.class_, label: 'Classes'),
    BottomNavigationBarItem(icon: Icons.check_circle, label: 'Attendance'),
    BottomNavigationBarItem(icon: Icons.assignment, label: 'Homework'),
    BottomNavigationBarItem(icon: Icons.more_horiz, label: 'More'),
  ]
)
```

#### STUDENT Navigation
```dart
BottomNavigationBar(
  items: [
    BottomNavigationBarItem(icon: Icons.home, label: 'Home'),
    BottomNavigationBarItem(icon: Icons.schedule, label: 'Schedule'),
    BottomNavigationBarItem(icon: Icons.grade, label: 'Results'),
    BottomNavigationBarItem(icon: Icons.payments, label: 'Fees'),
    BottomNavigationBarItem(icon: Icons.more_horiz, label: 'More'),
  ]
)
```

#### PARENT Navigation
```dart
BottomNavigationBar(
  items: [
    BottomNavigationBarItem(icon: Icons.home, label: 'Home'),
    BottomNavigationBarItem(icon: Icons.check_circle, label: 'Attendance'),
    BottomNavigationBarItem(icon: Icons.grade, label: 'Results'),
    BottomNavigationBarItem(icon: Icons.payments, label: 'Fees'),
    BottomNavigationBarItem(icon: Icons.more_horiz, label: 'More'),
  ]
)
```

### Access Control Enforcement

**Layer 1: Router Guards (GoRouter)**
```dart
GoRouter(
  redirect: (context, state) {
    final authState = ref.read(authProvider);
    
    // Block all routes except /login if not authenticated
    if (!authState.isAuthenticated && state.path != '/login') {
      return '/login';
    }
    
    // Block all routes except /change-password if password change required
    if (authState.isPasswordChangeRequired && state.path != '/change-password') {
      return '/change-password';
    }
    
    // Prevent SUPERADMIN access
    if (authState.user?.role == UserRole.SUPERADMIN) {
      return '/error?code=role_not_supported';
    }
    
    return null; // Allow navigation
  }
)
```

**Layer 2: Backend RBAC**
- All API endpoints enforce role-based access control
- Backend returns 403 Forbidden for unauthorized access
- Mobile app handles 403 by showing error screen

**Layer 3: Tenant Isolation**
- Backend automatically filters data by school_id
- Mobile app cannot access data from other schools
- All API requests include session cookie with tenant context

---

## 6. M1 Implementation Sequence

### Phase 1: Foundation (Week 1)
**Goal:** Authentication flow and basic navigation

**Tasks:**
1. ✅ **Already Complete (M0):**
   - API client with cookie management
   - CSRF interceptor with fail-closed behavior
   - Auth interceptor with 401 handling
   - Storage service (SharedPreferences)
   - Cookie service (in-memory CookieJar)
   - Auth repository (login, logout, validateSession)
   - Auth provider (state management)
   - Router with auth guards
   - Login screen (UI)
   - Change password screen (UI)

2. **New (M1 Phase 1):**
   - [ ] Create role-based route structure (`/admin/*`, `/teacher/*`, etc.)
   - [ ] Implement role-based redirect logic after login
   - [ ] Create base dashboard layout widget (reusable)
   - [ ] Set up Riverpod providers for dashboard data
   - [ ] Implement error handling for API failures
   - [ ] Add loading states and skeleton screens

**Deliverables:**
- User can login, change password (if required), and see role-specific empty dashboard
- Navigation structure in place
- Auth flow fully functional with CSRF protection

### Phase 2: ADMIN Dashboard (Week 2)
**Goal:** Complete ADMIN dashboard with all widgets

**Tasks:**
1. [ ] Create ADMIN dashboard screen
2. [ ] Implement school overview metrics widget
   - Fetch total students, teachers, classrooms
   - Display with icons and colors
3. [ ] Implement attendance trend chart
   - Fetch weekly attendance data
   - Display with line chart (fl_chart package)
4. [ ] Implement recent activities feed
   - Fetch latest 5 announcements
   - Display with timestamps and icons
5. [ ] Implement quick access cards
   - Navigate to Students, Teachers, Academics, etc.
6. [ ] Add pull-to-refresh functionality
7. [ ] Test with ADMIN role user

**Deliverables:**
- Fully functional ADMIN dashboard
- All widgets displaying real data from backend
- Responsive layout for different screen sizes

### Phase 3: TEACHER Dashboard (Week 3)
**Goal:** Complete TEACHER dashboard with all widgets

**Tasks:**
1. [ ] Create TEACHER dashboard screen
2. [ ] Implement teacher profile widget
   - Fetch teacher details and class assignment
3. [ ] Implement today's schedule widget
   - Fetch classes for today
   - Display with time slots and room numbers
4. [ ] Implement class performance metrics
   - Fetch aggregate student performance
   - Display with progress bars and averages
5. [ ] Implement pending tasks widget
   - Fetch unpublished announcements
   - Display with urgency indicators
6. [ ] Add quick action buttons (mark attendance, assign homework)
7. [ ] Test with TEACHER role user

**Deliverables:**
- Fully functional TEACHER dashboard
- All widgets displaying real data from backend
- Quick actions wired to placeholder screens (M2)

### Phase 4: STUDENT & PARENT Dashboards (Week 4)
**Goal:** Complete STUDENT and PARENT dashboards

**Tasks:**
1. [ ] Create STUDENT dashboard screen
2. [ ] Implement student profile widget
3. [ ] Implement quick stats widget (attendance %, average score, fees)
4. [ ] Implement today's schedule widget
5. [ ] Implement upcoming tests widget
6. [ ] Implement recent results widget
7. [ ] Create PARENT dashboard screen
8. [ ] Implement child selector widget (for multiple children)
9. [ ] Implement child profile summary widget
10. [ ] Implement attendance overview widget
11. [ ] Implement academic performance widget
12. [ ] Implement upcoming tests widget
13. [ ] Implement recent results widget
14. [ ] Implement announcements widget
15. [ ] Test with STUDENT and PARENT role users

**Deliverables:**
- Fully functional STUDENT and PARENT dashboards
- All widgets displaying real data from backend
- Multi-child support for PARENT role

### Phase 5: Polish & Testing (Week 5)
**Goal:** Final polish, testing, and bug fixes

**Tasks:**
1. [ ] Add loading skeletons for all dashboards
2. [ ] Add empty states for no data
3. [ ] Add error states with retry buttons
4. [ ] Implement pull-to-refresh on all dashboards
5. [ ] Add smooth transitions and animations
6. [ ] Test on different screen sizes (phones, tablets)
7. [ ] Test on different platforms (Android, iOS, Web)
8. [ ] Performance optimization (lazy loading, caching)
9. [ ] Accessibility audit (screen reader, contrast, touch targets)
10. [ ] Write integration tests for auth flow
11. [ ] Write widget tests for dashboard components
12. [ ] Fix all bugs and issues

**Deliverables:**
- Production-ready M1 milestone
- All dashboards fully functional
- Comprehensive test coverage
- Documentation complete

---

## 7. Testing Strategy

### Unit Tests

**Coverage Target:** 80%+ for business logic

**Test Files:**
```
test/
├── features/
│   └── auth/
│       ├── data/
│       │   └── repositories/
│       │       └── auth_repository_test.dart
│       ├── domain/
│       │   └── entities/
│       │       └── user_test.dart
│       └── presentation/
│           └── providers/
│               └── auth_provider_test.dart
├── core/
│   └── api/
│       └── interceptors/
│           ├── csrf_interceptor_test.dart ✅ (M0)
│           └── auth_interceptor_test.dart
└── services/
    ├── storage_service_test.dart ✅ (M0)
    └── cookie_service_test.dart
```

**Key Test Cases:**
- Auth repository: login success/failure, logout, session validation
- Auth provider: state transitions, error handling, password change flow
- CSRF interceptor: token attachment, fail-closed behavior, refresh on 403
- Storage service: save/retrieve auth state, clear on logout
- Cookie service: cookie management, clearing on logout

### Widget Tests

**Coverage Target:** All dashboard widgets

**Test Files:**
```
test/
└── features/
    ├── auth/
    │   └── presentation/
    │       └── screens/
    │           ├── login_screen_test.dart
    │           └── change_password_screen_test.dart
    └── dashboard/
        ├── admin_dashboard_test.dart
        ├── teacher_dashboard_test.dart
        ├── student_dashboard_test.dart
        └── parent_dashboard_test.dart
```

**Key Test Cases:**
- Login screen: form validation, submit button, error display
- Change password screen: password strength validation, match confirmation
- Dashboard screens: widget rendering, data display, loading states, error states
- Navigation: bottom nav taps, route transitions

### Integration Tests

**Coverage Target:** Critical user journeys

**Test Files:**
```
integration_test/
├── auth_flow_test.dart
├── admin_dashboard_flow_test.dart
├── teacher_dashboard_flow_test.dart
├── student_dashboard_flow_test.dart
└── parent_dashboard_flow_test.dart
```

**Key Test Cases:**
- **Auth Flow:**
  1. Launch app → see login screen
  2. Enter credentials → login success
  3. Password change required → redirect to change password
  4. Change password → redirect to dashboard
  5. Logout → return to login

- **Dashboard Flow (per role):**
  1. Login as role → see role-specific dashboard
  2. Dashboard loads data from backend
  3. Pull to refresh → data reloads
  4. Navigate to different tabs → content changes
  5. Session expires → redirect to login

### End-to-End Tests

**Tool:** Flutter Driver or Patrol

**Scenarios:**
1. **Happy Path (all roles):**
   - Login → Dashboard → Navigate tabs → Logout

2. **Password Change Flow:**
   - First login → Force password change → Dashboard access

3. **Session Expiry:**
   - Login → Wait for session expiry → Attempt action → Redirect to login

4. **Network Error:**
   - Login → Simulate network error → Show error state → Retry

### Security Tests

**Manual Testing:**
1. ✅ CSRF protection (M0 verified)
2. ✅ Cookie handling (M0 verified)
3. ✅ Session validation (M0 verified)
4. [ ] Test with expired JWT cookie
5. [ ] Test with invalid CSRF token
6. [ ] Test cross-role access attempts (ADMIN trying to access TEACHER endpoints)
7. [ ] Test cross-tenant access attempts (school A trying to access school B data)
8. [ ] Test session fixation attacks
9. [ ] Test cookie theft scenarios

### Performance Tests

**Metrics:**
- Dashboard load time < 2 seconds
- API response time < 500ms (95th percentile)
- Smooth scrolling (60 FPS)
- Memory usage < 200MB

**Tools:**
- Flutter DevTools (performance tab)
- Android Profiler
- Xcode Instruments

### Test Data

**Backend Setup:**
- Create test users for each role (ADMIN, TEACHER, STUDENT, PARENT)
- Seed database with sample data:
  - 50 students
  - 10 teachers
  - 5 classes
  - 100 attendance records
  - 50 fee records
  - 20 announcements

**Test Environment:**
- Use dev profile (`SPRING_PROFILES_ACTIVE=dev`)
- Local PostgreSQL database
- Backend running on `localhost:8080`
- Mobile app pointing to local backend

---

## 8. Dependencies and Prerequisites

### Flutter Packages (M1)

```yaml
dependencies:
  flutter:
    sdk: flutter
  
  # State Management
  flutter_riverpod: ^2.6.1
  
  # Routing
  go_router: ^14.8.1
  
  # HTTP Client
  dio: ^5.8.0+1
  dio_cookie_manager: ^3.1.1
  cookie_jar: ^4.0.8
  
  # Local Storage
  shared_preferences: ^2.3.4
  
  # UI Components
  fl_chart: ^0.69.0  # For attendance trend charts
  
  # Utilities
  logger: ^2.0.0
  equatable: ^2.0.5
  json_annotation: ^4.9.0
  freezed_annotation: ^2.4.4
  
  # Date/Time
  intl: ^0.19.0

dev_dependencies:
  flutter_test:
    sdk: flutter
  
  # Code Generation
  build_runner: ^2.4.12
  json_serializable: ^6.8.0
  freezed: ^2.5.7
  
  # Testing
  mocktail: ^1.0.4
  integration_test:
    sdk: flutter
```

### Backend Prerequisites

- ✅ Spring Boot backend running on port 8080
- ✅ PostgreSQL database with schema migrated (Flyway V1-V7)
- ✅ Test users created for each role
- ✅ Sample data seeded
- ✅ CORS configured for mobile app origins

### Development Environment

- ✅ Flutter 3.41.6
- ✅ Dart 3.11.4
- ✅ JDK 21 (for backend)
- ✅ Android Studio / VS Code
- ✅ Android SDK (for Android testing)
- ✅ Xcode (for iOS testing, macOS only)

---

## 9. Risk Assessment and Mitigation

### High Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Backend API changes during M1 | High | Medium | Freeze API contract after M0; use OpenAPI spec |
| Performance issues with large datasets | High | Medium | Implement pagination, lazy loading, caching |
| Session management bugs | High | Low | Comprehensive testing, edge case coverage |
| Role-based access control gaps | Critical | Low | Backend enforces RBAC; mobile is UI layer only |

### Medium Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Chart rendering performance | Medium | Medium | Use fl_chart with optimized data points |
| Multi-child selector complexity | Medium | Low | Start with single child, add multi-child later |
| Navigation state loss on app restart | Medium | Medium | Persist navigation state in SharedPreferences |

### Low Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Design inconsistencies | Low | Low | Follow Stitch design system strictly |
| Test coverage gaps | Low | Medium | Write tests alongside implementation |

---

## 10. Success Criteria

### Functional Criteria

- ✅ User can login with valid credentials
- ✅ User is forced to change password on first login
- ✅ User sees role-specific dashboard after login
- ✅ Dashboard displays real data from backend
- ✅ User can navigate between tabs
- ✅ User can logout successfully
- ✅ Session expiry redirects to login
- ✅ CSRF protection works for all state-changing requests
- ✅ Cookie-based authentication works across app restart

### Non-Functional Criteria

- ✅ Dashboard loads in < 2 seconds
- ✅ Smooth scrolling (60 FPS)
- ✅ Memory usage < 200MB
- ✅ Unit test coverage > 80%
- ✅ Widget test coverage > 70%
- ✅ All critical user journeys tested
- ✅ No critical security vulnerabilities
- ✅ Accessibility compliant (WCAG AA)

### Platform Criteria

- ✅ Works on Android 8.0+ (API 26+)
- ✅ Works on iOS 13.0+
- ✅ Works on Flutter Web (Chrome, Firefox, Safari)
- ✅ Responsive layout for phones and tablets

---

## 11. Deliverables Checklist

### Code Deliverables

- [ ] Role-based route structure implemented
- [ ] ADMIN dashboard screen with all widgets
- [ ] TEACHER dashboard screen with all widgets
- [ ] STUDENT dashboard screen with all widgets
- [ ] PARENT dashboard screen with all widgets
- [ ] Base dashboard layout widget (reusable)
- [ ] Loading skeleton widgets
- [ ] Empty state widgets
- [ ] Error state widgets
- [ ] Pull-to-refresh functionality
- [ ] Chart widgets (attendance trends, performance metrics)

### Test Deliverables

- [ ] Unit tests for auth repository
- [ ] Unit tests for auth provider
- [ ] Unit tests for CSRF interceptor
- [ ] Unit tests for storage service
- [ ] Widget tests for login screen
- [ ] Widget tests for change password screen
- [ ] Widget tests for all 4 dashboards
- [ ] Integration tests for auth flow
- [ ] Integration tests for dashboard flows
- [ ] Security test report

### Documentation Deliverables

- [ ] M1 implementation complete report
- [ ] API integration guide
- [ ] Testing guide
- [ ] Deployment guide
- [ ] Known issues and limitations

### Design Deliverables

- [ ] All dashboards match Stitch design specifications
- [ ] Design system applied consistently
- [ ] Accessibility audit passed
- [ ] Responsive design verified

---

## 12. Timeline and Milestones

| Week | Milestone | Deliverables |
|------|-----------|--------------|
| Week 1 | Phase 1: Foundation | Role-based routing, base dashboard layout, error handling |
| Week 2 | Phase 2: ADMIN Dashboard | Complete ADMIN dashboard with all widgets |
| Week 3 | Phase 3: TEACHER Dashboard | Complete TEACHER dashboard with all widgets |
| Week 4 | Phase 4: STUDENT & PARENT Dashboards | Complete STUDENT and PARENT dashboards |
| Week 5 | Phase 5: Polish & Testing | Final polish, comprehensive testing, bug fixes |

**Total Duration:** 5 weeks  
**Start Date:** Upon approval  
**Target Completion:** 5 weeks from start

---

## 13. Approval and Sign-Off

### Required Approvals

- [ ] Product Owner: Approve M1 scope and timeline
- [ ] Technical Lead: Approve architecture and implementation plan
- [ ] Design Lead: Approve design references and UI specifications
- [ ] QA Lead: Approve testing strategy and success criteria

### Sign-Off

**Prepared by:** AI Agent  
**Date:** 2026-11-05  
**Version:** 1.0

**Approved by:** _________________  
**Date:** _________________

---

## Appendix A: Backend API Endpoints Reference

### Authentication Endpoints

| Endpoint | Method | Auth Required | CSRF Required | Description |
|----------|--------|---------------|---------------|-------------|
| `/api/v1/auth/csrf-token` | GET | ❌ | ❌ | Fetch CSRF token |
| `/api/v1/auth/login` | POST | ❌ | ✅ | Login with credentials |
| `/api/v1/auth/logout` | POST | ✅ | ✅ | Logout and invalidate session |
| `/api/v1/auth/change-password` | POST | ✅ | ✅ | Change password |
| `/api/v1/users/me` | GET | ✅ | ❌ | Get current user (session validation) |

### ADMIN Endpoints (Subset for M1)

| Endpoint | Method | Auth Required | Role | Description |
|----------|--------|---------------|------|-------------|
| `/api/v1/students` | GET | ✅ | ADMIN | List students (paginated) |
| `/api/v1/teachers` | GET | ✅ | ADMIN | List teachers (paginated) |
| `/api/v1/attendance` | GET | ✅ | ADMIN | View attendance records |
| `/api/v1/fees/academic-year/{year}/total-amount` | GET | ✅ | ADMIN | Total fee amount |
| `/api/v1/announcements` | GET | ✅ | ADMIN | List announcements |

### TEACHER Endpoints (Subset for M1)

| Endpoint | Method | Auth Required | Role | Description |
|----------|--------|---------------|------|-------------|
| `/api/v1/teachers/me` | GET | ✅ | TEACHER | Get my profile |
| `/api/v1/teachers/me/classes` | GET | ✅ | TEACHER | Get my classes |
| `/api/v1/teachers/me/students` | GET | ✅ | TEACHER | Get my students |

### STUDENT Endpoints (Subset for M1)

| Endpoint | Method | Auth Required | Role | Description |
|----------|--------|---------------|------|-------------|
| `/api/v1/students/me` | GET | ✅ | STUDENT | Get my profile |
| `/api/v1/students/me/attendance/summary` | GET | ✅ | STUDENT | Get my attendance summary |
| `/api/v1/students/me/results` | GET | ✅ | STUDENT | Get my results |
| `/api/v1/students/me/fees` | GET | ✅ | STUDENT | Get my fees |
| `/api/v1/students/me/timetable` | GET | ✅ | STUDENT | Get my timetable |
| `/api/v1/students/me/exams` | GET | ✅ | STUDENT | Get my exams |

### PARENT Endpoints (Subset for M1)

| Endpoint | Method | Auth Required | Role | Description |
|----------|--------|---------------|------|-------------|
| `/api/v1/parents/me` | GET | ✅ | PARENT | Get my profile |
| `/api/v1/parents/me/children` | GET | ✅ | PARENT | Get my children |
| `/api/v1/parents/me/children/{childId}/attendance/summary` | GET | ✅ | PARENT | Get child attendance |
| `/api/v1/parents/me/children/{childId}/results` | GET | ✅ | PARENT | Get child results |
| `/api/v1/parents/me/children/{childId}/exams` | GET | ✅ | PARENT | Get child exams |
| `/api/v1/parents/me/announcements` | GET | ✅ | PARENT | Get announcements |

---

## Appendix B: Design Token Reference

### Colors

```dart
// Primary
static const Color primaryBlue = Color(0xFF2563EB);
static const Color primaryDark = Color(0xFF1E40AF);
static const Color primaryLight = Color(0xFFDBEAFE);

// Secondary
static const Color successGreen = Color(0xFF10B981);
static const Color warningAmber = Color(0xFFF59E0B);
static const Color errorRed = Color(0xFFEF4444);
static const Color infoCyan = Color(0xFF06B6D4);

// Neutral
static const Color gray900 = Color(0xFF111827);
static const Color gray700 = Color(0xFF374151);
static const Color gray500 = Color(0xFF6B7280);
static const Color gray300 = Color(0xFFD1D5DB);
static const Color gray100 = Color(0xFFF3F4F6);
static const Color white = Color(0xFFFFFFFF);
static const Color background = Color(0xFFF9FAFB);
```

### Typography

```dart
// Font Family
static const String fontFamily = 'Inter';

// Sizes
static const double displaySize = 32.0;
static const double headline1Size = 24.0;
static const double headline2Size = 20.0;
static const double headline3Size = 18.0;
static const double titleSize = 16.0;
static const double bodyLargeSize = 16.0;
static const double bodySize = 14.0;
static const double bodySmallSize = 12.0;
static const double captionSize = 11.0;
static const double metricLargeSize = 28.0;
static const double metricSize = 20.0;

// Weights
static const FontWeight light = FontWeight.w300;
static const FontWeight regular = FontWeight.w400;
static const FontWeight medium = FontWeight.w500;
static const FontWeight semiBold = FontWeight.w600;
static const FontWeight bold = FontWeight.w700;
```

### Spacing

```dart
static const double spacingXS = 4.0;
static const double spacingSM = 8.0;
static const double spacingMDSM = 12.0;
static const double spacingMD = 16.0;
static const double spacingMDLG = 20.0;
static const double spacingLG = 24.0;
static const double spacingXL = 32.0;
static const double spacing2XL = 48.0;
static const double spacing3XL = 64.0;
```

### Border Radius

```dart
static const double radiusSM = 4.0;
static const double radiusMD = 8.0;
static const double radiusLG = 12.0;
static const double radiusFull = 9999.0;
```

### Elevation

```dart
static const List<BoxShadow> elevation1 = [
  BoxShadow(
    color: Colors.black12,
    blurRadius: 3.0,
    offset: Offset(0, 1),
  ),
];

static const List<BoxShadow> elevation2 = [
  BoxShadow(
    color: Colors.black12,
    blurRadius: 6.0,
    offset: Offset(0, 4),
  ),
];

static const List<BoxShadow> elevation3 = [
  BoxShadow(
    color: Colors.black12,
    blurRadius: 15.0,
    offset: Offset(0, 10),
  ),
];
```

---

**End of M1 Implementation Plan**
