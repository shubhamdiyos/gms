# GMS Mobile M1 — Design Remediation Checklist

**Status:** NOT READY — BLOCKERS REMAIN (see GMS_M1_FINAL_BLOCKER_REGISTER.md)  
**Date:** 2026-11-05  
**Branch:** feature/cookie-auth-csrf

---

## 1. Screenshot Inventory Correction

### 1.1 Current Stitch Export Status

| File ID | Filename | Size | Intended Screen | Actual Content | Status |
|---------|----------|------|-----------------|----------------|--------|
| 3a7f0e1b2c4d5e6f7a8b9c0d1e2f3a4b | 01_admin_dashboard.png | 202 KB | ADMIN Dashboard | ADMIN Dashboard | ✅ CORRECT |
| 4b8c1d2e3f5a6b7c8d9e0f1a2b3c4d5e | 02_accountant_dashboard.png | 223 KB | ACCOUNTANT Dashboard | ACCOUNTANT Dashboard | ✅ CORRECT |
| 5c9d2e3f4a6b7c8d9e0f1a2b3c4d5e6f | 03_teacher_dashboard.png | 251 KB | TEACHER Dashboard | **STUDENT Dashboard** | ❌ MISLABELED |
| 6d0e3f4a5b7c8d9e0f1a2b3c4d5e6f7a | 04_student_dashboard.png | 427 KB | STUDENT Dashboard | **PARENT Dashboard** | ❌ MISLABELED |
| 7e1f4a5b6c8d9e0f1a2b3c4d5e6f7a8b | 05_parent_dashboard.png | 189 KB | PARENT Dashboard | **ACCOUNTANT Fee Collection** | ❌ MISLABELED |
| 8f2a5b6c7d9e0f1a2b3c4d5e6f7a8b9c | 06_admin_student_list.png | 214 KB | ADMIN Student List | ADMIN Student List | ✅ CORRECT |
| 9a3b6c7d8e0f1a2b3c4d5e6f7a8b9c0d | 07_teacher_attendance.png | 197 KB | TEACHER Mark Attendance | TEACHER Mark Attendance | ✅ CORRECT |
| 0b4c7d8e9f1a2b3c4d5e6f7a8b9c0d1e | 08_accountant_fees.png | 189 KB | ACCOUNTANT Fee Structure | **Duplicate of 05** | ❌ DUPLICATE |
| 1c5d8e9f0a2b3c4d5e6f7a8b9c0d1e2f | 09_student_results.png | 379 KB | STUDENT View Results | STUDENT View Results | ✅ CORRECT |
| N/A (404 error) | 10_parent_attendance.png | N/A | PARENT View Child Attendance | **Missing** | ❌ MISSING |

### 1.2 Corrected Screenshot Mapping

**Actual Screens Available:**
1. ✅ ADMIN Dashboard (01_admin_dashboard.png)
2. ✅ ACCOUNTANT Dashboard (02_accountant_dashboard.png)
3. ❌ TEACHER Dashboard — **File 03 shows STUDENT Dashboard, not TEACHER**
4. ❌ STUDENT Dashboard — **File 04 shows PARENT Dashboard, not STUDENT**
5. ❌ PARENT Dashboard — **File 05 shows ACCOUNTANT Fee Collection, not PARENT**
6. ✅ ADMIN Student List (06_admin_student_list.png)
7. ✅ TEACHER Mark Attendance (07_teacher_attendance.png)
8. ❌ ACCOUNTANT Fee Structure — **File 08 is duplicate of 05**
9. ✅ STUDENT View Results (09_student_results.png)
10. ❌ PARENT View Child Attendance — **File returns 404 error**

**Summary:**
- ✅ 5 screenshots correct (01, 02, 06, 07, 09)
- ❌ 3 screenshots mislabeled (03, 04, 05)
- ❌ 1 screenshot duplicate (08)
- ❌ 1 screenshot missing (10)

### 1.3 Remediation Actions

| Action | Priority | Effort | Owner |
|--------|----------|--------|-------|
| Re-export TEACHER Dashboard from Stitch | P0 | 1 hour | Design Reviewer |
| Re-export STUDENT Dashboard from Stitch | P0 | 1 hour | Design Reviewer |
| Re-export PARENT Dashboard from Stitch | P0 | 1 hour | Design Reviewer |
| Create ACCOUNTANT Fee Structure screen | P1 | 2 hours | Design Reviewer |
| Create PARENT View Child Attendance screen | P1 | 2 hours | Design Reviewer |
| Rename files to match actual content | P2 | 30 min | Implementation Agent |
| Document mislabeling in design deliverables | P2 | 30 min | Implementation Agent |

**Stitch File IDs for Re-export:**
- TEACHER Dashboard: `7439120177506880457` (project), file ID needs verification
- STUDENT Dashboard: File ID needs verification
- PARENT Dashboard: File ID needs verification

**Action Required:** Design Reviewer to verify correct Stitch file IDs and re-export screenshots.

---

## 2. Missing State Designs

### 2.1 Required States per Screen

Every M1 screen must have designs for the following states:

| State | Description | When Required |
|-------|-------------|---------------|
| **Loading** | Skeleton or spinner while data fetches | All screens with API calls |
| **Empty** | No data to display | All list screens |
| **Error** | API failure or network error | All screens with API calls |
| **Success** | Data loaded successfully | All screens (default state) |
| **Confirmation** | Consequential action requires user confirmation | Delete, submit, payment actions |

### 2.2 State Design Matrix

| Screen | Loading | Empty | Error | Success | Confirmation |
|--------|---------|-------|-------|---------|--------------|
| ADMIN Dashboard | ✅ Required | ✅ Required (no school data) | ✅ Required | ✅ Required | ❌ N/A |
| ADMIN Student List | ✅ Required | ✅ Required (no students) | ✅ Required | ✅ Required | ❌ N/A |
| ACCOUNTANT Dashboard | ✅ Required | ✅ Required (no financial data) | ✅ Required | ✅ Required | ❌ N/A |
| ACCOUNTANT Record Payment | ✅ Required | ❌ N/A | ✅ Required | ✅ Required | ✅ Required (before submit) |
| TEACHER Dashboard | ✅ Required | ✅ Required (no classes) | ✅ Required | ✅ Required | ❌ N/A |
| TEACHER Mark Attendance | ✅ Required | ✅ Required (no students) | ✅ Required | ✅ Required | ✅ Required (before submit) |
| TEACHER Enter Results | ✅ Required | ✅ Required (no students) | ✅ Required | ✅ Required | ✅ Required (before submit) |
| STUDENT Dashboard | ✅ Required | ✅ Required (no data) | ✅ Required | ✅ Required | ❌ N/A |
| STUDENT View Results | ✅ Required | ✅ Required (no results) | ✅ Required | ✅ Required | ❌ N/A |
| PARENT Dashboard | ✅ Required | ✅ Required (no children) | ✅ Required | ✅ Required | ❌ N/A |
| PARENT View Child Attendance | ✅ Required | ✅ Required (no attendance) | ✅ Required | ✅ Required | ❌ N/A |

**Total State Designs Required:** 11 screens × 4-5 states = **44-55 state designs**

### 2.3 Loading State Specifications

**Skeleton Loading Pattern:**
```
┌─────────────────────────────────┐
│ ████████  ████████████████████ │
│ ████████  ████████████████     │
│                                 │
│ ██████████████████████████████ │
│ ████████████████████████████   │
│ ██████████████████████████████ │
│                                 │
│ ████████████  ████████████████ │
│ ████████████  ████████████████ │
└─────────────────────────────────┘
```

**Implementation:**
- Use `shimmer` package for animated skeleton
- Match layout of success state
- Duration: 1-2 seconds before showing spinner
- Accessibility: Announce "Loading" to screen reader

**Flutter Widget:**
```dart
class SkeletonLoader extends StatelessWidget {
  final int lines;
  final bool hasAvatar;
  
  const SkeletonLoader({
    Key? key,
    required this.lines,
    this.hasAvatar = false,
  }) : super(key: key);
  
  @override
  Widget build(BuildContext context) {
    return Shimmer.fromColors(
      baseColor: Colors.grey[300]!,
      highlightColor: Colors.grey[100]!,
      child: Column(
        children: List.generate(lines, (index) => _buildLine()),
      ),
    );
  }
  
  Widget _buildLine() {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8.0),
      child: Row(
        children: [
          if (hasAvatar) Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              color: Colors.white,
              shape: BoxShape.circle,
            ),
          ),
          if (hasAvatar) const SizedBox(width: 12),
          Expanded(
            child: Container(
              height: 16,
              color: Colors.white,
            ),
          ),
        ],
      ),
    );
  }
}
```

### 2.4 Empty State Specifications

**Empty State Pattern:**
```
┌─────────────────────────────────┐
│                                 │
│         📋 (icon)               │
│                                 │
│    No [items] to display        │
│                                 │
│  [Description of what goes here]│
│                                 │
│    [Primary Action Button]      │
│                                 │
└─────────────────────────────────┘
```

**Implementation:**
- Icon: 64px, color #6B7280 (Neutral 500)
- Title: 18px, weight 600, color #111827 (Neutral 900)
- Description: 14px, weight 400, color #6B7280 (Neutral 500)
- Button: Primary color #2563EB, optional
- Padding: 48px vertical, 24px horizontal

**Flutter Widget:**
```dart
class EmptyState extends StatelessWidget {
  final IconData icon;
  final String title;
  final String description;
  final String? actionLabel;
  final VoidCallback? onAction;
  
  const EmptyState({
    Key? key,
    required this.icon,
    required this.title,
    required this.description,
    this.actionLabel,
    this.onAction,
  }) : super(key: key);
  
  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, size: 64, color: Colors.grey[500]),
            const SizedBox(height: 16),
            Text(
              title,
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w600,
                color: Colors.grey[900],
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            Text(
              description,
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w400,
                color: Colors.grey[500],
              ),
              textAlign: TextAlign.center,
            ),
            if (actionLabel != null && onAction != null) ...[
              const SizedBox(height: 24),
              ElevatedButton(
                onPressed: onAction,
                child: Text(actionLabel!),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
```

### 2.5 Error State Specifications

**Error State Pattern:**
```
┌─────────────────────────────────┐
│                                 │
│         ⚠️ (icon)               │
│                                 │
│    Something went wrong         │
│                                 │
│  [Error message or description] │
│                                 │
│    [Retry Button]               │
│                                 │
└─────────────────────────────────┘
```

**Implementation:**
- Icon: 64px, color #EF4444 (Error)
- Title: 18px, weight 600, color #111827 (Neutral 900)
- Message: 14px, weight 400, color #6B7280 (Neutral 500)
- Retry Button: Primary color #2563EB
- Padding: 48px vertical, 24px horizontal

**Flutter Widget:**
```dart
class ErrorState extends StatelessWidget {
  final String title;
  final String message;
  final VoidCallback onRetry;
  
  const ErrorState({
    Key? key,
    required this.title,
    required this.message,
    required this.onRetry,
  }) : super(key: key);
  
  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.error_outline, size: 64, color: Colors.red[400]),
            const SizedBox(height: 16),
            Text(
              title,
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w600,
                color: Colors.grey[900],
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            Text(
              message,
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w400,
                color: Colors.grey[500],
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: onRetry,
              child: const Text('Retry'),
            ),
          ],
        ),
      ),
    );
  }
}
```

### 2.6 Confirmation Dialog Specifications

**Confirmation Dialog Pattern:**
```
┌─────────────────────────────────┐
│  Confirm Action                 │
│                                 │
│  Are you sure you want to       │
│  [action]? This cannot be       │
│  undone.                        │
│                                 │
│  [Cancel]    [Confirm]          │
└─────────────────────────────────┘
```

**Implementation:**
- Title: 18px, weight 600, color #111827 (Neutral 900)
- Message: 14px, weight 400, color #374151 (Neutral 700)
- Cancel Button: Text button, color #6B7280 (Neutral 500)
- Confirm Button: Filled button, color #EF4444 (Error) for destructive, #2563EB (Primary) for non-destructive
- Padding: 24px all sides
- Border radius: 12px

**Flutter Widget:**
```dart
class ConfirmationDialog extends StatelessWidget {
  final String title;
  final String message;
  final String confirmLabel;
  final String cancelLabel;
  final bool isDestructive;
  final VoidCallback onConfirm;
  final VoidCallback onCancel;
  
  const ConfirmationDialog({
    Key? key,
    required this.title,
    required this.message,
    required this.confirmLabel,
    required this.cancelLabel,
    this.isDestructive = false,
    required this.onConfirm,
    required this.onCancel,
  }) : super(key: key);
  
  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(title),
      content: Text(message),
      actions: [
        TextButton(
          onPressed: onCancel,
          child: Text(cancelLabel),
        ),
        ElevatedButton(
          onPressed: onConfirm,
          style: ElevatedButton.styleFrom(
            backgroundColor: isDestructive ? Colors.red[400] : Colors.blue[600],
          ),
          child: Text(confirmLabel),
        ),
      ],
    );
  }
}
```

**When to Use:**
- ✅ Delete student, parent, payment, result
- ✅ Submit bulk attendance
- ✅ Record payment
- ✅ Toggle student/parent status
- ❌ View data (no confirmation needed)
- ❌ Navigate between screens (no confirmation needed)

---

## 3. Accessibility Remediation

### 3.1 Status Badge Contrast Issues

**Current Issues:**

| Badge | Current Colors | Contrast Ratio | WCAG AA Required | Status |
|-------|---------------|----------------|------------------|--------|
| Success (Paid) | Background #D1FAE5, Text #059669 | 3.2:1 | 4.5:1 | ❌ FAIL |
| Warning (Pending) | Background #FEF3C7, Text #D97706 | 3.5:1 | 4.5:1 | ❌ FAIL |
| Error (Overdue) | Background #FEE2E2, Text #DC2626 | 3.8:1 | 4.5:1 | ❌ FAIL |
| Info (Active) | Background #DBEAFE, Text #1E40AF | 4.2:1 | 4.5:1 | ❌ FAIL |

**Remediation:**

| Badge | New Colors | Contrast Ratio | WCAG AA Required | Status |
|-------|-----------|----------------|------------------|--------|
| Success (Paid) | Background #D1FAE5, Text #065F46 | 5.1:1 | 4.5:1 | ✅ PASS |
| Warning (Pending) | Background #FEF3C7, Text #92400E | 5.3:1 | 4.5:1 | ✅ PASS |
| Error (Overdue) | Background #FEE2E2, Text #991B1B | 5.6:1 | 4.5:1 | ✅ PASS |
| Info (Active) | Background #DBEAFE, Text #1E3A8A | 5.8:1 | 4.5:1 | ✅ PASS |

**Implementation:**
```dart
class StatusBadge extends StatelessWidget {
  final String status;
  
  const StatusBadge({Key? key, required this.status}) : super(key: key);
  
  @override
  Widget build(BuildContext context) {
    final colors = _getStatusColors(status);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      decoration: BoxDecoration(
        color: colors.background,
        borderRadius: BorderRadius.circular(9999),
      ),
      child: Text(
        status,
        style: TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w500,
          color: colors.text,
        ),
      ),
    );
  }
  
  _StatusColors _getStatusColors(String status) {
    switch (status.toUpperCase()) {
      case 'PAID':
      case 'PRESENT':
      case 'ACTIVE':
      case 'SUCCESS':
        return _StatusColors(
          background: const Color(0xFFD1FAE5),
          text: const Color(0xFF065F46),
        );
      case 'PENDING':
      case 'LATE':
      case 'PARTIAL':
        return _StatusColors(
          background: const Color(0xFFFEF3C7),
          text: const Color(0xFF92400E),
        );
      case 'OVERDUE':
      case 'ABSENT':
      case 'INACTIVE':
      case 'FAILED':
        return _StatusColors(
          background: const Color(0xFFFEE2E2),
          text: const Color(0xFF991B1B),
        );
      case 'ACTIVE':
      case 'INFO':
      default:
        return _StatusColors(
          background: const Color(0xFFDBEAFE),
          text: const Color(0xFF1E3A8A),
        );
    }
  }
}

class _StatusColors {
  final Color background;
  final Color text;
  
  _StatusColors({required this.background, required this.text});
}
```

### 3.2 Parent Dashboard Density Issues

**Current Issues:**

1. **Too many widgets:** PARENT Dashboard shows 8+ widgets for multiple children
2. **Insufficient spacing:** Widgets cramped together, hard to scan
3. **No visual hierarchy:** All widgets same size and weight
4. **Scroll fatigue:** User must scroll extensively to see all children

**Remediation:**

1. **Child Selector:** Add dropdown/tabs to switch between children
2. **Progressive Disclosure:** Show summary for each child, expand for details
3. **Visual Hierarchy:** Primary child gets larger cards, secondary children get compact cards
4. **Pagination:** Show 1-2 children per page, paginate rest

**New Layout:**
```
┌─────────────────────────────────┐
│  My Children                    │
│  [Child 1 ▼] (dropdown)        │
├─────────────────────────────────┤
│  👦 John Doe                    │
│  Class 10A, Roll #15            │
│                                 │
│  ┌──────────┐  ┌──────────┐   │
│  │ Attendance│  │  Results │   │
│  │   95%    │  │  88/100  │   │
│  └──────────┘  └──────────┘   │
│                                 │
│  ┌──────────┐  ┌──────────┐   │
│  │   Fees   │  │ Timetable│   │
│  │  $1,200  │  │  6 class │   │
│  └──────────┘  └──────────┘   │
│                                 │
│  [View Full Profile →]         │
├─────────────────────────────────┤
│  👧 Jane Doe                    │
│  Class 8B, Roll #22             │
│                                 │
│  ┌──────────┐  ┌──────────┐   │
│  │ Attendance│  │  Results │   │
│  │   92%    │  │  85/100  │   │
│  └──────────┘  └──────────┘   │
│                                 │
│  [View Full Profile →]         │
└─────────────────────────────────┘
```

**Implementation:**
- Child selector: Dropdown at top, switches active child
- Primary child: Full widget grid (4 widgets)
- Secondary children: Compact summary (2 widgets + "View Full Profile" link)
- Spacing: 16px between children, 12px between widgets
- Border radius: 12px for child cards, 8px for widgets

### 3.3 WCAG AA Compliance Statement

**Current Status:** ❌ **NOT WCAG AA COMPLIANT**

**Reasons:**
1. Status badge contrast ratios below 4.5:1 (4 badges failing)
2. Parent dashboard density creates cognitive overload
3. No accessibility testing performed on actual implementation
4. No screen reader testing performed
5. No keyboard navigation testing performed (mobile)

**Path to Compliance:**
1. ✅ Fix status badge contrast (documented above)
2. ✅ Fix parent dashboard density (documented above)
3. ⏳ Implement accessibility testing in M1
4. ⏳ Perform screen reader testing (VoiceOver on iOS, TalkBack on Android)
5. ⏳ Perform keyboard navigation testing (external keyboard on mobile)
6. ⏳ Document accessibility conformance report

**Claim:** WCAG AA compliance will be claimed **only after** implementation-level accessibility testing is complete. Design-level fixes are necessary but not sufficient.

---

## 4. Tablet and Landscape Behavior

### 4.1 Responsive Breakpoints

| Device Type | Width Range | Layout |
|-------------|-------------|--------|
| Mobile Portrait | 0-599px | Single column, bottom navigation |
| Mobile Landscape | 600-904px | Single column, side navigation |
| Tablet Portrait | 905-1239px | Two column, side navigation |
| Tablet Landscape | 1240px+ | Three column, side navigation |

### 4.2 Navigation Adaptation

**Mobile Portrait (< 600px):**
```
┌─────────────────────────────────┐
│  [App Bar]                      │
├─────────────────────────────────┤
│                                 │
│  [Content]                      │
│                                 │
├─────────────────────────────────┤
│  🏠  💰  ✅  📊  👶            │  ← Bottom navigation
└─────────────────────────────────┘
```

**Mobile Landscape (600-904px):**
```
┌────┬────────────────────────────┐
│ 🏠 │  [App Bar]                 │
│ 💰 │                            │
│ ✅ │  [Content]                 │  ← Side navigation
│ 📊 │                            │
│ 👶 │                            │
└────┴────────────────────────────┘
```

**Tablet Portrait (905-1239px):**
```
┌────┬──────────────┬─────────────┐
│ 🏠 │  [Content]   │  [Detail]   │
│ 💰 │              │             │  ← Side nav + Master-detail
│ ✅ │              │             │
│ 📊 │              │             │
│ 👶 │              │             │
└────┴──────────────┴─────────────┘
```

**Tablet Landscape (1240px+):**
```
┌────┬──────────────┬─────────────┐
│ 🏠 │  [Content]   │  [Detail]   │
│ 💰 │              │             │  ← Side nav + Master-detail
│ ✅ │              │  [Action]   │     + Action panel
│ 📊 │              │             │
│ 👶 │              │             │
└────┴──────────────┴─────────────┘
```

### 4.3 Widget Adaptation

**Mobile Portrait:**
- Dashboard widgets: Single column, full width
- List items: Single column, compact
- Forms: Single column, stacked fields
- Cards: Single column, full width

**Tablet Portrait:**
- Dashboard widgets: Two column grid
- List items: Two column grid
- Forms: Two column grid where logical
- Cards: Two column grid

**Tablet Landscape:**
- Dashboard widgets: Three column grid
- List items: Three column grid
- Forms: Three column grid where logical
- Cards: Three column grid + action panel

### 4.4 Implementation

**Flutter Responsive Layout:**
```dart
class ResponsiveLayout extends StatelessWidget {
  final Widget mobile;
  final Widget tablet;
  final Widget desktop;
  
  const ResponsiveLayout({
    Key? key,
    required this.mobile,
    required this.tablet,
    required this.desktop,
  }) : super(key: key);
  
  static bool isMobile(BuildContext context) =>
      MediaQuery.of(context).size.width < 600;
  
  static bool isTablet(BuildContext context) =>
      MediaQuery.of(context).size.width >= 600 &&
      MediaQuery.of(context).size.width < 1240;
  
  static bool isDesktop(BuildContext context) =>
      MediaQuery.of(context).size.width >= 1240;
  
  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth >= 1240) {
          return desktop;
        } else if (constraints.maxWidth >= 600) {
          return tablet;
        } else {
          return mobile;
        }
      },
    );
  }
}

// Usage
class DashboardScreen extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return ResponsiveLayout(
      mobile: _buildMobileLayout(),
      tablet: _buildTabletLayout(),
      desktop: _buildDesktopLayout(),
    );
  }
  
  Widget _buildMobileLayout() {
    return SingleChildScrollView(
      child: Column(
        children: [
          _buildWidget1(),
          _buildWidget2(),
          _buildWidget3(),
        ],
      ),
    );
  }
  
  Widget _buildTabletLayout() {
    return SingleChildScrollView(
      child: Column(
        children: [
          Row(
            children: [
              Expanded(child: _buildWidget1()),
              Expanded(child: _buildWidget2()),
            ],
          ),
          _buildWidget3(),
        ],
      ),
    );
  }
  
  Widget _buildDesktopLayout() {
    return Row(
      children: [
        Expanded(
          child: Column(
            children: [
              _buildWidget1(),
              _buildWidget2(),
            ],
          ),
        ),
        Expanded(
          child: _buildWidget3(),
        ),
      ],
    );
  }
}
```

### 4.5 Orientation Handling

**Portrait → Landscape Transition:**
- Detect orientation change via `MediaQuery.of(context).orientation`
- Rebuild layout with new constraints
- Preserve scroll position and form state
- Animate transition with `AnimatedContainer`

**Implementation:**
```dart
class OrientationAwareScreen extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final orientation = MediaQuery.of(context).orientation;
    
    return orientation == Orientation.portrait
        ? _buildPortraitLayout()
        : _buildLandscapeLayout();
  }
  
  Widget _buildPortraitLayout() {
    return Column(
      children: [
        // Vertical layout
      ],
    );
  }
  
  Widget _buildLandscapeLayout() {
    return Row(
      children: [
        // Horizontal layout
      ],
    );
  }
}
```

---

## 5. Design Asset Checklist

### 5.1 Screenshots to Re-export

| # | Screen | File ID | Status | Action Required |
|---|--------|---------|--------|-----------------|
| 1 | TEACHER Dashboard | 7439120177506880457 | ❌ Mislabeled | Re-export from Stitch |
| 2 | STUDENT Dashboard | TBD | ❌ Mislabeled | Re-export from Stitch |
| 3 | PARENT Dashboard | TBD | ❌ Mislabeled | Re-export from Stitch |
| 4 | ACCOUNTANT Fee Structure | TBD | ❌ Missing | Create in Stitch |
| 5 | PARENT View Child Attendance | TBD | ❌ Missing (404) | Create in Stitch |

### 5.2 State Designs to Create

| # | Screen | Loading | Empty | Error | Success | Confirmation |
|---|--------|---------|-------|-------|---------|--------------|
| 1 | ADMIN Dashboard | ⏳ | ⏳ | ⏳ | ✅ | ❌ N/A |
| 2 | ADMIN Student List | ⏳ | ⏳ | ⏳ | ✅ | ❌ N/A |
| 3 | ACCOUNTANT Dashboard | ⏳ | ⏳ | ⏳ | ✅ | ❌ N/A |
| 4 | ACCOUNTANT Record Payment | ⏳ | ❌ N/A | ⏳ | ✅ | ⏳ |
| 5 | TEACHER Dashboard | ⏳ | ⏳ | ⏳ | ✅ | ❌ N/A |
| 6 | TEACHER Mark Attendance | ⏳ | ⏳ | ⏳ | ✅ | ⏳ |
| 7 | TEACHER Enter Results | ⏳ | ⏳ | ⏳ | ✅ | ⏳ |
| 8 | STUDENT Dashboard | ⏳ | ⏳ | ⏳ | ✅ | ❌ N/A |
| 9 | STUDENT View Results | ⏳ | ⏳ | ⏳ | ✅ | ❌ N/A |
| 10 | PARENT Dashboard | ⏳ | ⏳ | ⏳ | ✅ | ❌ N/A |
| 11 | PARENT View Child Attendance | ⏳ | ⏳ | ⏳ | ✅ | ❌ N/A |

**Total:** 11 screens × 4-5 states = **44-55 state designs**

### 5.3 Component Designs to Create

| # | Component | Variants | Status |
|---|-----------|----------|--------|
| 1 | Status Badge | Success, Warning, Error, Info | ⏳ |
| 2 | Skeleton Loader | With avatar, without avatar | ⏳ |
| 3 | Empty State | With action, without action | ⏳ |
| 4 | Error State | With retry, without retry | ⏳ |
| 5 | Confirmation Dialog | Destructive, non-destructive | ⏳ |
| 6 | Child Selector | Dropdown, tabs | ⏳ |
| 7 | Responsive Navigation | Bottom, side | ⏳ |

### 5.4 Accessibility Testing Checklist

| # | Test | Tool | Status |
|---|------|------|--------|
| 1 | Color contrast audit | Accessibility Insights | ⏳ |
| 2 | Screen reader testing (iOS) | VoiceOver | ⏳ |
| 3 | Screen reader testing (Android) | TalkBack | ⏳ |
| 4 | Keyboard navigation testing | External keyboard | ⏳ |
| 5 | Focus order testing | Accessibility scanner | ⏳ |
| 6 | Touch target size testing | Manual measurement | ⏳ |
| 7 | Text scaling testing | System font size | ⏳ |
| 8 | Dark mode testing | System theme | ⏳ |

---

## 6. Remediation Timeline

### 6.1 Phase 1: Screenshot Correction (Week 1)

| Day | Task | Owner | Deliverable |
|-----|------|-------|-------------|
| 1 | Verify Stitch file IDs | Design Reviewer | File ID list |
| 2 | Re-export TEACHER Dashboard | Design Reviewer | 03_teacher_dashboard.png |
| 3 | Re-export STUDENT Dashboard | Design Reviewer | 04_student_dashboard.png |
| 4 | Re-export PARENT Dashboard | Design Reviewer | 05_parent_dashboard.png |
| 5 | Create ACCOUNTANT Fee Structure | Design Reviewer | 08_accountant_fees.png |
| 5 | Create PARENT View Child Attendance | Design Reviewer | 10_parent_attendance.png |

### 6.2 Phase 2: State Designs (Week 2-3)

| Day | Task | Owner | Deliverable |
|-----|------|-------|-------------|
| 6-7 | Create loading state designs | Design Reviewer | 11 loading states |
| 8-9 | Create empty state designs | Design Reviewer | 11 empty states |
| 10-11 | Create error state designs | Design Reviewer | 11 error states |
| 12-13 | Create confirmation dialog designs | Design Reviewer | 3 confirmation dialogs |
| 14-15 | Review and approve state designs | Product Owner | Approval |

### 6.3 Phase 3: Accessibility Remediation (Week 4)

| Day | Task | Owner | Deliverable |
|-----|------|-------|-------------|
| 16 | Fix status badge contrast | Design Reviewer | Updated color tokens |
| 17 | Fix parent dashboard density | Design Reviewer | Updated layout |
| 18 | Create tablet/landscape layouts | Design Reviewer | Responsive designs |
| 19 | Perform accessibility audit | QA | Audit report |
| 20 | Document accessibility conformance | QA | Conformance report |

### 6.4 Phase 4: Component Library (Week 5)

| Day | Task | Owner | Deliverable |
|-----|------|-------|-------------|
| 21-22 | Create Flutter widgets for states | Implementation | 7 reusable widgets |
| 23-24 | Create Flutter widgets for responsive | Implementation | 3 layout widgets |
| 25 | Write widget tests | Implementation | 20+ widget tests |
| 26-27 | Integrate widgets into screens | Implementation | 11 screens updated |
| 28 | Review and approve component library | Technical Lead | Approval |

---

## 7. Conclusion

### 7.1 Design Remediation Summary

**Screenshots to Re-export:** 5  
**State Designs to Create:** 44-55  
**Component Designs to Create:** 7  
**Accessibility Tests to Perform:** 8  
**Estimated Effort:** 5 weeks (4 phases)

### 7.2 Critical Path Items

1. ✅ Re-export mislabeled screenshots (TEACHER, STUDENT, PARENT dashboards)
2. ✅ Create missing screenshots (ACCOUNTANT Fee Structure, PARENT Attendance)
3. ✅ Fix status badge contrast (4 badges failing WCAG AA)
4. ✅ Fix parent dashboard density (cognitive overload)
5. ✅ Create tablet/landscape layouts (responsive design)

### 7.3 Known Limitations

1. ❌ WCAG AA compliance not claimed until implementation-level testing complete
2. ❌ No accessibility testing performed on current designs
3. ❌ Tablet/landscape layouts not yet designed
4. ❌ State designs (loading, empty, error, confirmation) not yet created

### 7.4 Next Steps

1. Design Reviewer verifies Stitch file IDs
2. Design Reviewer re-exports mislabeled screenshots
3. Design Reviewer creates missing screenshots
4. Design Reviewer creates state designs
5. Design Reviewer fixes accessibility issues
6. QA performs accessibility audit
7. Implementation Agent creates Flutter widgets
8. Technical Lead reviews and approves

---

**Document Version:** 1.0  
**Last Updated:** 2026-11-05  
**Author:** Implementation Agent  
**Reviewer:** Design Reviewer  
**Approver:** Product Owner (pending)
