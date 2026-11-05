# GMS Remaining Blockers and Gaps

**Version:** 1.0  
**Date:** 2026-11-05  
**Status:** ⚠️ BLOCKERS IDENTIFIED — Requires resolution before M0 implementation  
**Total Blockers:** 12 (2 P0, 4 P1, 6 P2)

---

## Executive Summary

This document provides an **explicit list of all remaining blockers and gaps** that must be resolved before Flutter M0 implementation can begin. Blockers are categorized by priority (P0 = Critical, P1 = High, P2 = Medium) and include specific action items, owners, and estimated resolution time.

**Blocker Summary:**
- 🔴 **P0 (Critical):** 2 blockers — Must resolve before M0 kickoff
- 🟡 **P1 (High):** 4 blockers — Should resolve before M0 implementation
- 🟢 **P2 (Medium):** 6 blockers — Can resolve during M0 implementation

**Estimated Resolution Time:**
- P0 blockers: 1-2 days
- P1 blockers: 3-5 days
- P2 blockers: 1-2 weeks (can be parallelized with M0 development)

**Recommendation:** Resolve P0 blockers immediately, P1 blockers before M0 kickoff, P2 blockers during M0 Phase 1-2.

---

## 1. Critical Blockers (P0)

### B-001: Design 7 Missing MVP Screens

**Category:** Design  
**Priority:** P0 (Critical)  
**Impact:** Cannot implement MVP without screen designs  
**Status:** ⏳ Not started

**Description:**
The MVP scope includes 17 screens, but only 10 have been designed in Stitch. The following 7 screens are missing:

1. **Login Screen** (AUTH-001)
   - Username/password form
   - Remember me checkbox
   - Login button
   - Error message display
   - Loading state

2. **Change Password Screen** (AUTH-002)
   - Old password field
   - New password field
   - Confirm password field
   - Password strength indicator
   - Validation rules display
   - Submit button
   - Success/error states

3. **Logout Confirmation Dialog**
   - Confirmation message
   - Cancel button
   - Logout button
   - Loading state during logout

4. **Session Expired Screen**
   - Session expired message
   - Re-login button
   - Optional: "Remember me" checkbox

5. **Loading Skeleton Screens**
   - Dashboard loading skeleton
   - List loading skeleton
   - Form loading skeleton

6. **Error Boundary Screen**
   - Error message display
   - Retry button
   - Contact support button
   - Error details (optional)

7. **Empty State Screens**
   - No students found
   - No fees pending
   - No attendance records
   - No results available
   - No notifications

**Action Items:**

| Task | Owner | Duration | Dependencies |
|------|-------|----------|--------------|
| Design Login Screen | Designer | 0.5 day | None |
| Design Change Password Screen | Designer | 0.5 day | None |
| Design Logout Dialog | Designer | 0.25 day | None |
| Design Session Expired Screen | Designer | 0.25 day | None |
| Design Loading Skeletons | Designer | 0.5 day | None |
| Design Error Boundary | Designer | 0.25 day | None |
| Design Empty States | Designer | 0.5 day | None |
| Review and approve designs | Product Owner | 0.5 day | All designs complete |
| Upload to Stitch project | Designer | 0.25 day | Designs approved |

**Total Duration:** 3.5 days  
**Deadline:** Before M0 Phase 2 (Authentication) kickoff

**Acceptance Criteria:**
- ✅ All 7 screens designed in Stitch
- ✅ Designs follow existing design system
- ✅ All states covered (loading, error, empty, success)
- ✅ Product owner approval obtained
- ✅ Screens uploaded to Stitch project

**Risk if Not Resolved:**
- M0 implementation blocked
- Developers cannot build authentication flow
- User experience incomplete

---

### B-002: Fix Student Persona Inconsistency (V-001)

**Category:** Design  
**Priority:** P0 (Critical)  
**Impact:** Confusing for stakeholders, inconsistent UX  
**Status:** ⏳ Not started

**Description:**
The Stitch project contains inconsistent student persona data across screens:

- **ADMIN Dashboard:** "Aarav Mehta / Class 10-A"
- **STUDENT Dashboard:** "Aarav Mehta / Class 10-A" (screen #5, 1229px)
- **STUDENT Dashboard:** "Aarav Patel / Grade 10-B" (screen #4, 1191px) ⚠️ DUPLICATE
- **PARENT Dashboard:** "Aarav Mehta / Class 10-A"

**Issues:**
1. Screen #4 shows different student name (Aarav Patel vs Aarav Mehta)
2. Screen #4 shows different class format (Grade 10-B vs Class 10-A)
3. Screen #4 is a duplicate (lower height, less complete)

**Action Items:**

| Task | Owner | Duration | Dependencies |
|------|-------|----------|--------------|
| Standardize on "Aarav Mehta / Class 10-A" | Designer | 0.25 day | None |
| Update all Stitch screens with consistent persona | Designer | 0.5 day | Standardization decision |
| Delete duplicate screen #4 (1191px) | Designer | 0.1 day | Update complete |
| Verify consistency across all 10 screens | Designer | 0.25 day | Update complete |

**Total Duration:** 1 day  
**Deadline:** Before M0 Phase 4 (Dashboard Screens) kickoff

**Acceptance Criteria:**
- ✅ All screens show "Aarav Mehta / Class 10-A"
- ✅ Duplicate screen #4 deleted
- ✅ Consistent class format (Class 10-A, not Grade 10-B)
- ✅ All 10 screens verified

**Risk if Not Resolved:**
- Confusing stakeholder demos
- Inconsistent user experience
- Potential implementation errors

---

## 2. High-Priority Blockers (P1)

### B-003: Consolidate Design Variants (V-002)

**Category:** Design  
**Priority:** P1 (High)  
**Impact:** Unclear design direction, potential rework  
**Status:** ⏳ Not started

**Description:**
The Stitch project contains 3 ADMIN Dashboard variants:

1. **Original** (5a43c1af7d3740f994885b182c739239) — Modern, clean design
2. **Variant A** — Neo-brutalist style
3. **Variant B** — Modern playful style

**Issues:**
- Unclear which design direction to follow
- Variants may confuse developers
- Potential for inconsistent implementation

**Action Items:**

| Task | Owner | Duration | Dependencies |
|------|-------|----------|--------------|
| Review all 3 variants | Product Owner + Designer | 0.5 day | None |
| Choose ONE direction | Product Owner | 0.25 day | Review complete |
| Delete unused variants | Designer | 0.25 day | Decision made |
| Document design rationale | Designer | 0.25 day | Decision made |

**Total Duration:** 1.25 days  
**Deadline:** Before M0 Phase 1 (Foundation) kickoff

**Acceptance Criteria:**
- ✅ One design direction chosen
- ✅ Unused variants deleted from Stitch
- ✅ Design rationale documented
- ✅ All stakeholders aligned

**Risk if Not Resolved:**
- Inconsistent design implementation
- Developer confusion
- Potential rework if wrong direction chosen

**Recommendation:** Keep the original modern, clean design (Variant #1) as it aligns with professional school management software.

---

### B-004: Conduct Accessibility Audit (DS-001)

**Category:** Design / Compliance  
**Priority:** P1 (High)  
**Impact:** Legal/compliance risk, poor UX for disabled users  
**Status:** ⏳ Not started

**Description:**
No accessibility audit has been conducted on the Stitch designs. WCAG 2.1 AA compliance is unknown.

**Potential Issues:**
- Insufficient color contrast ratios (<4.5:1)
- Missing alt text for images
- Inaccessible form labels
- Poor keyboard navigation
- Small touch targets (<44x44px)

**Action Items:**

| Task | Owner | Duration | Dependencies |
|------|-------|----------|--------------|
| Run automated accessibility audit | Designer / QA | 1 day | All designs complete |
| Manual accessibility testing | QA | 1 day | Automated audit complete |
| Fix contrast ratio issues | Designer | 0.5 day | Audit findings |
| Fix touch target issues | Designer | 0.5 day | Audit findings |
| Add alt text to images | Designer | 0.25 day | Audit findings |
| Verify form label accessibility | Designer | 0.25 day | Audit findings |
| Document accessibility compliance | QA | 0.5 day | All fixes complete |

**Total Duration:** 4 days  
**Deadline:** Before M0 Phase 6 (Testing and Polish)

**Acceptance Criteria:**
- ✅ All color contrast ratios ≥4.5:1 (WCAG 2.1 AA)
- ✅ All touch targets ≥44x44px
- ✅ All images have alt text
- ✅ All forms have accessible labels
- ✅ Keyboard navigation works
- ✅ Screen reader compatibility verified
- ✅ Accessibility compliance documented

**Risk if Not Resolved:**
- Legal/compliance risk (WCAG 2.1 AA)
- Poor UX for disabled users
- Potential app store rejection
- Negative user reviews

**Note:** This can be deferred to M1 if legal/compliance requirements allow, but should be addressed before public release.

---

### B-005: Set Up Flutter Development Environment

**Category:** Infrastructure  
**Priority:** P1 (High)  
**Impact:** Cannot start M0 implementation without environment  
**Status:** ⏳ Not started

**Description:**
Flutter development environment needs to be set up for 1-2 developers.

**Requirements:**
- Flutter SDK 3.16+
- Dart SDK 3.2+
- Xcode (for iOS development)
- Android Studio (for Android development)
- iOS Simulator
- Android Emulator
- Physical test devices (iPhone 14 Pro, Samsung Galaxy S21)
- Code editor (VS Code recommended)
- Git client

**Action Items:**

| Task | Owner | Duration | Dependencies |
|------|-------|----------|--------------|
| Install Flutter SDK | Developer | 0.5 day | None |
| Install Xcode (macOS only) | Developer | 1 day | None |
| Install Android Studio | Developer | 0.5 day | None |
| Set up iOS Simulator | Developer | 0.25 day | Xcode installed |
| Set up Android Emulator | Developer | 0.25 day | Android Studio installed |
| Configure code editor | Developer | 0.25 day | Flutter SDK installed |
| Run `flutter doctor` | Developer | 0.25 day | All tools installed |
| Fix any issues from flutter doctor | Developer | 0.5 day | Issues identified |
| Test build on iOS Simulator | Developer | 0.5 day | Environment ready |
| Test build on Android Emulator | Developer | 0.5 day | Environment ready |
| Procure physical test devices | Product Owner | 2 days | Budget approval |

**Total Duration:** 4-6 days (can be parallelized)  
**Deadline:** Before M0 Phase 1 (Foundation) kickoff

**Acceptance Criteria:**
- ✅ `flutter doctor` shows no errors
- ✅ Can build and run on iOS Simulator
- ✅ Can build and run on Android Emulator
- ✅ Physical test devices available
- ✅ Development environment documented

**Risk if Not Resolved:**
- Cannot start M0 implementation
- Developer productivity blocked
- Potential environment-specific bugs not caught

**Documentation Required:**
- Flutter installation guide
- Environment setup checklist
- Troubleshooting guide
- Device configuration guide

---

### B-006: Initialize Flutter Project Repository

**Category:** Infrastructure  
**Priority:** P1 (High)  
**Impact:** Cannot start development without project structure  
**Status:** ⏳ Not started

**Description:**
Flutter project needs to be initialized with proper structure, dependencies, and configuration.

**Requirements:**
- Create Flutter project: `flutter create gms_mobile`
- Set up project structure (see M0 Architecture Plan Section 1.3)
- Add dependencies to pubspec.yaml (see M0 Architecture Plan Section 1.2)
- Configure analysis_options.yaml
- Set up asset folders (images, icons, fonts)
- Initialize Git repository
- Create .gitignore
- Set up CI/CD pipeline (GitHub Actions)
- Create README.md

**Action Items:**

| Task | Owner | Duration | Dependencies |
|------|-------|----------|--------------|
| Create Flutter project | Developer | 0.25 day | Environment ready |
| Set up project structure | Developer | 0.5 day | Project created |
| Add dependencies to pubspec.yaml | Developer | 0.5 day | Structure set up |
| Configure analysis_options.yaml | Developer | 0.25 day | Dependencies added |
| Set up asset folders | Developer | 0.25 day | Project created |
| Initialize Git repository | Developer | 0.1 day | Project created |
| Create .gitignore | Developer | 0.1 day | Git initialized |
| Set up CI/CD pipeline | DevOps | 1 day | Git repository ready |
| Create README.md | Developer | 0.5 day | Project structure complete |
| Run `flutter pub get` | Developer | 0.1 day | Dependencies added |
| Verify project builds | Developer | 0.5 day | All setup complete |

**Total Duration:** 3-4 days  
**Deadline:** Before M0 Phase 1 (Foundation) kickoff

**Acceptance Criteria:**
- ✅ Project structure matches M0 Architecture Plan
- ✅ All dependencies added and resolved
- ✅ Code analysis passes with no errors
- ✅ Git repository initialized
- ✅ CI/CD pipeline configured
- ✅ README.md complete
- ✅ Project builds successfully on iOS and Android

**Risk if Not Resolved:**
- Cannot start M0 implementation
- Inconsistent project structure
- Missing dependencies discovered later
- CI/CD not automated

**Repository Structure:**
```
gms_mobile/
├── lib/                    # Application code
├── test/                   # Test files
├── assets/                 # Images, icons, fonts
├── pubspec.yaml            # Dependencies
├── analysis_options.yaml   # Code analysis rules
├── .gitignore              # Git ignore rules
├── .github/
│   └── workflows/
│       └── flutter-ci.yml  # CI/CD pipeline
└── README.md               # Project documentation
```

---

## 3. Medium-Priority Blockers (P2)

### B-007: Design Remaining 70 Screens (M1+)

**Category:** Design  
**Priority:** P2 (Medium)  
**Impact:** Incomplete app, but not blocking M0  
**Status:** ⏳ Not started

**Description:**
M0 covers 17 MVP screens. The remaining 70 screens (87 total - 17 MVP) need to be designed in M1+.

**Breakdown by Role:**
- ADMIN: 25 screens (3 designed, 22 remaining)
- ACCOUNTANT: 10 screens (2 designed, 8 remaining)
- TEACHER: 11 screens (2 designed, 9 remaining)
- STUDENT: 11 screens (2 designed, 9 remaining)
- PARENT: 11 screens (2 designed, 9 remaining)
- Shared: 2 screens (1 designed, 1 remaining)

**Action Items:**

| Task | Owner | Duration | Dependencies |
|------|-------|----------|--------------|
| Prioritize screens for M1 | Product Owner | 1 day | M0 complete |
| Design M1 screens (20-30) | Designer | 2-3 weeks | Prioritization complete |
| Design M2 screens (20-30) | Designer | 2-3 weeks | M1 complete |
| Design M3 screens (remaining) | Designer | 2-3 weeks | M2 complete |

**Total Duration:** 6-9 weeks (can be parallelized with M1/M2/M3 development)  
**Deadline:** Before respective milestone kickoffs (M1, M2, M3)

**Acceptance Criteria:**
- ✅ All 87 screens designed
- ✅ Designs follow existing design system
- ✅ All screens uploaded to Stitch
- ✅ Product owner approval obtained

**Risk if Not Resolved:**
- Incomplete app
- Cannot implement full feature set
- Poor user experience

**Recommendation:** Defer to M1+. Focus on M0 MVP screens first.

---

### B-008: Add Dark Mode Support (DS-002)

**Category:** Design  
**Priority:** P2 (Medium)  
**Impact:** Poor UX for dark mode users, but not blocking M0  
**Status:** ⏳ Not started

**Description:**
The design system only includes light mode. Dark mode tokens and variants are missing.

**Requirements:**
- Define dark mode color tokens
- Create dark mode variants for all 87 screens
- Implement theme switching logic
- Test both themes on all screens

**Action Items:**

| Task | Owner | Duration | Dependencies |
|------|-------|----------|--------------|
| Define dark mode color tokens | Designer | 1 day | Light mode complete |
| Create dark mode screen variants | Designer | 2-3 weeks | Color tokens defined |
| Implement theme switching logic | Developer | 2 days | Dark mode designs ready |
| Test both themes | QA | 3 days | Implementation complete |

**Total Duration:** 3-4 weeks  
**Deadline:** Before M2 kickoff (can be deferred)

**Acceptance Criteria:**
- ✅ Dark mode color tokens defined
- ✅ All screens have dark mode variants
- ✅ Theme switching works correctly
- ✅ Both themes tested and approved

**Risk if Not Resolved:**
- Poor UX for dark mode users
- Potential user complaints
- Competitive disadvantage

**Recommendation:** Defer to M2. Focus on M0 and M1 first.

---

### B-009: Implement Offline Support

**Category:** Feature  
**Priority:** P2 (Medium)  
**Impact:** App requires constant internet connection, but not blocking M0  
**Status:** ⏳ Not started

**Description:**
M0 assumes constant internet connectivity. Offline support (data caching, sync) is not implemented.

**Requirements:**
- Local database (SQLite/Hive)
- Data caching strategy
- Offline mode UI indicators
- Background sync when online
- Conflict resolution

**Action Items:**

| Task | Owner | Duration | Dependencies |
|------|-------|----------|--------------|
| Design offline architecture | Developer | 2 days | M0 complete |
| Implement local database | Developer | 3 days | Architecture designed |
| Implement data caching | Developer | 3 days | Database ready |
| Implement offline mode UI | Developer | 2 days | Caching ready |
| Implement background sync | Developer | 3 days | Offline UI ready |
| Test offline scenarios | QA | 3 days | Implementation complete |

**Total Duration:** 2-3 weeks  
**Deadline:** Before M2 kickoff (can be deferred)

**Acceptance Criteria:**
- ✅ App works offline
- ✅ Data cached locally
- ✅ Sync works when online
- ✅ Conflicts resolved correctly
- ✅ Offline indicators visible

**Risk if Not Resolved:**
- App unusable without internet
- Poor user experience in low-connectivity areas
- Data loss risk

**Recommendation:** Defer to M2. Focus on M0 and M1 first.

---

### B-010: Implement Push Notifications

**Category:** Feature  
**Priority:** P2 (Medium)  
**Impact:** No real-time updates, but not blocking M0  
**Status:** ⏳ Not started

**Description:**
M0 does not include push notifications. Users must manually refresh for updates.

**Requirements:**
- Firebase Cloud Messaging (FCM) setup
- Notification permission handling
- Notification display logic
- Deep linking from notifications
- Backend notification service

**Action Items:**

| Task | Owner | Duration | Dependencies |
|------|-------|----------|--------------|
| Set up Firebase project | DevOps | 1 day | None |
| Integrate FCM in Flutter app | Developer | 2 days | Firebase ready |
| Implement permission handling | Developer | 1 day | FCM integrated |
| Implement notification display | Developer | 2 days | Permission handling ready |
| Implement deep linking | Developer | 1 day | Notification display ready |
| Set up backend notification service | Backend Developer | 3 days | Firebase ready |
| Test notification flow | QA | 2 days | All components ready |

**Total Duration:** 1-2 weeks  
**Deadline:** Before M2 kickoff (can be deferred)

**Acceptance Criteria:**
- ✅ Push notifications work on iOS and Android
- ✅ Permission handling correct
- ✅ Notifications display properly
- ✅ Deep linking from notifications works
- ✅ Backend sends notifications correctly

**Risk if Not Resolved:**
- No real-time updates
- Users miss important notifications
- Poor engagement

**Recommendation:** Defer to M2. Focus on M0 and M1 first.

---

### B-011: Implement Biometric Authentication

**Category:** Security  
**Priority:** P2 (Medium)  
**Impact:** Less convenient login, but not blocking M0  
**Status:** ⏳ Not started

**Description:**
M0 uses username/password only. Biometric authentication (Face ID, fingerprint) not implemented.

**Requirements:**
- Local authentication package integration
- Biometric permission handling
- Biometric enrollment flow
- Fallback to password
- Secure storage of biometric credentials

**Action Items:**

| Task | Owner | Duration | Dependencies |
|------|-------|----------|--------------|
| Integrate local_auth package | Developer | 1 day | M0 auth complete |
| Implement permission handling | Developer | 0.5 day | Package integrated |
| Implement enrollment flow | Developer | 1 day | Permission handling ready |
| Implement biometric login | Developer | 1 day | Enrollment ready |
| Implement fallback to password | Developer | 0.5 day | Biometric login ready |
| Test biometric authentication | QA | 1 day | Implementation complete |

**Total Duration:** 4-5 days  
**Deadline:** Before M2 kickoff (can be deferred)

**Acceptance Criteria:**
- ✅ Biometric authentication works on supported devices
- ✅ Permission handling correct
- ✅ Enrollment flow user-friendly
- ✅ Fallback to password works
- ✅ Secure storage implemented

**Risk if Not Resolved:**
- Less convenient login
- Users must enter password every time
- Competitive disadvantage

**Recommendation:** Defer to M2. Focus on M0 and M1 first.

---

### B-012: Complete API Documentation

**Category:** Documentation  
**Priority:** P2 (Medium)  
**Impact:** Developers may need clarification, but not blocking M0  
**Status:** ⏳ Not started

**Description:**
Some API responses are not fully documented. Developers may need to inspect backend code for clarification.

**Missing Documentation:**
- Complete response schemas for all 165+ endpoints
- Error response formats
- Pagination details
- Rate limiting information
- API versioning strategy

**Action Items:**

| Task | Owner | Duration | Dependencies |
|------|-------|----------|--------------|
| Document all API response schemas | Backend Developer | 3 days | None |
| Document error response formats | Backend Developer | 1 day | Response schemas complete |
| Document pagination details | Backend Developer | 0.5 day | Response schemas complete |
| Document rate limiting | Backend Developer | 0.5 day | None |
| Document API versioning | Backend Developer | 0.5 day | None |
| Create OpenAPI/Swagger spec | Backend Developer | 2 days | All documentation complete |
| Review and approve documentation | Developer | 1 day | Spec complete |

**Total Duration:** 1-2 weeks  
**Deadline:** Before M1 kickoff (can be done during M0)

**Acceptance Criteria:**
- ✅ All API responses documented
- ✅ Error formats documented
- ✅ Pagination details documented
- ✅ OpenAPI/Swagger spec generated
- ✅ Documentation reviewed and approved

**Risk if Not Resolved:**
- Developer confusion
- Implementation errors
- Delayed development

**Recommendation:** Document critical APIs during M0, complete documentation before M1.

---

## 4. Blocker Resolution Timeline

### 4.1 Pre-M0 (Must resolve before M0 kickoff)

| Blocker | Priority | Duration | Deadline | Owner |
|---------|----------|----------|----------|-------|
| B-001: Design 7 missing MVP screens | P0 | 3.5 days | Before M0 Phase 2 | Designer |
| B-002: Fix student persona inconsistency | P0 | 1 day | Before M0 Phase 4 | Designer |
| B-003: Consolidate design variants | P1 | 1.25 days | Before M0 Phase 1 | Product Owner |
| B-005: Set up Flutter development environment | P1 | 4-6 days | Before M0 Phase 1 | Developer |
| B-006: Initialize Flutter project repository | P1 | 3-4 days | Before M0 Phase 1 | Developer |

**Total Pre-M0 Duration:** 10-12 days (can be parallelized to 5-6 days)

---

### 4.2 During M0 (Can resolve during M0 implementation)

| Blocker | Priority | Duration | Deadline | Owner |
|---------|----------|----------|----------|-------|
| B-004: Conduct accessibility audit | P1 | 4 days | Before M0 Phase 6 | Designer / QA |
| B-012: Complete API documentation | P2 | 1-2 weeks | Before M1 | Backend Developer |

---

### 4.3 Post-M0 (Defer to M1+)

| Blocker | Priority | Duration | Deadline | Owner |
|---------|----------|----------|----------|-------|
| B-007: Design remaining 70 screens | P2 | 6-9 weeks | Before M1/M2/M3 | Designer |
| B-008: Add dark mode support | P2 | 3-4 weeks | Before M2 | Designer |
| B-009: Implement offline support | P2 | 2-3 weeks | Before M2 | Developer |
| B-010: Implement push notifications | P2 | 1-2 weeks | Before M2 | Developer |
| B-011: Implement biometric authentication | P2 | 4-5 days | Before M2 | Developer |

---

## 5. Risk Assessment

### 5.1 Blocker Impact Analysis

| Blocker | Impact if Not Resolved | Probability | Severity | Risk Level |
|---------|------------------------|-------------|----------|------------|
| B-001 | M0 implementation blocked | High | High | 🔴 Critical |
| B-002 | Confusing UX, implementation errors | Medium | Medium | 🟡 High |
| B-003 | Inconsistent design, rework | Medium | Medium | 🟡 High |
| B-004 | Legal/compliance risk | Low | High | 🟡 High |
| B-005 | Cannot start M0 | High | High | 🔴 Critical |
| B-006 | Cannot start M0 | High | High | 🔴 Critical |
| B-007 | Incomplete app | Low | Medium | 🟢 Medium |
| B-008 | Poor UX for dark mode users | Low | Low | 🟢 Medium |
| B-009 | App unusable offline | Low | Medium | 🟢 Medium |
| B-010 | No real-time updates | Low | Low | 🟢 Medium |
| B-011 | Less convenient login | Low | Low | 🟢 Medium |
| B-012 | Developer confusion | Medium | Low | 🟢 Medium |

---

### 5.2 Mitigation Strategies

**For Critical Blockers (B-001, B-005, B-006):**
- Assign dedicated resources
- Set strict deadlines
- Daily progress tracking
- Escalation path to product owner

**For High-Priority Blockers (B-002, B-003, B-004):**
- Assign resources
- Weekly progress tracking
- Include in sprint planning

**For Medium-Priority Blockers (B-007 to B-012):**
- Defer to M1+
- Include in roadmap
- Assign when resources available

---

## 6. Success Criteria

### 6.1 Pre-M0 Success Criteria

All P0 and P1 blockers resolved:
- ✅ 7 missing MVP screens designed
- ✅ Student persona inconsistency fixed
- ✅ Design variants consolidated
- ✅ Accessibility audit conducted (or deferred with approval)
- ✅ Flutter development environment set up
- ✅ Flutter project repository initialized

### 6.2 M0 Success Criteria

All MVP features implemented:
- ✅ 17 MVP screens built
- ✅ Authentication working
- ✅ Navigation working
- ✅ API integration working
- ✅ Tests passing (>80% coverage)

### 6.3 Post-M0 Success Criteria

All P2 blockers resolved or deferred:
- ✅ Remaining 70 screens designed (or deferred to M1+)
- ✅ Dark mode implemented (or deferred to M2)
- ✅ Offline support implemented (or deferred to M2)
- ✅ Push notifications implemented (or deferred to M2)
- ✅ Biometric authentication implemented (or deferred to M2)
- ✅ API documentation complete

---

## 7. Recommendations

### 7.1 Immediate Actions (This Week)

1. **Resolve B-001:** Assign designer to create 7 missing MVP screens
2. **Resolve B-005:** Set up Flutter development environment for all developers
3. **Resolve B-006:** Initialize Flutter project repository with proper structure

### 7.2 Pre-M0 Actions (Next 2 Weeks)

1. **Resolve B-002:** Fix student persona inconsistency in Stitch
2. **Resolve B-003:** Consolidate design variants (choose one direction)
3. **Resolve B-004:** Conduct accessibility audit (or get approval to defer)

### 7.3 M0 Actions (Weeks 3-10)

1. **Implement M0 MVP:** Build 17 screens with authentication and navigation
2. **Resolve B-012:** Complete API documentation during M0
3. **Test thoroughly:** Ensure >80% code coverage

### 7.4 Post-M0 Actions (M1+)

1. **Resolve B-007:** Design remaining 70 screens
2. **Resolve B-008:** Add dark mode support
3. **Resolve B-009:** Implement offline support
4. **Resolve B-010:** Implement push notifications
5. **Resolve B-011:** Implement biometric authentication

---

## 8. Conclusion

This document provides a **complete list of remaining blockers and gaps** for the GMS Flutter mobile application. All blockers have been categorized by priority, with specific action items, owners, and deadlines.

**Key Takeaways:**
- 🔴 **2 Critical blockers** must be resolved before M0 kickoff
- 🟡 **4 High-priority blockers** should be resolved before M0 implementation
- 🟢 **6 Medium-priority blockers** can be deferred to M1+

**Recommendation:**
1. Resolve P0 blockers immediately (this week)
2. Resolve P1 blockers before M0 kickoff (next 2 weeks)
3. Defer P2 blockers to M1+ (post-M0)

**Next Steps:**
1. Product owner reviews and approves blocker list
2. Assign owners to each blocker
3. Set deadlines for P0 and P1 blockers
4. Begin resolution immediately

**Status:** ⚠️ **BLOCKERS IDENTIFIED — Requires product owner approval and resource allocation**
