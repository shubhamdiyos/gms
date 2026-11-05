# GMS Mobile M1 — Screen Implementation Plan

## Executive Summary

**Version:** 1.0  
**Date:** 2026-11-05  
**Status:** Ready for Product Owner Review  
**Scope:** M1 MVP Implementation (10 screens)

### M1 Objectives

1. Implement 5 role-specific dashboards (ADMIN, ACCOUNTANT, TEACHER, STUDENT, PARENT)
2. Implement 5 critical workflow screens (student list, attendance, fee collection, results, child attendance)
3. Establish navigation architecture with role-based access control
4. Integrate with existing Spring Boot backend APIs
5. Achieve 80%+ test coverage for M1 screens

### M1 Constraints

- **Do NOT modify backend** — Use existing endpoints only
- **Do NOT implement unsupported features** — Defer to M2+
- **Preserve M0 authentication** — Cookie-based auth with CSRF protection
- **₹0 spending** — No paid infrastructure or services
- **Mobile-first** — iOS and Android (Flutter), Web optional

---

## 1. Approved M1 Screen Inventory

### 1.1 Dashboard Screens (5)

| # | Screen | Role | Stitch ID | Priority | Backend Support |
|---|--------|------|-----------|----------|-----------------|
| 1 | ADMIN Dashboard | ADMIN | `5a43c1af7d3740f994885b182c739239` | P0 | ✅ Full |
| 2 | ACCOUNTANT Dashboard | ACCOUNTANT | `29f844d6ae4647a0ac53961bded367bc` | P0 | ✅ Full |
| 3 | TEACHER Dashboard | TEACHER | `ad24999303744cbe8c61b8fdd2302f61` | P0 | ⚠️ Partial |
| 4 | STUDENT Dashboard | STUDENT | `c9bc244168d9429eb1d2401eef143165` | P0 | ✅ Full |
| 5 | PARENT Dashboard | PARENT | `3d8f5426d1d544d18c62d8dd8da2c291` | P0 | ✅ Full |

### 1.2 Workflow Screens (5)

| # | Screen | Role | Stitch ID | Priority | Backend Support |
|---|--------|------|-----------|----------|-----------------|
| 6 | ADMIN Student List | ADMIN | `a38ced53be864fc1ac0e8206e885ab86` | P1 | ✅ Full |
| 7 | TEACHER Mark Attendance | TEACHER | `b082fb915c8b47dfbc5659c257b99203` | P1 | ✅ Full |
| 8 | ACCOUNTANT Fee Collection | ACCOUNTANT | `8fac315be76e4cd3bad949e8d1ddf1b6` | P1 | ✅ Full |
| 9 | STUDENT View Results | STUDENT | `19c3d4437b6749deabefbd574ef5da19` | P1 | ✅ Full |
| 10 | PARENT View Child Attendance | PARENT | `2b09a00995da4d32a6682c329fd67784` | P1 | ⚠️ Partial |

### 1.3 Deferred to M2+

| Screen | Role | Reason |
|--------|------|--------|
| Student Details | ADMIN | Requires new screen design |
| Add/Edit Student | ADMIN | Requires form design |
| Teacher List | ADMIN | Low priority for M1 |
| Payment Confirmation | ACCOUNTANT | Requires new screen design |
| Receipt Generation | ACCOUNTANT | Requires PDF generation |
| Class Selection | TEACHER | Can be simplified |
| Child Selection | PARENT | Can be simplified |
| Exam Details | STUDENT | Requires new screen design |
| Report Card | STUDENT | Requires PDF generation |

---

## 2. Role-to-Screen Mapping

### 2.1 ADMIN Role

**Navigation Tabs:**
1. **Home** → ADMIN Dashboard
2. **Students** → Student List
3. **Teachers** → Placeholder (M2)
4. **Academics** → Placeholder (M2)
5. **More** → Settings, Logout

**M1 Screens:**
- ADMIN Dashboard (metrics, charts, activities)
- Student List (search, filter, pagination)

**Backend Endpoints:**
- `GET /api/v1/dashboard` — Dashboard data
- `GET /api/v1/students` — Student list
- `GET /api/v1/teachers` — Teacher list (placeholder)

---

### 2.2 ACCOUNTANT Role

**Navigation Tabs:**
1. **Dashboard** → ACCOUNTANT Dashboard
2. **Fees** → Fee Collection
3. **Students** → Placeholder (M2)
4. **Reports** → Placeholder (M2)
5. **More** → Settings, Logout

**M1 Screens:**
- ACCOUNTANT Dashboard (financial metrics, transactions)
- Fee Collection (student search, outstanding fees, payment form)

**Backend Endpoints:**
- `GET /api/v1/financial-dashboard` — Financial metrics
- `GET /api/v1/fee-payments` — Recent transactions
- `GET /api/v1/student-fees/student/{id}/outstanding` — Outstanding fees
- `POST /api/v1/fee-payments` — Record payment

---

### 2.3 TEACHER Role

**Navigation Tabs:**
1. **Home** → TEACHER Dashboard
2. **Classes** → Placeholder (M2)
3. **Attendance** → Mark Attendance
4. **Exams** → Placeholder (M2)
5. **More** → Settings, Logout

**M1 Screens:**
- TEACHER Dashboard (class overview, schedule) ⚠️ Requires backend work
- Mark Attendance (student list, status toggles, submit)

**Backend Endpoints:**
- `GET /api/v1/teachers/me` — Teacher profile
- `GET /api/v1/teachers/me/classes` — Assigned classes
- `GET /api/v1/attendance/class/{id}/date/{date}` — Class attendance
- `POST /api/v1/attendance/bulk` — Submit attendance

---

### 2.4 STUDENT Role

**Navigation Tabs:**
1. **Home** → STUDENT Dashboard
2. **Schedule** → Placeholder (M2)
3. **Results** → View Results
4. **Fees** → Placeholder (M2)
5. **More** → Settings, Logout

**M1 Screens:**
- STUDENT Dashboard (profile, metrics, schedule, deadlines)
- View Results (exam list, scores, grades)

**Backend Endpoints:**
- `GET /api/v1/students/me` — Student profile
- `GET /api/v1/students/me/results` — Exam results
- `GET /api/v1/students/me/attendance/summary` — Attendance metric
- `GET /api/v1/students/me/fees/summary` — Fee metric

---

### 2.5 PARENT Role

**Navigation Tabs:**
1. **Home** → PARENT Dashboard
2. **Attendance** → View Child Attendance
3. **Results** → Placeholder (M2)
4. **Fees** → Placeholder (M2)
5. **More** → Settings, Logout

**M1 Screens:**
- PARENT Dashboard (child overview, metrics, notices)
- View Child Attendance ⚠️ Requires backend work

**Backend Endpoints:**
- `GET /api/v1/parents/children` — Children list
- `GET /api/v1/parents/children/{id}/attendance` — Child attendance
- `GET /api/v1/parents/children/{id}/results` — Child results
- `GET /api/v1/announcements?audience=PARENT` — School notices

---

## 3. Stitch Design References

### 3.1 Design System

**Project:** GMS — Next Generation School Management Platform  
**Project ID:** `7439120177506880457`  
**Design System:** GMS Professional — Modern Education Platform  
**Asset ID:** `assets/5382227573212513983`

### 3.2 Color Tokens

```dart
// Primary Colors
const Color primaryBlue = Color(0xFF2563EB);
const Color primaryDark = Color(0xFF1E40AF);
const Color primaryLight = Color(0xFFDBEAFE);

// Secondary Colors
const Color successGreen = Color(0xFF10B981);
const Color warningAmber = Color(0xFFF59E0B);
const Color errorRed = Color(0xFFEF4444);
const Color infoCyan = Color(0xFF06B6D4);

// Neutral Colors
const Color gray900 = Color(0xFF111827);
const Color gray700 = Color(0xFF374151);
const Color gray500 = Color(0xFF6B7280);
const Color gray300 = Color(0xFFD1D5DB);
const Color gray100 = Color(0xFFF3F4F6);
const Color white = Color(0xFFFFFFFF);
const Color background = Color(0xFFF9FAFB);
```

### 3.3 Typography Tokens

```dart
// Font Family
const String fontFamily = 'Inter';

// Type Scale
const double displaySize = 32;
const double headline1Size = 24;
const double headline2Size = 20;
const double headline3Size = 18;
const double titleSize = 16;
const double bodyLargeSize = 16;
const double bodySize = 14;
const double bodySmallSize = 12;
const double captionSize = 11;
const double metricLargeSize = 28;
const double metricSize = 20;
```

### 3.4 Spacing Tokens

```dart
// Spacing (4px grid)
const double spacingXs = 4;
const double spacingSm = 8;
const double spacingMdSm = 12;
const double spacingMd = 16;
const double spacingMdLg = 20;
const double spacingLg = 24;
const double spacingXl = 32;
const double spacing2xl = 48;
const double spacing3xl = 64;

// Border Radius
const double radiusSm = 4;
const double radiusMd = 8;
const double radiusLg = 12;
const double radiusFull = 9999;
```

---

## 4. Navigation Architecture

### 4.1 Router Configuration

```dart
final routerProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    initialLocation: '/login',
    redirect: (context, state) {
      final authState = ref.read(authProvider);
      
      // Public routes
      if (state.uri.path == '/login') {
        return authState.isAuthenticated ? '/home' : null;
      }
      
      // Protected routes
      if (!authState.isAuthenticated) {
        return '/login';
      }
      
      // Role-based routing
      final role = authState.user?.role;
      if (role == null) return '/login';
      
      // Default home based on role
      if (state.uri.path == '/home') {
        return switch (role) {
          UserRole.ADMIN => '/admin/dashboard',
          UserRole.ACCOUNTANT => '/accountant/dashboard',
          UserRole.TEACHER => '/teacher/dashboard',
          UserRole.STUDENT => '/student/dashboard',
          UserRole.PARENT => '/parent/dashboard',
          _ => '/login',
        };
      }
      
      return null;
    },
    routes: [
      // Auth routes
      GoRoute(path: '/login', builder: (_, __) => LoginScreen()),
      
      // ADMIN routes
      GoRoute(
        path: '/admin',
        builder: (_, __) => AdminShell(),
        routes: [
          GoRoute(path: 'dashboard', builder: (_, __) => AdminDashboard()),
          GoRoute(path: 'students', builder: (_, __) => AdminStudentList()),
        ],
      ),
      
      // ACCOUNTANT routes
      GoRoute(
        path: '/accountant',
        builder: (_, __) => AccountantShell(),
        routes: [
          GoRoute(path: 'dashboard', builder: (_, __) => AccountantDashboard()),
          GoRoute(path: 'fees', builder: (_, __) => AccountantFeeCollection()),
        ],
      ),
      
      // TEACHER routes
      GoRoute(
        path: '/teacher',
        builder: (_, __) => TeacherShell(),
        routes: [
          GoRoute(path: 'dashboard', builder: (_, __) => TeacherDashboard()),
          GoRoute(path: 'attendance', builder: (_, __) => TeacherMarkAttendance()),
        ],
      ),
      
      // STUDENT routes
      GoRoute(
        path: '/student',
        builder: (_, __) => StudentShell(),
        routes: [
          GoRoute(path: 'dashboard', builder: (_, __) => StudentDashboard()),
          GoRoute(path: 'results', builder: (_, __) => StudentResults()),
        ],
      ),
      
      // PARENT routes
      GoRoute(
        path: '/parent',
        builder: (_, __) => ParentShell(),
        routes: [
          GoRoute(path: 'dashboard', builder: (_, __) => ParentDashboard()),
          GoRoute(path: 'attendance', builder: (_, __) => ParentChildAttendance()),
        ],
      ),
    ],
  );
});
```

### 4.2 Role-Based Access Control

```dart
enum UserRole {
  ADMIN,
  ACCOUNTANT,
  TEACHER,
  STUDENT,
  PARENT,
  SUPERADMIN; // Web-only, excluded from mobile
  
  bool canAccess(String route) {
    return switch (this) {
      ADMIN => route.startsWith('/admin'),
      ACCOUNTANT => route.startsWith('/accountant'),
      TEACHER => route.startsWith('/teacher'),
      STUDENT => route.startsWith('/student'),
      PARENT => route.startsWith('/parent'),
      SUPERADMIN => false, // Not allowed on mobile
    };
  }
}
```

---

## 5. Implementation Sequence

### Phase 1: Foundation (Week 1)

**Objective:** Set up project structure, design system, and navigation

**Tasks:**
1. Create Flutter project structure (if not exists)
2. Implement design system tokens (colors, typography, spacing)
3. Set up GoRouter with role-based routing
4. Create shell widgets for each role (bottom navigation)
5. Implement authentication flow (login, logout, session validation)

**Deliverables:**
- `lib/core/theme/app_colors.dart`
- `lib/core/theme/app_typography.dart`
- `lib/core/theme/app_spacing.dart`
- `lib/app/router.dart`
- `lib/features/auth/presentation/screens/login_screen.dart`
- `lib/features/shell/admin_shell.dart`
- `lib/features/shell/accountant_shell.dart`
- `lib/features/shell/teacher_shell.dart`
- `lib/features/shell/student_shell.dart`
- `lib/features/shell/parent_shell.dart`

**Testing:**
- Unit tests for theme tokens
- Widget tests for shell navigation
- Integration tests for auth flow

---

### Phase 2: Dashboards (Week 2-3)

**Objective:** Implement all 5 role dashboards

**Tasks:**
1. ADMIN Dashboard (metrics, charts, activities)
2. ACCOUNTANT Dashboard (financial metrics, transactions)
3. TEACHER Dashboard (class overview, schedule) ⚠️ Backend dependency
4. STUDENT Dashboard (profile, metrics, schedule)
5. PARENT Dashboard (child overview, metrics, notices)

**Deliverables:**
- `lib/features/admin/presentation/screens/dashboard_screen.dart`
- `lib/features/accountant/presentation/screens/dashboard_screen.dart`
- `lib/features/teacher/presentation/screens/dashboard_screen.dart`
- `lib/features/student/presentation/screens/dashboard_screen.dart`
- `lib/features/parent/presentation/screens/dashboard_screen.dart`

**Backend Dependencies:**
- Teacher dashboard requires `/api/v1/teachers/me/schedule` endpoint

**Testing:**
- Unit tests for dashboard view models
- Widget tests for dashboard UI
- Integration tests for API calls

---

### Phase 3: Workflow Screens (Week 4-5)

**Objective:** Implement 5 critical workflow screens

**Tasks:**
1. ADMIN Student List (search, filter, pagination)
2. TEACHER Mark Attendance (student list, status toggles, submit)
3. ACCOUNTANT Fee Collection (student search, payment form)
4. STUDENT View Results (exam list, scores, grades)
5. PARENT View Child Attendance ⚠️ Backend dependency

**Deliverables:**
- `lib/features/admin/presentation/screens/student_list_screen.dart`
- `lib/features/teacher/presentation/screens/mark_attendance_screen.dart`
- `lib/features/accountant/presentation/screens/fee_collection_screen.dart`
- `lib/features/student/presentation/screens/results_screen.dart`
- `lib/features/parent/presentation/screens/child_attendance_screen.dart`

**Backend Dependencies:**
- Parent attendance requires calendar endpoint

**Testing:**
- Unit tests for workflow view models
- Widget tests for workflow UI
- Integration tests for form submissions

---

### Phase 4: Polish & Testing (Week 6)

**Objective:** Final testing, bug fixes, and performance optimization

**Tasks:**
1. Add loading states (skeleton screens)
2. Add error states (error messages, retry buttons)
3. Add empty states (illustrations, guidance)
4. Performance optimization (lazy loading, caching)
5. Accessibility audit (contrast, touch targets, screen reader)
6. User acceptance testing

**Deliverables:**
- Loading state widgets
- Error state widgets
- Empty state widgets
- Performance profiling report
- Accessibility audit report

**Testing:**
- Full regression test suite
- Performance benchmarks
- Accessibility compliance check

---

## 6. Testing Strategy

### 6.1 Unit Tests

**Coverage Target:** 80%+

**What to Test:**
- View model logic (state management, business rules)
- API service methods (request/response mapping)
- Utility functions (date formatting, validation)

**Example:**
```dart
test('should calculate attendance percentage correctly', () {
  final viewModel = AttendanceViewModel();
  viewModel.calculatePercentage(38, 42);
  expect(viewModel.attendancePercentage, 90.48);
});
```

---

### 6.2 Widget Tests

**Coverage Target:** All screens

**What to Test:**
- Screen renders correctly
- User interactions trigger correct actions
- Loading/error/empty states display correctly

**Example:**
```dart
testWidgets('should display student list', (tester) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        studentListProvider.overrideWith((ref) => mockViewModel),
      ],
      child: MaterialApp(home: StudentListScreen()),
    ),
  );
  
  expect(find.text('Aarav Mehta'), findsOneWidget);
  expect(find.text('Class 10-A'), findsOneWidget);
});
```

---

### 6.3 Integration Tests

**Coverage Target:** Critical workflows

**What to Test:**
- End-to-end user journeys
- API integration (real backend or mock)
- Authentication flow
- Form submissions

**Example:**
```dart
testWidgets('should complete attendance marking workflow', (tester) async {
  // Login
  await tester.tap(find.text('Login'));
  await tester.pumpAndSettle();
  
  // Navigate to attendance
  await tester.tap(find.text('Attendance'));
  await tester.pumpAndSettle();
  
  // Mark attendance
  await tester.tap(find.byType(Checkbox).first);
  await tester.tap(find.text('Submit'));
  await tester.pumpAndSettle();
  
  // Verify success
  expect(find.text('Attendance recorded'), findsOneWidget);
});
```

---

### 6.4 Security Tests

**What to Test:**
- CSRF token handling
- Cookie-based authentication
- Role-based access control
- Session validation

**Example:**
```dart
test('should reject request without CSRF token', () async {
  final client = ApiClient();
  await client.clearCsrfToken();
  
  expect(
    () => client.post('/api/v1/attendance/bulk', data: {}),
    throwsA(isA<CsrfValidationException>()),
  );
});
```

---

## 7. Risk Mitigation

### 7.1 Backend Dependencies

**Risk:** Teacher dashboard and parent attendance require backend endpoints that may not exist.

**Mitigation:**
1. Verify endpoints exist before starting implementation
2. If missing, implement simplified version using existing endpoints
3. Document gaps and defer to M2 if critical

---

### 7.2 Design Gaps

**Risk:** Missing screenshots (TEACHER Dashboard, PARENT Attendance) and mislabeled files.

**Mitigation:**
1. Request corrected screenshots from design team
2. Use existing screens as reference for consistency
3. Document assumptions in code comments

---

### 7.3 Scope Creep

**Risk:** Stakeholders request additional features during M1.

**Mitigation:**
1. Freeze M1 scope before implementation
2. Document all deferred features in M2 backlog
3. Require product owner approval for scope changes

---

## 8. Success Criteria

### 8.1 Functional Requirements

- [ ] All 5 dashboards display correct data
- [ ] All 5 workflow screens complete their primary function
- [ ] Navigation works correctly for all roles
- [ ] Authentication and session validation work
- [ ] CSRF protection is enforced

### 8.2 Non-Functional Requirements

- [ ] 80%+ test coverage
- [ ] All screens load in <2 seconds
- [ ] No crashes or ANRs in testing
- [ ] WCAG AA contrast compliance (or documented exceptions)
- [ ] Touch targets ≥44x44px

### 8.3 Integration Requirements

- [ ] All API calls use correct endpoints
- [ ] Error handling follows backend error format
- [ ] Pagination works for all list screens
- [ ] Search and filter work where applicable

---

## 9. Deliverables Checklist

### Code Deliverables

- [ ] Design system tokens (colors, typography, spacing)
- [ ] Router configuration with role-based access
- [ ] 5 role shell widgets (bottom navigation)
- [ ] 5 dashboard screens
- [ ] 5 workflow screens
- [ ] API service layer (Dio client, interceptors)
- [ ] State management (Riverpod providers)
- [ ] Unit tests (80%+ coverage)
- [ ] Widget tests (all screens)
- [ ] Integration tests (critical workflows)

### Documentation Deliverables

- [ ] `GMS_MOBILE_VISUAL_REVIEW.md` ✅
- [ ] `GMS_WORKFLOW_SCREEN_GAP_MATRIX.md` ✅
- [ ] `GMS_SCREEN_API_TRACEABILITY.md` ✅
- [ ] `GMS_M1_SCREEN_IMPLEMENTATION_PLAN.md` ✅ (this document)
- [ ] API endpoint documentation
- [ ] Testing strategy document
- [ ] Deployment guide

---

## 10. Next Steps

### Immediate Actions

1. **Product Owner Review** — Approve M1 scope and implementation plan
2. **Backend Verification** — Confirm teacher schedule and parent calendar endpoints exist
3. **Design Clarification** — Request missing screenshots (TEACHER Dashboard, PARENT Attendance)
4. **File Naming Correction** — Rename mislabeled screenshot files

### Pre-Implementation Checklist

- [ ] Product owner approves M1 scope
- [ ] Backend endpoints verified
- [ ] Missing screenshots obtained
- [ ] Development environment set up
- [ ] CI/CD pipeline configured
- [ ] Test devices available (iOS, Android)

---

## 11. Conclusion

**M1 Scope:** 10 screens (5 dashboards + 5 workflows)  
**Timeline:** 6 weeks  
**Team:** 1-2 Flutter developers  
**Backend Changes:** None (use existing endpoints)  
**Budget:** ₹0 (no paid infrastructure)

**Recommendation:** Proceed with M1 implementation after product owner approval.

**Risks:**
- 2 screens missing from design package (20%)
- 2 backend endpoints unverified
- Teacher homework and payment gateway deferred to M2+

**Mitigation:**
- Use existing screens as reference
- Simplify workflows where endpoints missing
- Document all deferred features

---

**Status:** Ready for Product Owner Approval

**Awaiting sign-off before beginning implementation.**
