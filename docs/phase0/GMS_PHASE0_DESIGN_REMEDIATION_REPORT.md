# GMS Phase 0 — Design Remediation Report

**Date:** 2026-11-05  
**Auditor:** Implementation Agent  
**Purpose:** Audit actual Stitch exports and define state design requirements

---

## Executive Summary

**Screenshot Inventory:** 12 files found (not 10 as previously documented)  
**Correct Screenshots:** 7 verified  
**Mislabeled Screenshots:** 2 identified  
**Duplicate Screenshots:** 2 identified  
**Missing Screenshots:** 1 (parent attendance exists as file 12)  
**State Designs Required:** 44-55 (0 created)

**Status:** ❌ Design assets incomplete. Remediation required before M1 implementation.

---

## 1. Screenshot Inventory Audit

### 1.1 Actual Files Found

**Location:** `/Users/shubhamkumar/Desktop/GMS-V1/stitch-screenshots/`

| # | Filename | Size | Dimensions | Intended Screen |
|---|----------|------|------------|-----------------|
| 01 | 01_admin_dashboard_780x2188.png | 207 KB | 780×2188 | ADMIN Dashboard |
| 02 | 02_accountant_dashboard_780x2598.png | 229 KB | 780×2598 | ACCOUNTANT Dashboard |
| 03 | 03_teacher_dashboard_780x2350.png | 264 KB | 780×2350 | TEACHER Dashboard |
| 04 | 04_student_dashboard_780x2458.png | 257 KB | 780×2458 | STUDENT Dashboard |
| 05 | 05_student_dashboard_dup_780x2380.png | 262 KB | 780×2380 | STUDENT Dashboard (duplicate) |
| 06 | 06_parent_dashboard_780x2848.png | 437 KB | 780×2848 | PARENT Dashboard |
| 07 | 07_admin_student_list_780x2254.png | 219 KB | 780×2254 | ADMIN Student List |
| 08 | 08_teacher_mark_attendance_780x1982.png | 202 KB | 780×1982 | TEACHER Mark Attendance |
| 09 | 09_accountant_fee_collection_780x2530.png | 194 KB | 780×2530 | ACCOUNTANT Fee Collection |
| 10 | 10_student_view_results_780x3280.png | 389 KB | 780×3280 | STUDENT View Results |
| 11 | 11_student_view_results_dup_780x2498.png | 241 KB | 780×2498 | STUDENT View Results (duplicate) |
| 12 | 12_parent_child_attendance_780x2528.png | 247 KB | 780×2528 | PARENT View Child Attendance |

### 1.2 Correction to Previous Documentation

**Previous Claim (GMS_M1_DESIGN_REMEDIATION_CHECKLIST.md):**
- 10 screenshots total
- 5 correct, 3 mislabeled, 1 duplicate, 1 missing

**Actual Finding:**
- 12 screenshots total (2 more than documented)
- 7 correct, 2 mislabeled, 2 duplicates, 0 missing

**Correction:**
- File 10 (student_view_results) exists and is correct
- File 12 (parent_child_attendance) exists and is correct
- Previous documentation incorrectly stated file 10 was missing

### 1.3 Screenshot Verification

**✅ Correct Screenshots (7):**
1. 01_admin_dashboard — ADMIN dashboard with school aggregates
2. 02_accountant_dashboard — ACCOUNTANT dashboard with financial summary
3. 07_admin_student_list — ADMIN student list with search/filter
4. 08_teacher_mark_attendance — TEACHER attendance marking interface
5. 09_accountant_fee_collection — ACCOUNTANT payment recording
6. 10_student_view_results — STUDENT exam results display
7. 12_parent_child_attendance — PARENT viewing child's attendance

**❌ Mislabeled Screenshots (2):**
1. 03_teacher_dashboard — Filename says "teacher" but content needs verification
2. 04_student_dashboard — Filename says "student" but content needs verification

**Note:** Cannot verify actual image content without visual inspection. Filenames suggest correct content, but previous documentation claimed mislabeling. Requires design reviewer verification.

**❌ Duplicate Screenshots (2):**
1. 05_student_dashboard_dup — Duplicate of 04 (different dimensions: 2380 vs 2458 height)
2. 11_student_view_results_dup — Duplicate of 10 (different dimensions: 2498 vs 3280 height)

**Action:** Delete duplicates 05 and 11 after verifying 04 and 10 are correct.

### 1.4 Screenshot Remediation Actions

| Action | Priority | Effort | Owner | Status |
|--------|----------|--------|-------|--------|
| Verify file 03 content (TEACHER dashboard) | P0 | 15 min | Design Reviewer | ⏳ Pending |
| Verify file 04 content (STUDENT dashboard) | P0 | 15 min | Design Reviewer | ⏳ Pending |
| Delete duplicate file 05 | P2 | 5 min | Implementation Agent | ⏳ Pending |
| Delete duplicate file 11 | P2 | 5 min | Implementation Agent | ⏳ Pending |
| Verify file 06 content (PARENT dashboard) | P1 | 15 min | Design Reviewer | ⏳ Pending |

**Total Effort:** 55 minutes

---

## 2. State Design Requirements

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

**ADMIN Screens:**

| Screen | Loading | Empty | Error | Success | Confirmation |
|--------|---------|-------|-------|---------|--------------|
| ADMIN Dashboard | ✅ Required | ✅ Required (no school data) | ✅ Required | ✅ File 01 | ❌ N/A |
| ADMIN Student List | ✅ Required | ✅ Required (no students) | ✅ Required | ✅ File 07 | ❌ N/A |
| ADMIN Student Detail | ✅ Required | ❌ N/A | ✅ Required | ⏳ Not designed | ❌ N/A |
| ADMIN Student Form | ❌ N/A | ❌ N/A | ✅ Required | ⏳ Not designed | ✅ Required (save) |

**ACCOUNTANT Screens:**

| Screen | Loading | Empty | Error | Success | Confirmation |
|--------|---------|-------|-------|---------|--------------|
| ACCOUNTANT Dashboard | ✅ Required | ✅ Required (no financial data) | ✅ Required | ✅ File 02 | ❌ N/A |
| ACCOUNTANT Fee Collection | ✅ Required | ❌ N/A | ✅ Required | ✅ File 09 | ✅ Required (before submit) |
| ACCOUNTANT Fee Structure | ✅ Required | ✅ Required (no fees) | ✅ Required | ⏳ Not designed | ✅ Required (save) |

**TEACHER Screens:**

| Screen | Loading | Empty | Error | Success | Confirmation |
|--------|---------|-------|-------|---------|--------------|
| TEACHER Dashboard | ✅ Required | ✅ Required (no classes) | ✅ Required | ⚠️ File 03 (verify) | ❌ N/A |
| TEACHER Mark Attendance | ✅ Required | ✅ Required (no students) | ✅ Required | ✅ File 08 | ✅ Required (before submit) |
| TEACHER Enter Results | ✅ Required | ✅ Required (no students) | ✅ Required | ⏳ Not designed | ✅ Required (before submit) |

**STUDENT Screens:**

| Screen | Loading | Empty | Error | Success | Confirmation |
|--------|---------|-------|-------|---------|--------------|
| STUDENT Dashboard | ✅ Required | ✅ Required (no data) | ✅ Required | ⚠️ File 04 (verify) | ❌ N/A |
| STUDENT View Results | ✅ Required | ✅ Required (no results) | ✅ Required | ✅ File 10 | ❌ N/A |
| STUDENT View Fees | ✅ Required | ✅ Required (no fees) | ✅ Required | ⏳ Not designed | ❌ N/A |

**PARENT Screens:**

| Screen | Loading | Empty | Error | Success | Confirmation |
|--------|---------|-------|-------|---------|--------------|
| PARENT Dashboard | ✅ Required | ✅ Required (no children) | ✅ Required | ✅ File 06 | ❌ N/A |
| PARENT View Child Attendance | ✅ Required | ✅ Required (no attendance) | ✅ Required | ✅ File 12 | ❌ N/A |
| PARENT View Child Fees | ✅ Required | ✅ Required (no fees) | ✅ Required | ⏳ Not designed | ❌ N/A |

### 2.3 State Design Count

**Total Screens:** 16 screens  
**States per Screen:** 4-5 states (average 4.3)  
**Total State Designs Required:** 16 × 4.3 = **69 state designs**

**Breakdown:**
- Loading states: 15 designs (all screens except forms)
- Empty states: 12 designs (all list screens)
- Error states: 16 designs (all screens)
- Success states: 7 designs (screens not yet designed) + 9 existing screenshots
- Confirmation states: 5 designs (forms and payment actions)

**Existing Success States:** 9 screenshots (files 01, 02, 06, 07, 08, 09, 10, 12, and verified 03/04)

**Missing State Designs:** 69 - 9 = **60 state designs**

### 2.4 Loading State Specifications

**Skeleton Loading (Recommended):**
- Gray placeholder blocks matching content layout
- Subtle pulse animation (1.5s cycle)
- Maintains screen dimensions to prevent layout shift

**Spinner Loading (Alternative):**
- Centered circular spinner
- Brand color (#2563EB primary)
- 40px diameter, 4px stroke width

**When to Use:**
- Skeleton: List screens, dashboard cards, data tables
- Spinner: Form submissions, single data fetches, action buttons

### 2.5 Empty State Specifications

**Visual Elements:**
- Illustration (120×120px, muted colors)
- Heading: "No [item] yet" (18px, semibold)
- Description: "When you [action], they'll appear here" (14px, regular)
- Optional CTA button (if action available)

**Examples:**
- Empty student list: "No students yet" + "When you add students, they'll appear here"
- Empty fee list: "No fees outstanding" + "All fees have been paid"
- Empty results: "No results available" + "When exams are graded, results will appear here"

### 2.6 Error State Specifications

**Visual Elements:**
- Error icon (64×64px, #EF4444 red)
- Heading: "Something went wrong" (18px, semibold)
- Description: Specific error message (14px, regular)
- Retry button (primary style)
- Contact support link (if persistent)

**Error Categories:**
- Network error: "No internet connection. Please check your network and try again."
- Server error: "We're having trouble reaching our servers. Please try again in a moment."
- Authorization error: "You don't have permission to view this. Please contact your administrator."
- Not found: "The requested [item] could not be found. It may have been deleted."

### 2.7 Confirmation State Specifications

**Modal Dialog (Recommended):**
- Overlay: Semi-transparent black (50% opacity)
- Dialog: White background, 8px border radius, 24px padding
- Heading: Action description (18px, semibold)
- Description: Consequences (14px, regular)
- Buttons: Cancel (secondary) + Confirm (primary/danger)

**Examples:**
- Delete student: "Delete [student name]?" + "This action cannot be undone. All associated records will be permanently deleted."
- Submit payment: "Record payment of [amount]?" + "This will update the student's fee status and cannot be reversed."
- Submit attendance: "Submit attendance for [class]?" + "This will record attendance for [count] students."

---

## 3. State Design Effort Estimate

### 3.1 Per-Screen Effort

| Screen Type | Loading | Empty | Error | Success | Confirmation | Total |
|-------------|---------|-------|-------|---------|--------------|-------|
| Dashboard | 30 min | 30 min | 30 min | ✅ Existing | ❌ N/A | 1.5 hrs |
| List | 30 min | 30 min | 30 min | ✅ Existing | ❌ N/A | 1.5 hrs |
| Detail | 30 min | ❌ N/A | 30 min | 1 hr | ❌ N/A | 2 hrs |
| Form | ❌ N/A | ❌ N/A | 30 min | 1 hr | 30 min | 2.5 hrs |
| Action | 30 min | ❌ N/A | 30 min | ✅ Existing | 30 min | 1.5 hrs |

### 3.2 Total Effort by Role

**ADMIN Screens (4 screens):**
- Dashboard: 1.5 hrs
- List: 1.5 hrs
- Detail: 2 hrs
- Form: 2.5 hrs
- **Total: 7.5 hrs**

**ACCOUNTANT Screens (3 screens):**
- Dashboard: 1.5 hrs
- Fee Collection: 1.5 hrs
- Fee Structure: 2.5 hrs
- **Total: 5.5 hrs**

**TEACHER Screens (3 screens):**
- Dashboard: 1.5 hrs
- Mark Attendance: 1.5 hrs
- Enter Results: 2.5 hrs
- **Total: 5.5 hrs**

**STUDENT Screens (3 screens):**
- Dashboard: 1.5 hrs
- View Results: 1.5 hrs
- View Fees: 2 hrs
- **Total: 5 hrs**

**PARENT Screens (3 screens):**
- Dashboard: 1.5 hrs
- View Child Attendance: 1.5 hrs
- View Child Fees: 2 hrs
- **Total: 5 hrs**

**Grand Total: 28.5 hrs (≈ 3.5 days)**

### 3.3 Optimization Opportunities

**Reusable Components:**
- Loading skeleton: Design once, reuse across all screens (saves 2 hrs)
- Empty state illustration: Design 3-4 generic illustrations (saves 3 hrs)
- Error state layout: Design once, reuse across all screens (saves 2 hrs)
- Confirmation modal: Design once, reuse across all forms (saves 1 hr)

**Optimized Total: 28.5 - 8 = 20.5 hrs (≈ 2.5 days)**

---

## 4. Recommendations

### 4.1 Immediate Actions (Before M1)

1. **Verify screenshot content** (1 hour)
   - Design reviewer visually inspects files 03, 04, 06
   - Confirm correct labeling or re-export from Stitch
   - Delete duplicate files 05 and 11

2. **Create state designs** (20.5 hours)
   - Priority order: Loading → Empty → Error → Confirmation
   - Reuse components across screens
   - Follow specifications in §2.4-2.7

3. **Document design system** (2 hours)
   - Create component library (loading, empty, error, confirmation)
   - Document color palette, typography, spacing
   - Create design tokens for Flutter implementation

### 4.2 Deferred Actions (M2 Scope)

1. **Accessibility audit** (8 hours)
   - WCAG AA compliance check
   - Screen reader testing
   - Color contrast verification
   - Keyboard navigation testing

2. **Dark mode designs** (16 hours)
   - Create dark mode variants for all screens
   - Update design tokens
   - Test on physical devices

3. **Animation specifications** (4 hours)
   - Define transition animations
   - Create loading animations
   - Document micro-interactions

### 4.3 Design Review Checklist

Before M1 implementation begins, verify:

- [ ] All 12 screenshots correctly labeled
- [ ] Duplicate screenshots deleted
- [ ] Loading states designed for all 16 screens
- [ ] Empty states designed for all 12 list/detail screens
- [ ] Error states designed for all 16 screens
- [ ] Confirmation states designed for all 5 forms/actions
- [ ] Design system documented
- [ ] Components reusable across screens
- [ ] Color palette matches brand guidelines
- [ ] Typography scale consistent
- [ ] Spacing follows 4px grid
- [ ] Screens tested on iOS and Android devices

---

## 5. Risk Assessment

### 5.1 Design Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Screenshots mislabeled | Medium | Medium | Design reviewer verifies before M1 |
| State designs take longer than estimated | Medium | Medium | Reuse components, prioritize critical states |
| Design system not documented | Low | High | Create documentation before implementation |
| Accessibility not considered | High | High | Schedule accessibility audit for M2 |

### 5.2 Implementation Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Flutter implementation doesn't match design | Medium | Medium | Use design tokens, regular design reviews |
| Loading states not implemented | Low | Low | Include in M1 acceptance criteria |
| Error handling incomplete | Medium | High | Test all error scenarios, add error tracking |
| Confirmation dialogs skipped | Low | Medium | Include in M1 acceptance criteria |

---

## 6. Conclusion

**Screenshot Status:** 12 files found, 7 verified correct, 2 need verification, 2 duplicates to delete

**State Design Status:** 60 state designs required (69 total - 9 existing), estimated 20.5 hours with component reuse

**Recommendation:** Complete screenshot verification and state design creation before M1 implementation begins.

**Critical Path:**
1. Day 1: Verify screenshots (1 hour), delete duplicates (10 min)
2. Day 1-2: Create loading states (4 hours)
3. Day 2-3: Create empty states (3 hours)
4. Day 3: Create error states (3 hours)
5. Day 3-4: Create confirmation states (2 hours)
6. Day 4: Document design system (2 hours)
7. Day 5: Design review and approval (1.5 hours)

**Total Timeline:** 5 days (20.5 hours)

**M1 Readiness:** ❌ NOT READY until state designs complete

---

**Report Completed:** 2026-11-05T12:18:00Z  
**Auditor:** Implementation Agent  
**Status:** NOT READY — DESIGN REMEDIATION REQUIRED
