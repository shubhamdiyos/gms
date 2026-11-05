# GMS Flutter M0 Architecture and Implementation Plan

**Version:** 1.0  
**Date:** 2026-11-05  
**Status:** ✅ Ready for implementation  
**Duration:** 8-10 weeks (estimated)  
**Team:** 1-2 Flutter developers

---

## Executive Summary

This document provides the **complete architecture and implementation plan** for the GMS Flutter mobile application M0 milestone. M0 delivers the **17 MVP screens** with full authentication, navigation, and core workflows.

**M0 Scope:**
- ✅ Authentication (login, logout, password change, session management)
- ✅ 5 role-specific dashboards (ADMIN, ACCOUNTANT, TEACHER, STUDENT, PARENT)
- ✅ 10 critical workflow screens
- ✅ Navigation with deep linking and guards
- ✅ State management with Riverpod
- ✅ API integration with CSRF protection
- ✅ Unit and integration tests (29 test cases)

**Out of Scope (M1+):**
- ⏳ Remaining 70 screens (87 total - 17 MVP)
- ⏳ Dark mode
- ⏳ Accessibility audit (WCAG 2.1 AA)
- ⏳ Offline support
- ⏳ Push notifications
- ⏳ Biometric authentication

---

## 1. Architecture Overview

### 1.1 Architecture Pattern

**Clean Architecture with Feature-Based Organization**

```
┌─────────────────────────────────────────┐
│         Presentation Layer              │
│  (Screens, Widgets, Riverpod Providers) │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│         Domain Layer                    │
│  (Use Cases, Entities, Repository       │
│   Interfaces)                           │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│         Data Layer                      │
│  (Repository Implementations, API       │
│   Client, Local Storage, Models)        │
└─────────────────────────────────────────┘
```

### 1.2 Technology Stack

| Layer | Technology | Version | Purpose |
|-------|-----------|---------|---------|
| Framework | Flutter | 3.16+ | Cross-platform mobile |
| Language | Dart | 3.2+ | Type-safe programming |
| State Management | Riverpod | 2.4+ | Reactive state |
| Routing | GoRouter | 10.0+ | Declarative navigation |
| HTTP Client | Dio | 5.3+ | API communication |
| Cookie Storage | cookie_jar | 4.0+ | Persistent cookies |
| Secure Storage | flutter_secure_storage | 9.0+ | Encrypted local storage |
| JSON Serialization | json_annotation + freezed | 4.8+ / 2.4+ | Type-safe models |
| Image Loading | cached_network_image | 3.3+ | Efficient image caching |
| SVG Support | flutter_svg | 2.0+ | Vector graphics |
| Internationalization | intl | 0.18+ | Multi-language support |
| Testing | flutter_test + mockito | SDK / 5.4+ | Unit and widget tests |

### 1.3 Project Structure

```
gms_mobile/
├── lib/
│   ├── main.dart
│   ├── app.dart
│   │
│   ├── core/
│   │   ├── auth/
│   │   │   ├── auth_cookie_jar.dart
│   │   │   ├── auth_service.dart
│   │   │   ├── auth_state.dart
│   │   │   ├── auth_repository.dart
│   │   │   └── csrf_token_manager.dart
│   │   │
│   │   ├── network/
│   │   │   ├── api_client.dart
│   │   │   ├── auth_interceptor.dart
│   │   │   ├── csrf_interceptor.dart
│   │   │   └── error_interceptor.dart
│   │   │
│   │   ├── theme/
│   │   │   ├── app_theme.dart
│   │   │   ├── app_colors.dart
│   │   │   ├── app_typography.dart
│   │   │   └── app_spacing.dart
│   │   │
│   │   ├── router/
│   │   │   ├── app_router.dart
│   │   │   ├── route_names.dart
│   │   │   └── navigation_guards.dart
│   │   │
│   │   ├── storage/
│   │   │   ├── secure_storage_service.dart
│   │   │   └── local_storage_service.dart
│   │   │
│   │   └── utils/
│   │       ├── date_utils.dart
│   │       ├── validation_utils.dart
│   │       └── format_utils.dart
│   │
│   ├── features/
│   │   ├── auth/
│   │   │   ├── presentation/
│   │   │   │   ├── screens/
│   │   │   │   │   ├── login_screen.dart
│   │   │   │   │   └── change_password_screen.dart
│   │   │   │   └── widgets/
│   │   │   │       ├── login_form.dart
│   │   │   │       └── password_form.dart
│   │   │   └── providers/
│   │   │       └── auth_providers.dart
│   │   │
│   │   ├── dashboard/
│   │   │   ├── presentation/
│   │   │   │   ├── screens/
│   │   │   │   │   ├── admin_dashboard_screen.dart
│   │   │   │   │   ├── accountant_dashboard_screen.dart
│   │   │   │   │   ├── teacher_dashboard_screen.dart
│   │   │   │   │   ├── student_dashboard_screen.dart
│   │   │   │   │   └── parent_dashboard_screen.dart
│   │   │   │   └── widgets/
│   │   │   │       ├── dashboard_card.dart
│   │   │   │       └── stats_tile.dart
│   │   │   └── providers/
│   │   │       └── dashboard_providers.dart
│   │   │
│   │   ├── students/
│   │   │   ├── presentation/
│   │   │   │   ├── screens/
│   │   │   │   │   ├── student_list_screen.dart
│   │   │   │   │   ├── student_details_screen.dart
│   │   │   │   │   └── add_student_screen.dart
│   │   │   │   └── widgets/
│   │   │   │       ├── student_card.dart
│   │   │   │       └── student_form.dart
│   │   │   ├── data/
│   │   │   │   ├── repositories/
│   │   │   │   │   └── student_repository_impl.dart
│   │   │   │   └── models/
│   │   │   │       └── student_model.dart
│   │   │   └── providers/
│   │   │       └── student_providers.dart
│   │   │
│   │   ├── fees/
│   │   │   ├── presentation/
│   │   │   │   ├── screens/
│   │   │   │   │   └── fee_collection_screen.dart
│   │   │   │   └── widgets/
│   │   │   │       ├── fee_card.dart
│   │   │   │       └── payment_form.dart
│   │   │   ├── data/
│   │   │   │   ├── repositories/
│   │   │   │   │   └── fee_repository_impl.dart
│   │   │   │   └── models/
│   │   │   │       └── fee_model.dart
│   │   │   └── providers/
│   │   │       └── fee_providers.dart
│   │   │
│   │   ├── attendance/
│   │   │   ├── presentation/
│   │   │   │   ├── screens/
│   │   │   │   │   └── mark_attendance_screen.dart
│   │   │   │   └── widgets/
│   │   │   │       ├── attendance_list.dart
│   │   │   │       └── attendance_toggle.dart
│   │   │   ├── data/
│   │   │   │   ├── repositories/
│   │   │   │   │   └── attendance_repository_impl.dart
│   │   │   │   └── models/
│   │   │   │       └── attendance_model.dart
│   │   │   └── providers/
│   │   │       └── attendance_providers.dart
│   │   │
│   │   ├── results/
│   │   │   ├── presentation/
│   │   │   │   └── screens/
│   │   │   │       └── view_results_screen.dart
│   │   │   ├── data/
│   │   │   │   ├── repositories/
│   │   │   │   │   └── result_repository_impl.dart
│   │   │   │   └── models/
│   │   │   │       └── result_model.dart
│   │   │   └── providers/
│   │   │       └── result_providers.dart
│   │   │
│   │   └── parent/
│   │       ├── presentation/
│   │       │   └── screens/
│   │       │       └── child_attendance_screen.dart
│   │       ├── data/
│   │       │   ├── repositories/
│   │       │   │   └── parent_repository_impl.dart
│   │       │   └── models/
│   │       │       └── child_model.dart
│   │       └── providers/
│   │           └── parent_providers.dart
│   │
│   └── shared/
│       ├── widgets/
│       │   ├── app_bar.dart
│       │   ├── bottom_nav_bar.dart
│       │   ├── loading_indicator.dart
│       │   ├── error_view.dart
│       │   ├── empty_view.dart
│       │   └── custom_button.dart
│       │
│       └── models/
│           ├── api_response.dart
│           ├── page_response.dart
│           └── user_model.dart
│
├── test/
│   ├── auth/
│   │   ├── auth_cookie_jar_test.dart
│   │   ├── auth_service_test.dart
│   │   ├── csrf_token_manager_test.dart
│   │   └── auth_interceptor_test.dart
│   │
│   ├── integration/
│   │   ├── login_flow_test.dart
│   │   ├── session_persistence_test.dart
│   │   └── password_change_flow_test.dart
│   │
│   └── features/
│       ├── dashboard/
│       ├── students/
│       ├── fees/
│       └── attendance/
│
├── assets/
│   ├── images/
│   ├── icons/
│   └── fonts/
│
├── pubspec.yaml
├── analysis_options.yaml
└── README.md
```

---

## 2. Implementation Phases

### 2.1 Phase 1: Foundation (Week 1-2)

**Goals:**
- Initialize Flutter project
- Set up dependencies
- Configure theme and design system
- Implement core utilities

**Tasks:**

| Task | Duration | Priority | Dependencies |
|------|----------|----------|--------------|
| Initialize Flutter project | 1 day | P0 | None |
| Add dependencies to pubspec.yaml | 0.5 day | P0 | Project init |
| Configure analysis_options.yaml | 0.5 day | P0 | Project init |
| Set up AppTheme (colors, typography, spacing) | 2 days | P0 | Design system |
| Create shared widgets (AppBar, BottomNav, LoadingIndicator) | 2 days | P1 | Theme |
| Set up utility classes (DateUtils, ValidationUtils) | 1 day | P1 | None |
| Configure asset folders (images, icons, fonts) | 0.5 day | P1 | Project init |

**Deliverables:**
- ✅ Flutter project initialized
- ✅ Dependencies configured
- ✅ Theme system in place
- ✅ Shared widgets created
- ✅ Utilities implemented

**Exit Criteria:**
- Project builds successfully on iOS and Android
- Theme renders correctly
- Shared widgets display properly

---

### 2.2 Phase 2: Authentication (Week 3-4)

**Goals:**
- Implement cookie jar
- Build CSRF token manager
- Create auth service
- Develop login and password change screens
- Set up auth state management

**Tasks:**

| Task | Duration | Priority | Dependencies |
|------|----------|----------|--------------|
| Implement AuthCookieJar | 2 days | P0 | Foundation |
| Build CsrfTokenManager | 1.5 days | P0 | Cookie jar |
| Create AuthService | 2 days | P0 | Cookie jar, CSRF manager |
| Implement AuthRepository | 1.5 days | P0 | Auth service |
| Create AuthState and AuthNotifier | 1 day | P0 | Auth service |
| Build LoginScreen | 2 days | P0 | Auth state |
| Build ChangePasswordScreen | 1.5 days | P0 | Auth state |
| Set up auth interceptors (CSRF, 401) | 2 days | P0 | Auth service |
| Write auth unit tests (25 tests) | 2 days | P1 | Auth components |
| Write auth integration tests (3 tests) | 1 day | P1 | Auth components |

**Deliverables:**
- ✅ AuthCookieJar implemented
- ✅ CsrfTokenManager implemented
- ✅ AuthService implemented
- ✅ LoginScreen built
- ✅ ChangePasswordScreen built
- ✅ Auth interceptors configured
- ✅ 28 auth tests passing

**Exit Criteria:**
- Login flow works end-to-end
- CSRF tokens fetched and sent correctly
- Password change flow works
- Session persists across app restart
- All auth tests pass

---

### 2.3 Phase 3: Navigation (Week 5)

**Goals:**
- Set up GoRouter
- Configure navigation guards
- Implement deep linking
- Build bottom navigation bar
- Create role-based routing

**Tasks:**

| Task | Duration | Priority | Dependencies |
|------|----------|----------|--------------|
| Configure GoRouter | 1 day | P0 | Authentication |
| Implement navigation guards (auth, password change, role-based) | 2 days | P0 | GoRouter |
| Set up deep linking (7 routes) | 1.5 days | P0 | GoRouter |
| Build BottomNavBar component | 1 day | P1 | Theme |
| Create role-based navigation structures (5 roles) | 2 days | P0 | Bottom nav |
| Implement back navigation behavior | 0.5 day | P1 | GoRouter |
| Build logout flow | 0.5 day | P0 | Auth service |
| Test navigation flows | 1.5 days | P1 | All navigation |

**Deliverables:**
- ✅ GoRouter configured
- ✅ Navigation guards implemented
- ✅ Deep linking working
- ✅ Bottom navigation bar built
- ✅ Role-based routing configured
- ✅ Logout flow implemented

**Exit Criteria:**
- Navigation between all MVP screens works
- Deep links open correct screens
- Navigation guards prevent unauthorized access
- Logout clears state and redirects to login

---

### 2.4 Phase 4: Dashboard Screens (Week 6)

**Goals:**
- Build 5 role-specific dashboard screens
- Implement dashboard data providers
- Create dashboard widgets
- Connect to backend APIs

**Tasks:**

| Task | Duration | Priority | Dependencies |
|------|----------|----------|--------------|
| Build AdminDashboardScreen | 1.5 days | P0 | Navigation |
| Build AccountantDashboardScreen | 1.5 days | P0 | Navigation |
| Build TeacherDashboardScreen | 1.5 days | P0 | Navigation |
| Build StudentDashboardScreen | 1.5 days | P0 | Navigation |
| Build ParentDashboardScreen | 1.5 days | P0 | Navigation |
| Create dashboard providers (5 roles) | 1 day | P0 | API client |
| Build dashboard widgets (cards, stats) | 1 day | P1 | Theme |
| Test dashboard screens | 1 day | P1 | All dashboards |

**Deliverables:**
- ✅ 5 dashboard screens built
- ✅ Dashboard providers configured
- ✅ Dashboard widgets created
- ✅ API integration working

**Exit Criteria:**
- Each role sees correct dashboard
- Dashboard data loads from API
- Navigation to workflow screens works
- Dashboard widgets render correctly

---

### 2.5 Phase 5: Workflow Screens (Week 7-8)

**Goals:**
- Build 10 critical workflow screens
- Implement workflow data providers
- Create workflow widgets
- Connect to backend APIs
- Handle form submissions

**Tasks:**

| Task | Duration | Priority | Dependencies |
|------|----------|----------|--------------|
| Build StudentListScreen | 1.5 days | P0 | Dashboard |
| Build StudentDetailsScreen | 1.5 days | P0 | Student list |
| Build FeeCollectionScreen | 2 days | P0 | Dashboard |
| Build MarkAttendanceScreen | 2 days | P0 | Dashboard |
| Build ViewResultsScreen | 1.5 days | P0 | Dashboard |
| Build ChildAttendanceScreen | 1.5 days | P0 | Dashboard |
| Create workflow providers (6 screens) | 1 day | P0 | API client |
| Build workflow widgets (forms, lists, cards) | 2 days | P1 | Theme |
| Implement form validation | 1 day | P1 | Workflow widgets |
| Handle API errors and loading states | 1 day | P1 | Workflow providers |
| Test workflow screens | 2 days | P1 | All workflows |

**Deliverables:**
- ✅ 10 workflow screens built
- ✅ Workflow providers configured
- ✅ Workflow widgets created
- ✅ Form validation implemented
- ✅ Error handling in place
- ✅ API integration working

**Exit Criteria:**
- All workflow screens functional
- Forms validate input correctly
- API calls succeed with proper data
- Error states display appropriately
- Loading states work correctly

---

### 2.6 Phase 6: Testing and Polish (Week 9-10)

**Goals:**
- Write comprehensive tests
- Fix bugs and edge cases
- Optimize performance
- Polish UI/UX
- Prepare for release

**Tasks:**

| Task | Duration | Priority | Dependencies |
|------|----------|----------|--------------|
| Write widget tests for all screens | 3 days | P1 | All screens |
| Write integration tests for workflows | 2 days | P1 | All workflows |
| Fix bugs from testing | 2 days | P0 | Test results |
| Optimize API calls (caching, deduplication) | 1 day | P1 | API client |
| Polish UI (animations, transitions, spacing) | 2 days | P2 | All screens |
| Add loading skeletons | 1 day | P2 | All screens |
| Add error boundaries | 1 day | P2 | All screens |
| Performance profiling | 1 day | P1 | All screens |
| Code review and refactoring | 2 days | P1 | All code |
| Update documentation | 1 day | P1 | All features |
| Prepare release build | 1 day | P0 | All testing |

**Deliverables:**
- ✅ Comprehensive test suite
- ✅ All bugs fixed
- ✅ Performance optimized
- ✅ UI polished
- ✅ Documentation updated
- ✅ Release build ready

**Exit Criteria:**
- >80% code coverage
- All critical bugs fixed
- App runs smoothly on iOS and Android
- UI matches design system
- Documentation complete

---

## 3. Development Workflow

### 3.1 Branch Strategy

```
main (production)
  └── develop (integration)
       ├── feature/auth-cookie-jar
       ├── feature/login-screen
       ├── feature/admin-dashboard
       └── ...
```

**Rules:**
- `main` — production-ready code only
- `develop` — integration branch for features
- `feature/*` — one branch per feature/task
- All features require PR review before merge
- No direct commits to `main` or `develop`

---

### 3.2 Code Review Process

1. **Developer** creates feature branch
2. **Developer** implements feature with tests
3. **Developer** creates PR to `develop`
4. **Reviewer** checks:
   - Code quality (clean, readable, maintainable)
   - Test coverage (>80%)
   - Architecture compliance (clean architecture)
   - Security best practices (no hardcoded secrets, proper auth)
   - Performance (no unnecessary rebuilds, efficient API calls)
5. **Reviewer** approves or requests changes
6. **Developer** addresses feedback
7. **Reviewer** merges PR

---

### 3.3 Testing Strategy

**Test Pyramid:**

```
        ╱╲
       ╱  ╲      E2E Tests (5%)
      ╱────╲     Integration Tests (15%)
     ╱      ╲    Unit Tests (30%)
    ╱────────╲   Widget Tests (50%)
   ╱──────────╲
```

**Test Types:**

| Type | Coverage | Tools | Purpose |
|------|----------|-------|---------|
| Unit Tests | 30% | flutter_test, mockito | Test business logic |
| Widget Tests | 50% | flutter_test | Test UI components |
| Integration Tests | 15% | integration_test | Test feature flows |
| E2E Tests | 5% | patrol, appium | Test complete user journeys |

**Test Requirements:**
- All new code must have tests
- Minimum 80% code coverage
- All critical paths tested (auth, payments, attendance)
- Tests run in CI/CD pipeline

---

### 3.4 CI/CD Pipeline

```yaml
# .github/workflows/flutter-ci.yml
name: Flutter CI

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main, develop]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: subosito/flutter-action@v2
        with:
          flutter-version: '3.16.0'
      
      - name: Install dependencies
        run: flutter pub get
      
      - name: Analyze code
        run: flutter analyze
      
      - name: Run tests
        run: flutter test --coverage
      
      - name: Check coverage
        run: |
          coverage=$(lcov --summary coverage/lcov.info | grep "lines" | awk '{print $2}' | sed 's/%//')
          if (( $(echo "$coverage < 80" | bc -l) )); then
            echo "Coverage $coverage% is below 80% threshold"
            exit 1
          fi
      
      - name: Build APK
        run: flutter build apk --release
      
      - name: Build IPA
        run: flutter build ipa --release
```

---

## 4. API Integration

### 4.1 API Client Configuration

```dart
class ApiClient {
  late final Dio _dio;
  final String baseUrl;
  final AuthCookieJar cookieJar;
  
  ApiClient({
    required this.baseUrl,
    required this.cookieJar,
  }) {
    _dio = Dio(BaseOptions(
      baseUrl: baseUrl,
      connectTimeout: Duration(seconds: 30),
      receiveTimeout: Duration(seconds: 30),
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
    ));
    
    // Add interceptors
    _dio.interceptors.addAll([
      CsrfInterceptor(cookieJar: cookieJar),
      AuthInterceptor(cookieJar: cookieJar),
      ErrorInterceptor(),
      LogInterceptor(
        requestBody: true,
        responseBody: true,
      ),
    ]);
  }
  
  Future<Response> get(String path, {Map<String, dynamic>? queryParameters}) async {
    return await _dio.get(path, queryParameters: queryParameters);
  }
  
  Future<Response> post(String path, {dynamic data, Map<String, dynamic>? headers}) async {
    return await _dio.post(path, data: data, options: Options(headers: headers));
  }
  
  Future<Response> put(String path, {dynamic data, Map<String, dynamic>? headers}) async {
    return await _dio.put(path, data: data, options: Options(headers: headers));
  }
  
  Future<Response> delete(String path, {Map<String, dynamic>? headers}) async {
    return await _dio.delete(path, options: Options(headers: headers));
  }
}
```

---

### 4.2 Error Handling

```dart
class ErrorInterceptor extends Interceptor {
  @override
  Future<void> onError(DioError err, ErrorInterceptorHandler handler) async {
    final statusCode = err.response?.statusCode;
    final errorData = err.response?.data;
    
    switch (statusCode) {
      case 401:
        // Session expired
        throw SessionExpiredException(
          message: 'Session expired. Please login again.',
        );
      
      case 403:
        final errorCode = errorData?['error'];
        if (errorCode == 'PASSWORD_CHANGE_REQUIRED') {
          throw PasswordChangeRequiredException(
            message: 'Please change your password to continue.',
          );
        }
        throw AccessDeniedException(
          message: 'You do not have permission to access this resource.',
        );
      
      case 404:
        throw NotFoundException(
          message: 'The requested resource was not found.',
        );
      
      case 400:
        final errors = errorData?['errors'];
        throw ValidationException(
          message: 'Please check your input.',
          errors: errors,
        );
      
      case 500:
        throw ServerException(
          message: 'Server error. Please try again later.',
        );
      
      default:
        throw UnknownException(
          message: 'An unexpected error occurred.',
        );
    }
  }
}
```

---

### 4.3 CSRF Token Management

```dart
class CsrfInterceptor extends Interceptor {
  final AuthCookieJar cookieJar;
  final CsrfTokenManager csrfManager;
  
  CsrfInterceptor({
    required this.cookieJar,
    required this.csrfManager,
  });
  
  @override
  Future<void> onRequest(RequestOptions options, RequestInterceptorHandler handler) async {
    final method = options.method.toUpperCase();
    
    // Only add CSRF header to state-changing methods
    if (method == 'POST' || method == 'PUT' || 
        method == 'PATCH' || method == 'DELETE') {
      
      final csrfToken = await csrfManager.fetchCsrfToken();
      options.headers['X-XSRF-TOKEN'] = csrfToken.token;
    }
    
    handler.next(options);
  }
}
```

---

## 5. State Management

### 5.1 Provider Structure

```dart
// Auth state
@riverpod
class AuthNotifier extends _$AuthNotifier {
  @override
  AuthState build() => AuthStateUnauthenticated();
  
  Future<void> login(String username, String password) async {
    state = AsyncValue.loading();
    try {
      final authService = ref.read(authServiceProvider);
      final authState = await authService.login(username, password);
      state = AsyncValue.data(authState);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }
  
  Future<void> logout() async {
    final authService = ref.read(authServiceProvider);
    await authService.logout();
    state = AuthStateUnauthenticated();
  }
  
  Future<void> changePassword(String oldPassword, String newPassword) async {
    final authService = ref.read(authServiceProvider);
    await authService.changePassword(oldPassword, newPassword);
    
    // Update state
    final currentState = state.value;
    if (currentState is AuthStateAuthenticated) {
      state = AsyncValue.data(AuthStateAuthenticated(
        accessToken: currentState.accessToken,
        expiresIn: currentState.expiresIn,
        requirePasswordChange: false,
      ));
    }
  }
}

// Dashboard data
@riverpod
Future<SchoolDashboardResponse> adminDashboard(Ref ref) async {
  final apiClient = ref.read(apiClientProvider);
  final response = await apiClient.get('/api/v1/dashboard/school');
  return SchoolDashboardResponse.fromJson(response.data);
}

// Student list
@riverpod
class StudentListNotifier extends _$StudentListNotifier {
  @override
  AsyncValue<List<Student>> build() => AsyncValue.data([]);
  
  Future<void> search({
    String? query,
    int? classId,
    int page = 0,
    int size = 20,
    String sort = 'firstName,asc',
  }) async {
    state = AsyncValue.loading();
    try {
      final apiClient = ref.read(apiClientProvider);
      final response = await apiClient.get(
        '/api/v1/students/search',
        queryParameters: {
          if (name != null) 'query': query,
          if (classId != null) 'classId': classId,
          'page': page,
          'size': size,
          'sort': sort,
        },
      );
      
      final pageResponse = PageResponse<Student>.fromJson(
        response.data,
        (json) => Student.fromJson(json),
      );
      
      state = AsyncValue.data(pageResponse.content);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }
}
```

---

### 5.2 State Consumption

```dart
class AdminDashboardScreen extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final dashboardAsync = ref.watch(adminDashboardProvider);
    
    return Scaffold(
      appBar: AppBar(title: Text('Admin Dashboard')),
      body: dashboardAsync.when(
        data: (dashboard) => DashboardContent(dashboard: dashboard),
        loading: () => LoadingIndicator(),
        error: (error, stack) => ErrorView(
          error: error,
          onRetry: () => ref.invalidate(adminDashboardProvider),
        ),
      ),
    );
  }
}
```

---

## 6. Security Considerations

### 6.1 Authentication Security

| Security Measure | Implementation | Status |
|------------------|----------------|--------|
| HttpOnly cookies | gms_auth cookie has HttpOnly=true | ✅ |
| Secure cookies | Both cookies have Secure=true | ✅ |
| SameSite cookies | Both cookies have SameSite=Lax | ✅ |
| CSRF protection | Double Submit Cookie pattern | ✅ |
| Session expiry | 7-day cookie Max-Age | ✅ |
| Password enforcement | PasswordChangeEnforcementFilter | ✅ |
| JWT never exposed | accessToken always null in response | ✅ |

---

### 6.2 Data Security

| Security Measure | Implementation | Status |
|------------------|----------------|--------|
| Tenant isolation | schoolId in JWT, enforced in queries | ✅ |
| Ownership checks | F-1 (student), F-2 (parent), F-3 (parent-student) | ✅ |
| Input validation | Form validation on client and server | ✅ |
| SQL injection | Parameterized queries (JPA/Hibernate) | ✅ |
| XSS protection | HttpOnly cookies, input sanitization | ✅ |
| Sensitive data | No PII in logs or error messages | ✅ |

---

### 6.3 Secure Storage

```dart
class SecureStorageService {
  final FlutterSecureStorage _storage = FlutterSecureStorage();
  
  Future<void> write(String key, String value) async {
    await _storage.write(key: key, value: value);
  }
  
  Future<String?> read(String key) async {
    return await _storage.read(key: key);
  }
  
  Future<void> delete(String key) async {
    await _storage.delete(key: key);
  }
  
  Future<void> deleteAll() async {
    await _storage.deleteAll();
  }
}
```

**What to store securely:**
- User preferences (theme, language)
- Last viewed screen (for deep linking)
- Feature flags

**What NOT to store:**
- JWT tokens (in HttpOnly cookie)
- Passwords (never stored)
- PII (minimize collection)

---

## 7. Performance Optimization

### 7.1 API Optimization

```dart
// Cache API responses
@riverpod
Future<Student> studentDetails(Ref ref, int studentId) async {
  // Cache for 5 minutes
  ref.cacheFor(Duration(minutes: 5));
  
  final apiClient = ref.read(apiClientProvider);
  final response = await apiClient.get('/api/v1/students/$studentId');
  return Student.fromJson(response.data);
}

// Debounce search input
class StudentListScreen extends ConsumerStatefulWidget {
  @override
  ConsumerState<StudentListScreen> createState() => _StudentListScreenState();
}

class _StudentListScreenState extends ConsumerState<StudentListScreen> {
  final _searchController = TextEditingController();
  Timer? _debounce;
  
  void _onSearchChanged(String query) {
    if (_debounce?.isActive ?? false) _debounce!.cancel();
    
    _debounce = Timer(Duration(milliseconds: 500), () {
      ref.read(studentListNotifierProvider.notifier).search(query: query);
    });
  }
  
  @override
  void dispose() {
    _searchController.dispose();
    _debounce?.cancel();
    super.dispose();
  }
  
  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: _searchController,
      onChanged: _onSearchChanged,
      decoration: InputDecoration(
        hintText: 'Search students...',
        prefixIcon: Icon(Icons.search),
      ),
    );
  }
}
```

---

### 7.2 UI Optimization

```dart
// Use const constructors
class DashboardCard extends StatelessWidget {
  const DashboardCard({
    Key? key,
    required this.title,
    required this.value,
  }) : super(key: key);
  
  final String title;
  final String value;
  
  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16.0), // const
        child: Column(
          children: [
            Text(title),
            Text(value),
          ],
        ),
      ),
    );
  }
}

// Use ListView.builder for long lists
class StudentList extends StatelessWidget {
  final List<Student> students;
  
  const StudentList({Key? key, required this.students}) : super(key: key);
  
  @override
  Widget build(BuildContext context) {
    return ListView.builder(
      itemCount: students.length,
      itemBuilder: (context, index) {
        return StudentCard(student: students[index]);
      },
    );
  }
}

// Use RepaintBoundary for complex widgets
class ComplexChart extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return RepaintBoundary(
      child: CustomPaint(
        painter: ChartPainter(),
      ),
    );
  }
}
```

---

## 8. Testing Plan

### 8.1 Unit Tests

**Coverage Target:** 30% of total tests

**Test Files:**
- `test/auth/auth_cookie_jar_test.dart` (6 tests)
- `test/auth/auth_service_test.dart` (8 tests)
- `test/auth/csrf_token_manager_test.dart` (4 tests)
- `test/auth/auth_interceptor_test.dart` (7 tests)
- `test/features/students/student_repository_test.dart` (5 tests)
- `test/features/fees/fee_repository_test.dart` (5 tests)
- `test/features/attendance/attendance_repository_test.dart` (5 tests)

**Total:** 40 unit tests

---

### 8.2 Widget Tests

**Coverage Target:** 50% of total tests

**Test Files:**
- `test/features/auth/login_screen_test.dart` (10 tests)
- `test/features/auth/change_password_screen_test.dart` (8 tests)
- `test/features/dashboard/admin_dashboard_test.dart` (12 tests)
- `test/features/students/student_list_test.dart` (15 tests)
- `test/features/students/student_details_test.dart` (10 tests)
- `test/features/fees/fee_collection_test.dart` (12 tests)
- `test/features/attendance/mark_attendance_test.dart` (10 tests)
- `test/shared/widgets/app_bar_test.dart` (5 tests)
- `test/shared/widgets/bottom_nav_test.dart` (5 tests)

**Total:** 87 widget tests

---

### 8.3 Integration Tests

**Coverage Target:** 15% of total tests

**Test Files:**
- `test/integration/login_flow_test.dart` (2 tests)
- `test/integration/session_persistence_test.dart` (1 test)
- `test/integration/password_change_flow_test.dart` (1 test)
- `test/integration/student_workflow_test.dart` (3 tests)
- `test/integration/fee_payment_workflow_test.dart` (2 tests)
- `test/integration/attendance_workflow_test.dart` (2 tests)

**Total:** 11 integration tests

---

### 8.4 E2E Tests

**Coverage Target:** 5% of total tests

**Test Files:**
- `integration_test/app_test.dart` (5 tests)
  - Login flow
  - Admin dashboard navigation
  - Student admission workflow
  - Fee payment workflow
  - Attendance marking workflow

**Total:** 5 E2E tests

---

### 8.5 Test Summary

| Test Type | Count | Coverage Target |
|-----------|-------|-----------------|
| Unit Tests | 40 | 30% |
| Widget Tests | 87 | 50% |
| Integration Tests | 11 | 15% |
| E2E Tests | 5 | 5% |
| **Total** | **143** | **100%** |

**Overall Code Coverage Target:** >80%

---

## 9. Deployment Plan

### 9.1 Build Configuration

**Android (build.gradle):**
```gradle
android {
    compileSdkVersion 34
    
    defaultConfig {
        applicationId "school.gms.mobile"
        minSdkVersion 24
        targetSdkVersion 34
        versionCode 1
        versionName "1.0.0"
    }
    
    buildTypes {
        release {
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
        }
    }
}
```

**iOS (Info.plist):**
```xml
<key>CFBundleName</key>
<string>GMS Mobile</string>
<key>CFBundleIdentifier</key>
<string>school.gms.mobile</string>
<key>CFBundleVersion</key>
<string>1</string>
<key>CFBundleShortVersionString</key>
<string>1.0.0</string>
<key>MinimumOSVersion</key>
<string>14.0</string>
```

---

### 9.2 Release Process

1. **Build release APK/IPA:**
   ```bash
   flutter build apk --release
   flutter build ipa --release
   ```

2. **Test on physical devices:**
   - iOS: iPhone 14 Pro (390x844)
   - Android: Samsung Galaxy S21 (360x800)

3. **Submit to stores:**
   - Google Play Store
   - Apple App Store

4. **Monitor:**
   - Crash reports (Sentry)
   - Analytics (Firebase)
   - User feedback

---

## 10. Risk Management

### 10.1 Technical Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Authentication implementation errors | Medium | High | Comprehensive test suite (29 tests) |
| CSRF token handling bugs | Low | High | Backend verification + test coverage |
| Session expiry edge cases | Medium | Medium | 401 interceptor with recursive logout guard |
| Navigation guard bypass | Low | Medium | Role-based access control in backend |
| Cookie persistence failures | Low | Medium | Secure storage fallback |
| Deep link routing errors | Low | Medium | Comprehensive deep link testing |
| Performance issues on low-end devices | Medium | Medium | Performance profiling + optimization |
| API response format changes | Low | Low | Backend feature-frozen |

---

### 10.2 Project Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Developer availability | Low | High | Document all decisions, cross-train |
| Scope creep | Medium | Medium | Strict MVP scope, change request process |
| Timeline delays | Medium | Medium | Buffer time in each phase, prioritize P0 tasks |
| Design system inconsistencies | Medium | Low | Design review before implementation |
| Stakeholder feedback delays | Low | Medium | Regular demos, early feedback loops |

---

## 11. Success Criteria

### 11.1 M0 Completion Criteria

| Criterion | Target | Measurement |
|-----------|--------|-------------|
| MVP screens built | 17/17 | Screen count |
| API coverage | 100% | Endpoint verification |
| Test coverage | >80% | Code coverage report |
| Authentication working | ✅ | Login/logout/password change flows |
| Navigation working | ✅ | All role-based navigation paths |
| Deep linking working | ✅ | 7 deep links tested |
| Performance | ✅ | <3s load time, 60fps scrolling |
| Bugs fixed | ✅ | All P0/P1 bugs resolved |
| Documentation | ✅ | README, API docs, architecture docs |

---

### 11.2 Quality Gates

| Gate | Criteria | Owner |
|------|----------|-------|
| Code Review | All PRs reviewed and approved | Tech Lead |
| Test Coverage | >80% coverage | QA Engineer |
| Security Audit | No P0/P1 vulnerabilities | Security Engineer |
| Performance Test | <3s load time, 60fps | Performance Engineer |
| UAT | Stakeholder sign-off | Product Owner |

---

## 12. Timeline

### 12.1 Gantt Chart

```
Week 1-2:   ████████ Foundation
Week 3-4:   ████████ Authentication
Week 5:     ████ Navigation
Week 6:     ████ Dashboard Screens
Week 7-8:   ████████ Workflow Screens
Week 9-10:  ████████ Testing and Polish
```

### 12.2 Milestones

| Milestone | Target Date | Deliverables |
|-----------|-------------|--------------|
| M0.1: Foundation | Week 2 | Project init, theme, shared widgets |
| M0.2: Authentication | Week 4 | Login, logout, password change, session management |
| M0.3: Navigation | Week 5 | GoRouter, guards, deep linking, bottom nav |
| M0.4: Dashboards | Week 6 | 5 role-specific dashboards |
| M0.5: Workflows | Week 8 | 10 critical workflow screens |
| M0.6: Testing | Week 10 | 143 tests, >80% coverage, bug fixes |
| **M0: Release** | **Week 10** | **17 MVP screens, fully tested, production-ready** |

---

## 13. Conclusion

This M0 architecture and implementation plan provides a **complete roadmap** for delivering the GMS Flutter mobile application MVP. The plan includes:

- ✅ Clean architecture with feature-based organization
- ✅ Comprehensive technology stack (Flutter, Riverpod, Dio, GoRouter)
- ✅ 6-phase implementation plan (10 weeks)
- ✅ Detailed task breakdown with dependencies
- ✅ Development workflow (branching, code review, CI/CD)
- ✅ API integration strategy (client, error handling, CSRF)
- ✅ State management patterns (Riverpod providers)
- ✅ Security considerations (auth, data, storage)
- ✅ Performance optimization (caching, debouncing, UI)
- ✅ Testing plan (143 tests, >80% coverage)
- ✅ Deployment process (build, test, submit)
- ✅ Risk management (technical and project risks)
- ✅ Success criteria (completion criteria, quality gates)
- ✅ Timeline (Gantt chart, milestones)

**Ready for product owner approval and implementation kickoff.**
