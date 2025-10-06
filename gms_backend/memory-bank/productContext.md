## Product Context

**Why it exists**
Schools need a centralized system to manage institutions, staff, students, and academics with secure, auditable operations.

**Problems solved**
- Unified user management and authentication
- Enforced role-based access for school vs system scopes
- CRUD workflows for foundational school data

**How it should work (UX goals)**
- Users log in with email or username; receive JWT
- Subsequent requests include Bearer token; backend derives username, roles, schoolId, empId
- Admins manage users and school data; superadmins manage schools and system-wide settings

**Operational qualities**
- Secure by default: endpoints authenticated unless explicitly permitted
- Minimal data leakage across schools
- Observable health/info endpoints


