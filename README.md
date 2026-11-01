# 🎓 GMS - Global Management System

A comprehensive School Management System built with Spring Boot and React, featuring role-based access control, multi-tenant architecture, and complete CRUD operations.

## 🚀 Features

### 🔐 Authentication & Authorization
- **JWT-based authentication** with secure token management
- **Role-based access control (RBAC)** with 5 user roles:
  - SuperAdmin (system-wide access)
  - Admin (school-level management)
  - Teacher (academic operations)
  - Student (personal data access)
  - Parent (child's academic information)

### 🏫 SuperAdmin Features
- **Complete school management** with CRUD operations
- **System-wide dashboard** with real-time analytics
- **Multi-tenant school creation** with automatic admin bootstrap
- **Soft delete implementation** for data preservation
- **Cross-school operations** and management

### 🎯 Core Modules
- **School Management** - Create, manage, and monitor schools
- **User Management** - Students, employees, parents, and teachers
- **Academic Management** - Classes, subjects, attendance, exams
- **Financial Management** - Fees, payments, invoices, reports
- **Communication** - Notifications, announcements, messaging

## 🛠 Tech Stack

### Backend
- **Framework**: Spring Boot 3.3.3
- **Language**: Java 21
- **Database**: PostgreSQL
- **Security**: Spring Security + JWT
- **Build Tool**: Maven
- **Architecture**: Multi-layered with service abstraction

### Frontend
- **Framework**: React 18
- **UI Library**: Material-UI (MUI) v5
- **State Management**: Redux Toolkit + RTK Query
- **Routing**: React Router v6 with protected routes
- **Build Tool**: Vite
- **Styling**: CSS-in-JS with MUI theming

## 📁 Project Structure

```
WindsurfGMS/
├── gms_backend/           # Spring Boot backend
│   ├── src/main/java/com/gms/
│   │   ├── config/        # Security, CORS, Bootstrap configs
│   │   ├── controller/    # REST API endpoints
│   │   ├── model/         # Entities, DTOs, Requests/Responses
│   │   ├── repository/    # JPA repositories
│   │   ├── service/       # Business logic layer
│   │   ├── security/      # JWT, authentication components
│   │   └── util/          # Utility classes and helpers
│   └── src/main/resources/
│       └── application.properties
├── gms_frontend/          # React frontend
│   ├── src/
│   │   ├── components/    # Reusable UI components
│   │   ├── features/      # Feature-based modules
│   │   ├── store/         # Redux store and API slices
│   │   ├── hooks/         # Custom React hooks
│   │   └── services/      # API client services
│   └── package.json
└── README.md
```

## 🚀 Quick Start

### Prerequisites
- Java 21+
- Node.js 18+
- PostgreSQL 14+
- Maven 3.8+

### Backend Setup

1. **Clone the repository**
```bash
git clone https://github.com/shubhamdiyos/gms.git
cd gms/gms_backend
```

2. **Point the application at a database you own**

Do **not** hand-edit `gms_backend/src/main/resources/application.properties`: the committed file currently carries a
hosted database credential (finding SEC-01 in
[docs/audit/REPOSITORY_AUDIT.md](docs/audit/REPOSITORY_AUDIT.md)), and editing it is how a developer ends up working
against production data by accident. Override the datasource at run time instead:

```bash
# start the disposable Postgres from Compose
cp .env.example .env
docker compose up -d postgres

# run the backend against it (command-line properties win over the file)
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # JDK 21 only — see below
mvn spring-boot:run -Dspring-boot.run.arguments=" \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/gms?sslmode=disable \
  --spring.datasource.username=gms_user \
  --spring.datasource.password=<DB_PASSWORD from your .env>"
```

Two caveats that are real today, not aspirational:

* **Java 21 is required.** With a newer default JDK the build fails with
  `Fatal error compiling: … TypeTag :: UNKNOWN` (a Lombok/javac incompatibility). Nothing in the repository pins this
  yet.
* **The application cannot create its own schema from a clean database.** `ddl-auto` defaults to `validate` and there
  are no migration files, so startup fails with `Schema-validation: missing table [academic_years]` until Flyway
  migrations exist (OPS-07). The temporary workaround used during the baseline was
  `--spring.jpa.hibernate.ddl-auto=update` **against a disposable database only** — never against a hosted one.

Full reproduction steps and measured outputs: [docs/audit/BASELINE_STATUS.md](docs/audit/BASELINE_STATUS.md).

3. **Start the backend**

Use the command in step 2 — plain `mvn spring-boot:run` reads the committed datasource URL, which points at a hosted
database.

Backend will start on `http://localhost:8080`

### Frontend Setup

1. **Navigate to frontend directory**
```bash
cd ../gms_frontend
```

2. **Install dependencies**
```bash
npm install
```

3. **Start development server**
```bash
npm run dev
```

Frontend will start on `http://localhost:5173`

## 🔑 Initial SuperAdmin

The first SuperAdmin is created at startup by the bootstrap runner. Its username and
password are **not** stored in this repository — they come from deployment configuration:

| Property | Purpose |
| --- | --- |
| `APP_SUPERADMIN_ENABLED` | Turn the bootstrap on or off |
| `APP_SUPERADMIN_USERNAME` | Login name of the initial platform administrator |
| `APP_SUPERADMIN_PASSWORD` | Initial password (must satisfy the password policy) |
| `APP_SUPERADMIN_EMAIL` | Contact address for the account |
| `APP_SUPERADMIN_FULL_NAME` | Display name |

See [docs/audit/SECURITY_AUDIT.md](docs/audit/SECURITY_AUDIT.md) for why a shipped default is
not acceptable. The initial password is single-use: the first login forces a change.

## 📊 API Endpoints

### Authentication
- `POST /api/v1/auth/login` - User login
- `POST /api/v1/auth/change-password` - Change password

### Schools (SuperAdmin)
- `GET /api/v1/schools` - List all schools
- `POST /api/v1/schools/create` - Create new school
- `PUT /api/v1/schools/update` - Update school
- `PATCH /api/v1/schools/toggle` - Toggle school status
- `GET /api/v1/schools/{id}` - Get school details

### Students
- `GET /api/v1/students` - List students
- `POST /api/v1/students/create` - Create student
- `PUT /api/v1/students/update` - Update student

### Employees
- `GET /api/v1/employees` - List employees
- `POST /api/v1/employees/create` - Create employee
- `PUT /api/v1/employees/update` - Update employee

## 🔐 Security Features

Status labels below describe the **implemented** behaviour, measured on 2026-11-01 against a local instance
([docs/audit/BASELINE_STATUS.md](docs/audit/BASELINE_STATUS.md) §4). The target design is in the audit; the gaps are
tracked by finding ID.

### JWT Authentication
- Stateless authentication with JWT tokens — implemented
- Role-based claims in the JWT payload — implemented
- Secure password hashing with BCrypt — implemented
- ~~Automatic token refresh mechanism~~ — **does not exist** (SEC-11): there is no refresh endpoint, no logout and no
  revocation list, so a 1-hour token cannot be withdrawn, even after a password change
- Token signing key — **currently the source-code default**, which is public (SEC-02). Rotating it is an owner action

### Multi-Tenant Architecture
- One `School` = one tenant, resolved from the token — implemented as a convention
- ~~School-level data isolation~~ / ~~Tenant-aware repository queries~~ — **not enforced**. The seven controllers that
  inherit `AbstractCRUDController` expose `GET /{id}` and `DELETE /{id}` with no tenant predicate (TEN-01…TEN-07),
  `users`, `schools`, `students`, `fee_payments` and 12 tables in total have no tenant column at all, and the student
  and parent portal routes are unreachable because of filter-chain ordering (SEC-06)
- ~~Cross-school access for SuperAdmin only~~ — a correctly signed token asserts whatever `schoolId` it carries

### RBAC Implementation
- Method-level security with `@PreAuthorize` — partially: 6 of 36 controllers have none, and 20 of 36 are protected
  only by `anyRequest().authenticated()`
- ~~Role hierarchy enforcement~~ — no hierarchy is configured; authorities come from the `user_roles` table
- Permission-based UI rendering and protected frontend routes — implemented, but client-side only and therefore not a
  security control

## 🎨 Frontend Features

### Dashboard System
- **SuperAdmin Dashboard**: System overview with analytics
- **Admin Dashboard**: School-specific management
- **Teacher Dashboard**: Class and academic management
- **Student Dashboard**: Personal academic information
- **Parent Dashboard**: Child's academic progress

### UI/UX Features
- **Responsive design** with Material-UI components
- **Dark/Light theme** support
- **Real-time notifications** with toast messages
- **Loading states** and error handling
- **Form validation** with Yup schemas
- **Data tables** with sorting, filtering, pagination

## 🔧 Development Features

### Backend Patterns
- **Abstract CRUD services** for code reuse
- **Global exception handling** with structured responses
- **Audit trail** with created/updated timestamps
- **Soft delete** implementation for data preservation
- **Repository pattern** with custom queries

### Frontend Architecture
- **Feature-based** folder structure
- **Redux Toolkit** for state management
- **RTK Query** for API integration and caching
- **Custom hooks** for reusable logic
- **Protected routes** with role-based access

## 🧪 Testing

**The backend has tests; the frontend does not.** Measured 2026-11-01 on `mvn -B verify` — 36 tests in 6 classes,
0 failures — so this section's earlier claim that the repository had none is no longer true, and it is corrected
rather than left to rot.

| Command | What actually happens today |
| --- | --- |
| `mvn -B verify` | runs `gms_backend/src/test` — `JwtTokenProviderTest`, `SuperAdminBootstrapPropertiesTest`, `CustomUserDetailsServiceTest`, `LoginAttemptServiceTest`, `UserServiceImplRoleGuardTest`, `GlobalExceptionHandlerTest` — and writes `target/site/jacoco/index.html` |
| `mvn -DskipTests` | still the command the production image build uses (`gms_backend/Dockerfile:11`), so the shipped jar is not built from a tested compile — see INF-09 |
| `npm test` | fails: `gms_frontend/package.json` has no `test` script and no runner is installed. CI's "Unit tests" step runs it with `continue-on-error: true`, so that step always fails and is swallowed — it proves nothing and is labelled as such (`ci.yml`) |
| `npm run lint` | runs, and currently reports 47 errors across 22 files (FE-14); advisory in CI, with the count recorded there |
| JaCoCo | bound to `prepare-agent` + `report` at `verify`, so coverage is measured; there is still no coverage **threshold** that can fail a build |
| CI | **two** workflows now: `.github/workflows/ci.yml` builds and tests on PRs and on `master`, and `.github/workflows/deploy-backend.yml` deploys to production on push to `master` with no build or test job upstream of it, and none of the checks are marked **required** (OPS-01, INF-10) |
| `scripts/verify-security.sh` | 18 executable security assertions against a local boot — see [docs/audit/BASELINE_STATUS.md](docs/audit/BASELINE_STATUS.md) §13.2 |

What is still missing is the part that makes refactoring safe: no integration tests against a real database, no
frontend tests, no end-to-end suite. Until those land, the manual verification path is the Postman collection —
45 requests against roughly 230 endpoint mappings — documented in
[docs/WindsurfGMS-Postman-Testing-Guide.md](docs/WindsurfGMS-Postman-Testing-Guide.md). Run it against a disposable
database only.

## 📝 Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/new-feature`
3. Commit changes: `git commit -m 'Add new feature'`
4. Push to branch: `git push origin feature/new-feature`
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👥 Team

- **Developer**: Shubham Kumar
- **GitHub**: [@shubhamdiyos](https://github.com/shubhamdiyos) — open an issue on this repository for anything
  GMS-related

## 🙏 Acknowledgments

- Spring Boot team for the excellent framework
- React team for the powerful frontend library
- Material-UI team for the beautiful components
- PostgreSQL team for the robust database

---

**Built with ❤️ using Spring Boot and React**
