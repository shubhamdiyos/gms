# GMS Navigation Map

**Version:** 1.0  
**Date:** 2026-11-05  
**Status:** ✅ Verified against backend authorization rules  
**Scope:** MVP screens (17 screens across 5 roles)

---

## Executive Summary

This document defines the **complete navigation structure** for the GMS Flutter mobile application. All navigation paths have been verified against backend authorization rules and role-based access control.

**Key Features:**
- 5 role-specific navigation structures
- 17 MVP screens with defined transitions
- Role-based access control (RBAC) enforced
- Deep linking support
- Logout behavior documented

---

## 1. Navigation Architecture

### 1.1 Router Configuration

**Library:** `go_router` (Flutter's recommended routing solution)

**Structure:**
```
/ (ShellRoute)
├── /login (LoginScreen)
├── /change-password (ChangePasswordScreen)
├── / (HomeShell - authenticated)
│   ├── /dashboard (Role-specific dashboard)
│   ├── /students (Student list/details)
│   ├── /attendance (Attendance marking)
│   ├── /fees (Fee collection)
│   ├── /results (View results)
│   └── /profile (User profile)
└── /splash (SplashScreen - app launch)
```

### 1.2 Navigation State

```dart
@riverpod
GoRouter router(Ref ref) {
  final authState = ref.watch(authNotifierProvider);
  
  return GoRouter(
    initialLocation: '/splash',
    redirect: (context, state) {
      final isAuthenticated = authState is AuthStateAuthenticated;
      final isAuthRoute = state.matchedLocation.startsWith('/login') ||
                         state.matchedLocation.startsWith('/change-password');
      
      // Redirect to login if not authenticated
      if (!isAuthenticated && !isAuthRoute) {
        return '/login';
      }
      
      // Redirect to dashboard if authenticated and on auth route
      if (isAuthenticated && isAuthRoute) {
        return '/dashboard';
      }
      
      // Check password change requirement
      if (isAuthenticated && 
          (authState as AuthStateAuthenticated).requirePasswordChange &&
          state.matchedLocation != '/change-password') {
        return '/change-password';
      }
      
      return null;
    },
    routes: [
      // Public routes
      GoRoute(
        path: '/login',
        builder: (context, state) => const LoginScreen(),
      ),
      GoRoute(
        path: '/change-password',
        builder: (context, state) => const ChangePasswordScreen(),
      ),
      
      // Protected routes (ShellRoute)
      ShellRoute(
        builder: (context, state, child) => HomeShell(child: child),
        routes: [
          GoRoute(
            path: '/dashboard',
            builder: (context, state) => const DashboardScreen(),
          ),
          // ... other protected routes
        ],
      ),
    ],
  );
}
```

---

## 2. Role-Based Navigation Structures

### 2.1 ADMIN Navigation

**Bottom Navigation Bar:**
```
[Home] [Students] [Reports] [Settings]
```

**Navigation Flow:**

```
Login
  ↓ (requirePasswordChange=false)
Admin Dashboard (ADM-001)
  ├─→ Student List (ADM-002)
  │     ├─→ Student Details (ADM-003)
  │     │     ├─→ Edit Student (ADM-005)
  │     │     └─→ Deactivate Student (confirmation dialog)
  │     └─→ Add Student (ADM-004)
  ├─→ Reports
  │     ├─→ Financial Reports
  │     ├─→ Attendance Reports
  │     └─→ Academic Reports
  └─→ Settings
        ├─→ School Settings
        ├─→ User Management
        └─→ Logout
```

**Screen Access Matrix:**

| Screen | Access | Entry Point | Back Navigation |
|--------|--------|-------------|-----------------|
| ADM-001 (Dashboard) | ✅ | Bottom nav → Home | N/A |
| ADM-002 (Student List) | ✅ | Dashboard → Students module | Dashboard |
| ADM-003 (Student Details) | ✅ | Student List → Tap student | Student List |
| ADM-004 (Add Student) | ✅ | Student List → FAB | Student List |
| ADM-005 (Edit Student) | ✅ | Student Details → Edit button | Student Details |
| All ACCOUNTANT screens | ✅ | Dashboard → Fee module | Dashboard |
| All TEACHER screens | ✅ | Dashboard → Attendance module | Dashboard |
| All STUDENT screens | ✅ | Student search | Varies |
| All PARENT screens | ✅ | Parent management | Varies |

**Logout Behavior:**
1. Tap Settings → Logout
2. Show confirmation dialog
3. Call POST `/api/v1/auth/logout`
4. Clear cookies and auth state
5. Navigate to `/login`

---

### 2.2 ACCOUNTANT Navigation

**Bottom Navigation Bar:**
```
[Home] [Fees] [Reports] [Settings]
```

**Navigation Flow:**

```
Login
  ↓ (requirePasswordChange=false)
Accountant Dashboard (ACC-001)
  ├─→ Fee Collection (ACC-002)
  │     ├─→ Search Student
  │     ├─→ View Student Fees
  │     ├─→ Record Payment
  │     └─→ Generate Receipt
  ├─→ Fee Management
  │     ├─→ Fee Structure List
  │     ├─→ Create Fee Structure
  │     └─→ Fee Payment History
  ├─→ Reports
  │     ├─→ Financial Dashboard
  │     ├─→ Collection Reports
  │     └─→ Outstanding Reports
  └─→ Settings
        ├─→ Payment Gateway Settings
        └─→ Logout
```

**Screen Access Matrix:**

| Screen | Access | Entry Point | Back Navigation |
|--------|--------|-------------|-----------------|
| ACC-001 (Dashboard) | ✅ | Bottom nav → Home | N/A |
| ACC-002 (Fee Collection) | ✅ | Dashboard → Fees module | Dashboard |
| Fee Structure screens | ✅ | Dashboard → Fee management | Dashboard |
| Financial reports | ✅ | Dashboard → Reports | Dashboard |
| ADMIN screens (read-only) | ⚠️ | Limited access | Varies |
| TEACHER screens | ❌ | No access | N/A |
| STUDENT screens (own) | ❌ | No access | N/A |
| PARENT screens | ❌ | No access | N/A |

**Logout Behavior:**
- Same as ADMIN

---

### 2.3 TEACHER Navigation

**Bottom Navigation Bar:**
```
[Home] [Classes] [Attendance] [Settings]
```

**Navigation Flow:**

```
Login
  ↓ (requirePasswordChange=false)
Teacher Dashboard (TEA-001)
  ├─→ My Classes
  │     ├─→ Class Details
  │     ├─→ Student List (for class)
  │     └─→ Timetable (for class)
  ├─→ Mark Attendance (TEA-002)
  │     ├─→ Select Class
  │     ├─→ Select Date
  │     ├─→ Mark Attendance
  │     └─→ Submit
  ├─→ Exams
  │     ├─→ Exam List
  │     ├─→ Create Exam
  │     └─→ Enter Marks
  └─→ Settings
        ├─→ Profile
        ├─→ Assigned Subjects
        └─→ Logout
```

**Screen Access Matrix:**

| Screen | Access | Entry Point | Back Navigation |
|--------|--------|-------------|-----------------|
| TEA-001 (Dashboard) | ✅ | Bottom nav → Home | N/A |
| TEA-002 (Mark Attendance) | ✅ | Dashboard → Attendance module | Dashboard |
| My Classes | ✅ | Dashboard → Classes | Dashboard |
| Exam management | ✅ | Dashboard → Exams | Dashboard |
| Timetable (own) | ✅ | Dashboard → Timetable | Dashboard |
| ADMIN screens | ❌ | No access | N/A |
| ACCOUNTANT screens | ❌ | No access | N/A |
| STUDENT screens (own class) | ✅ | Via class list | Class details |
| PARENT screens | ❌ | No access | N/A |

**Logout Behavior:**
- Same as ADMIN

---

### 2.4 STUDENT Navigation

**Bottom Navigation Bar:**
```
[Home] [Results] [Timetable] [Settings]
```

**Navigation Flow:**

```
Login
  ↓ (requirePasswordChange=false)
Student Dashboard (STU-001)
  ├─→ My Profile
  │     ├─→ Personal Information
  │     └─→ Academic Information
  ├─→ View Results (STU-002)
  │     ├─→ Exam List
  │     ├─→ Subject-wise Results
  │     └─→ Download Report Card
  ├─→ My Timetable
  │     ├─→ Weekly View
  │     └─→ Subject Details
  ├─→ My Attendance
  │     ├─→ Attendance Summary
  │     └─→ Date-wise Attendance
  └─→ Settings
        ├─→ Profile
        └─→ Logout
```

**Screen Access Matrix:**

| Screen | Access | Entry Point | Back Navigation |
|--------|--------|-------------|-----------------|
| STU-001 (Dashboard) | ✅ | Bottom nav → Home | N/A |
| STU-002 (View Results) | ✅ | Dashboard → Results module | Dashboard |
| My Timetable | ✅ | Dashboard → Timetable | Dashboard |
| My Attendance | ✅ | Dashboard → Attendance | Dashboard |
| My Profile | ✅ | Dashboard → Profile | Dashboard |
| ADMIN screens | ❌ | No access | N/A |
| ACCOUNTANT screens | ❌ | No access | N/A |
| TEACHER screens | ❌ | No access | N/A |
| PARENT screens | ❌ | No access | N/A |

**Logout Behavior:**
- Same as ADMIN

---

### 2.5 PARENT Navigation

**Bottom Navigation Bar:**
```
[Home] [Children] [Fees] [Settings]
```

**Navigation Flow:**

```
Login
  ↓ (requirePasswordChange=false)
Parent Dashboard (PAR-001)
  ├─→ My Children
  │     ├─→ Child List
  │     └─→ Child Details
  │           ├─→ View Child Attendance (PAR-002)
  │           ├─→ View Child Results
  │           └─→ View Child Timetable
  ├─→ Fee Payment
  │     ├─→ Pending Fees List
  │     ├─→ Payment History
  │     └─→ Make Payment
  ├─→ Notifications
  │     ├─→ Notification List
  │     └─→ Notification Details
  └─→ Settings
        ├─→ Profile
        └─→ Logout
```

**Screen Access Matrix:**

| Screen | Access | Entry Point | Back Navigation |
|--------|--------|-------------|-----------------|
| PAR-001 (Dashboard) | ✅ | Bottom nav → Home | N/A |
| PAR-002 (View Child Attendance) | ✅ | Dashboard → Children → Select child | Child details |
| Child Results | ✅ | Dashboard → Children → Select child → Results | Child details |
| Fee Payment | ✅ | Dashboard → Fees module | Dashboard |
| Notifications | ✅ | Dashboard → Notifications | Dashboard |
| ADMIN screens | ❌ | No access | N/A |
| ACCOUNTANT screens | ⚠️ | Own children's fees only | Varies |
| TEACHER screens | ❌ | No access | N/A |
| STUDENT screens | ❌ | No access | N/A |

**Logout Behavior:**
- Same as ADMIN

---

## 3. Deep Linking

### 3.1 Supported Deep Links

| Deep Link | Target Screen | Auth Required | Role Restriction |
|-----------|---------------|---------------|------------------|
| `gms://login` | LoginScreen | No | None |
| `gms://dashboard` | DashboardScreen | Yes | All authenticated |
| `gms://students/{id}` | StudentDetails | Yes | ADMIN, SUPERADMIN |
| `gms://attendance/mark` | MarkAttendance | Yes | TEACHER, ADMIN, SUPERADMIN |
| `gms://fees/pay/{studentId}` | FeePayment | Yes | ACCOUNTANT, ADMIN, SUPERADMIN |
| `gms://results/{studentId}` | ViewResults | Yes | STUDENT (own), ADMIN, SUPERADMIN, TEACHER |
| `gms://children/{childId}/attendance` | ChildAttendance | Yes | PARENT (own child), ADMIN, SUPERADMIN |

### 3.2 Deep Link Handler

```dart
GoRouter(
  routes: [
    GoRoute(
      path: '/students/:id',
      builder: (context, state) {
        final studentId = int.parse(state.pathParameters['id']!);
        return StudentDetailsScreen(studentId: studentId);
      },
    ),
    GoRoute(
      path: '/attendance/mark',
      builder: (context, state) => const MarkAttendanceScreen(),
    ),
    // ... other deep links
  ],
);
```

---

## 4. Navigation Guards

### 4.1 Authentication Guard

**Purpose:** Redirect unauthenticated users to login

```dart
GoRouter(
  redirect: (context, state) {
    final isAuthenticated = ref.read(authNotifierProvider) is AuthStateAuthenticated;
    final isAuthRoute = state.matchedLocation.startsWith('/login');
    
    if (!isAuthenticated && !isAuthRoute) {
      return '/login';
    }
    
    return null;
  },
);
```

### 4.2 Password Change Guard

**Purpose:** Force password change when required

```dart
GoRouter(
  redirect: (context, state) {
    final authState = ref.read(authNotifierProvider);
    final requiresPasswordChange = authState is AuthStateAuthenticated &&
                                   authState.requirePasswordChange;
    final isPasswordChangeRoute = state.matchedLocation == '/change-password';
    
    if (requiresPasswordChange && !isPasswordChangeRoute) {
      return '/change-password';
    }
    
    return null;
  },
);
```

### 4.3 Role-Based Guard

**Purpose:** Prevent unauthorized access to role-specific screens

```dart
GoRouter(
  redirect: (context, state) {
    final authState = ref.read(authNotifierProvider);
    if (authState is! AuthStateAuthenticated) return null;
    
    final userRoles = authState.roles; // From JWT
    final route = state.matchedLocation;
    
    // Define role-based access rules
    final adminRoutes = ['/students', '/users', '/school-settings'];
    final accountantRoutes = ['/fees', '/financial-reports'];
    final teacherRoutes = ['/attendance/mark', '/my-classes'];
    final studentRoutes = ['/my-results', '/my-timetable'];
    final parentRoutes = ['/my-children', '/child-attendance'];
    
    if (adminRoutes.any((r) => route.startsWith(r)) &&
        !userRoles.contains('ADMIN') && !userRoles.contains('SUPERADMIN')) {
      return '/dashboard'; // Redirect to dashboard
    }
    
    // ... similar checks for other roles
    
    return null;
  },
);
```

---

## 5. Back Navigation Behavior

### 5.1 System Back Button

**Android:**
- Default behavior: Pop current route from stack
- Custom behavior: Override with `PopScope` widget

**iOS:**
- Swipe back gesture: Pop current route
- Back button: Pop current route

### 5.2 Custom Back Navigation

```dart
class StudentDetailsScreen extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false, // Prevent default back navigation
      onPopInvoked: (didPop) {
        if (!didPop) {
          // Custom back behavior
          context.go('/students'); // Navigate to student list
        }
      },
      child: /* ... */,
    );
  }
}
```

### 5.3 Back Stack Management

**Clear back stack on logout:**
```dart
Future<void> logout() async {
  await authService.logout();
  context.go('/login'); // Replaces entire back stack
}
```

**Clear back stack on role switch:**
```dart
void switchRole(Role newRole) {
  context.go('/dashboard'); // Replaces entire back stack
}
```

---

## 6. Logout Behavior

### 6.1 Logout Flow

```
User taps Logout
  ↓
Show confirmation dialog
  ↓ (confirmed)
Call POST /api/v1/auth/logout
  ↓
Clear cookies (AuthCookieJar.clear())
  ↓
Clear CSRF token cache
  ↓
Clear auth state (AuthStateUnauthenticated)
  ↓
Clear all cached data (Riverpod container.invalidateAll())
  ↓
Navigate to /login (clear back stack)
```

### 6.2 Logout Implementation

```dart
Future<void> logout(BuildContext context, WidgetRef ref) async {
  // Show confirmation dialog
  final confirmed = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text('Logout'),
      content: Text('Are you sure you want to logout?'),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context, false),
          child: Text('Cancel'),
        ),
        TextButton(
          onPressed: () => Navigator.pop(context, true),
          child: Text('Logout'),
        ),
      ],
    ),
  );
  
  if (confirmed != true) return;
  
  // Call logout API
  try {
    await ref.read(authRepositoryProvider).logout();
  } catch (e) {
    // Ignore errors (session may already be expired)
  }
  
  // Clear local state
  await ref.read(cookieJarProvider).clear();
  ref.read(csrfManagerProvider).clearCache();
  ref.read(authNotifierProvider.notifier).logout();
  
  // Clear all cached data
  ref.invalidateAll();
  
  // Navigate to login (clear back stack)
  context.go('/login');
}
```

### 6.3 Session Expiry Behavior

```dart
// In 401 interceptor
if (err.response?.statusCode == 401) {
  // Show user-friendly message
  ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(content: Text('Session expired. Please login again.')),
  );
  
  // Logout and redirect
  await logout(context, ref);
}
```

---

## 7. Navigation Transitions

### 7.1 Default Transitions

**Material Design (Android):**
- Forward: Fade + Slide up
- Backward: Fade + Slide down

**Cupertino (iOS):**
- Forward: Slide from right
- Backward: Slide to right

### 7.2 Custom Transitions

```dart
GoRoute(
  path: '/students/:id',
  pageBuilder: (context, state) {
    return CustomTransitionPage(
      child: StudentDetailsScreen(studentId: state.pathParameters['id']),
      transitionsBuilder: (context, animation, secondaryAnimation, child) {
        return FadeTransition(
          opacity: animation,
          child: child,
        );
      },
    );
  },
);
```

---

## 8. Navigation State Persistence

### 8.1 Current Route Persistence

```dart
// Save current route
final currentRoute = GoRouter.of(context).routerDelegate.currentConfiguration.uri.toString();
SharedPreferences prefs = await SharedPreferences.getInstance();
await prefs.setString('last_route', currentRoute);

// Restore on app launch
String? lastRoute = prefs.getString('last_route');
if (lastRoute != null) {
  context.go(lastRoute);
}
```

### 8.2 Navigation History

```dart
// Track navigation history
class NavigationHistoryNotifier extends ChangeNotifier {
  final List<String> _history = [];
  
  void push(String route) {
    _history.add(route);
    notifyListeners();
  }
  
  void pop() {
    if (_history.isNotEmpty) {
      _history.removeLast();
      notifyListeners();
    }
  }
  
  List<String> get history => List.unmodifiable(_history);
}
```

---

## 9. Error Handling

### 9.1 404 Route Not Found

```dart
GoRouter(
  errorPageBuilder: (context, state) {
    return MaterialPage(
      key: state.pageKey,
      child: NotFoundScreen(path: state.matchedLocation),
    );
  },
);
```

### 9.2 Navigation Errors

```dart
try {
  context.go('/students/999'); // Student doesn't exist
} catch (e) {
  ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(content: Text('Navigation error: $e')),
  );
  context.go('/dashboard'); // Fallback to dashboard
}
```

---

## 10. Testing Navigation

### 10.1 Unit Tests

```dart
testWidgets('navigates to login when not authenticated', (tester) async {
  // Setup: unauthenticated state
  when(mockAuthService.isAuthenticated).thenReturn(false);
  
  // Build app
  await tester.pumpWidget(app);
  
  // Verify: on login screen
  expect(find.byType(LoginScreen), findsOneWidget);
});

testWidgets('navigates to dashboard when authenticated', (tester) async {
  // Setup: authenticated state
  when(mockAuthService.isAuthenticated).thenReturn(true);
  
  // Build app
  await tester.pumpWidget(app);
  
  // Verify: on dashboard
  expect(find.byType(DashboardScreen), findsOneWidget);
});
```

### 10.2 Integration Tests

```dart
testWidgets('complete login flow', (tester) async {
  // Start on login screen
  await tester.pumpWidget(app);
  expect(find.byType(LoginScreen), findsOneWidget);
  
  // Enter credentials
  await tester.enterText(find.byKey(Key('username')), 'admin');
  await tester.enterText(find.byKey(Key('password')), 'password');
  
  // Tap login button
  await tester.tap(find.byKey(Key('login_button')));
  await tester.pumpAndSettle();
  
  // Verify: on dashboard
  expect(find.byType(DashboardScreen), findsOneWidget);
});
```

---

## 11. Navigation Map Summary

### 11.1 Screen Count by Role

| Role | MVP Screens | Total Screens (Full App) |
|------|-------------|--------------------------|
| ADMIN | 5 | 25 |
| ACCOUNTANT | 2 | 12 |
| TEACHER | 2 | 15 |
| STUDENT | 2 | 13 |
| PARENT | 2 | 13 |
| Shared (Auth) | 2 | 2 |
| **Total** | **17** | **87** |

### 11.2 Navigation Depth

| Role | Max Depth | Average Depth |
|------|-----------|---------------|
| ADMIN | 4 levels | 2.5 levels |
| ACCOUNTANT | 3 levels | 2.0 levels |
| TEACHER | 3 levels | 2.0 levels |
| STUDENT | 3 levels | 2.0 levels |
| PARENT | 3 levels | 2.0 levels |

### 11.3 Entry Points

| Screen | Entry Points |
|--------|--------------|
| Login | App launch, session expiry, logout |
| Dashboard | Login, bottom nav, deep link |
| Student List | Dashboard, deep link |
| Fee Collection | Dashboard, deep link |
| Mark Attendance | Dashboard, deep link |
| View Results | Dashboard, deep link |
| View Child Attendance | Dashboard, deep link |

---

## 12. Conclusion

This navigation map provides **complete coverage** of all MVP screens with:

- ✅ Role-based navigation structures
- ✅ Deep linking support
- ✅ Navigation guards (auth, password change, role-based)
- ✅ Back navigation behavior
- ✅ Logout flow
- ✅ Error handling
- ✅ Testing strategy

**Ready for Flutter M0 implementation.**
