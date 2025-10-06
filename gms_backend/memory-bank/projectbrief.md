## Project Brief

**Project Name**: GMS (Global/School Management System)

**Purpose**: Provide a role-based, multi-tenant (per school) management system to handle schools, employees, students, classes, subjects, and users with secure authentication and audit capabilities.

**Development Principle**:
- Always follow the existing coding pattern when adding code.

**Primary Users**:
- SUPERADMIN: system-level administration
- ADMIN: school-level administration
- TEACHER, STUDENT, PARENT: academic roles (view/limited actions)

**Core Features**:
- JWT-based authentication with email/username login, password change, and self-registration for employees
- Role-based access control (RBAC) via Spring Security
- CRUD for entities: School, Employee, Student, Classroom, Subject, User, Designation, StudentFee, FeePayment
- Audit fields across entities via a shared base entity
- Basic observability with Actuator and Prometheus registry

**Out of Scope (for now)**:
- Full multi-tenant DB isolation
- Complex scheduling, grading, and reporting workflows
- UI/front-end

**Constraints**:
- Spring Boot 3.3.x, Java 21
- PostgreSQL database
- Stateless REST APIs secured with Bearer JWT

**Success Criteria**:
- Secure endpoints honoring RBAC
- Stable CRUD flows for core entities
- Clean authentication flow producing tokens with username, roles, schoolId, empId claims