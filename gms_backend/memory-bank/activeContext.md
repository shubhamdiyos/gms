## Active Context

**Current focus**
- Working on entity model patterns and ensuring consistency across all entity models
- Documenting entity conventions in memory-bank
- Fixing service implementation errors and ensuring proper linkage between services
- Establishing global API implementation coding patterns
- Aligning existing implementations with reference patterns

**Recent changes**
- Initialized Memory Bank core files with architecture, patterns, tech details
- Added entityPatterns.md to document entity model conventions
- Verified existing entity models follow established patterns
- Fixed service implementation errors in AttendanceServiceImpl, TeacherAssignmentServiceImpl, and TimetableServiceImpl
- Created apiCodingPatterns.md to establish mandatory coding patterns for all API implementations
- Aligning existing controllers and services with reference patterns (UserController, SchoolController, StudentController, AttendanceController, and EmployeeController completed)
- Resolved compilation errors related to repository method name mismatches and entity field access issues
- Documented error resolution strategies in errorResolutionLog.md to prevent recurrence

**Next steps**
- Continue aligning remaining controllers and services with reference patterns
- Add API surface overview and endpoint ownership; provide Postman collection and curl snippets
- Capture data model map and relationships
- Externalize secrets (mail creds, JWT secret) to env configuration

**Decisions**
- Keep `JwtTokenProvider` subject = system username to align with `SecurityUtil` downstream lookups
- No Swagger/OpenAPI UI; API testing via Postman or curl. Maintain a Postman collection for teams.
- Apply soft delete across APIs: mark records deleted by setting `status = "0"`.
- Establish apiCodingPatterns.md as the primary reference for all AI agents working on API implementations
- Align all existing implementations with the reference patterns to ensure consistency

**Test Credentials (local/dev)**
- SUPERADMIN: email `superadmin@gms.local`, password `ChangeMe123!`