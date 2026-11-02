# 🎓 GMS - Global Management System

A comprehensive School Management System built with Spring Boot and React, featuring role-based access control, multi-tenant architecture, and complete CRUD operations.

> **Read this sentence before believing the one above it.** "Multi-tenant architecture" describes the *data model*
> (25 of 36 entities carry a `school_id`), not the *enforcement*: [docs/audit/REPOSITORY_AUDIT.md](docs/audit/REPOSITORY_AUDIT.md)
> records `TEN-01…07`, including entities with no tenant column at all and inherited endpoints with no ownership
> check. "Complete CRUD operations" is literally true of `AbstractCRUDController`, which auto-registers an unguarded
> `DELETE /{id}` on six resources (`SEC-05`) and is scheduled for deletion by `ADR-023`.
>
> **Where the authoritative documents are:** [docs/README.md](docs/README.md) is the index of who owns what. The
> sequence and the definition of done are [docs/ENGINEERING_ROADMAP.md](docs/ENGINEERING_ROADMAP.md); the frozen
> product scope is [docs/PRODUCT_REQUIREMENTS.md](docs/PRODUCT_REQUIREMENTS.md); module boundaries, the tenancy
> mechanism and the index design are [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). **Nothing in GMS has been
> deployed**, and no stage past Stage 1 has started. Setup commands below are real; the state of testing is stated
> honestly in [docs/audit/BASELINE_STATUS.md](docs/audit/BASELINE_STATUS.md).

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

2. **Give the application a database and a signing key**

`gms_backend/src/main/resources/application.properties` carries no host and no credential. The datasource is
`${DB_URL:}` / `${DB_USERNAME:}` / `${DB_PASSWORD:}` (`:24-26`), each with an intentionally empty default, and
`security.jwt.secret` (`:48`) has no default at all. That is the SEC-01 remediation, and the empty defaults are why a
first run **fails instead of connecting somewhere you did not mean**: measured on 2026-11-02,
`java -jar target/gms-0.1.0-SNAPSHOT.jar` with no environment stops with
`IllegalStateException: security.jwt.secret is missing, shorter than 32 bytes, or a retired public default`
(`JwtTokenProvider.java:64`). Set the three values rather than editing the file, so nothing local can become a
committed default:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # JDK 21 only — see below

# the disposable Postgres; it publishes to 127.0.0.1:5433 and to no other interface (docker-compose.yml:18)
cp .env.example .env        # then fill in DB_PASSWORD
docker compose up -d postgres

export DB_URL=jdbc:postgresql://localhost:5433/gms
export DB_USERNAME=gms_user
export DB_PASSWORD=<DB_PASSWORD from your .env>
export SECURITY_JWT_SECRET=$(openssl rand -base64 48)

mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Two caveats that are real today, not aspirational:

* **Java 21 is required.** With a newer default JDK the build fails with
  `Fatal error compiling: … TypeTag :: UNKNOWN` (a Lombok/javac incompatibility). Nothing in the repository pins this
  yet: there is no `.mvn/jvm.config`, no `maven-enforcer` rule and no `.java-version`, and `pom.xml:109`'s
  `<source>${java.version}</source>` constrains the language level, not the compiler that reads it.
* **The schema comes from Flyway, and only from Flyway.** `db/migration/` holds `V1` through `V7`,
  `spring.flyway.enabled=${FLYWAY_ENABLED:true}` (`application.properties:43`) is on by default, and
  `spring.jpa.hibernate.ddl-auto=validate` is a literal in all three profiles. An empty database is therefore built
  during boot — the log shows `Migrating schema "public" to version` seven times, ending at
  `"7 - record who wrote the pupil"` — and Hibernate never writes it. The `--spring.jpa.hibernate.ddl-auto=update`
  workaround this section used to recommend is gone from the repository: ADR-022 removed the `${JPA_DDL_AUTO:…}`
  indirection, so there is no environment variable left that can reach `update` in any profile.

Full reproduction steps and measured outputs: [docs/audit/BASELINE_STATUS.md](docs/audit/BASELINE_STATUS.md).

3. **Start the backend**

The command in step 2 is the start. Plain `mvn spring-boot:run` is not a shortcut around it: with no `DB_URL` and no
`SECURITY_JWT_SECRET` exported, it refuses to boot for the reason quoted above.

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

**One flag to know about before a demo.** `VITE_MOCK_AUTH=true` (read at build time by
`src/services/authService.js:13` and `src/store/api/apiSlice.js:16`) routes every request to in-memory fixtures and
makes the login form accept any password, so a stale local `.env` silently turns the whole application into fake data.
It is unset here — `gms_frontend/` carries only `.env.production`, and the committed `dist/` bundle contains no
mock module at all — so both `npm run dev` and the built site talk to the real backend.

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

**Both halves have tests now.** The backend measured 36 tests in 6 classes on 2026-11-01, 121 in 18 classes on
2026-11-02, and **126 in 19 classes** the same day once `RetiredRowReadContractTest` landed — the class the
live-tenancy harness's first run earned, pinning that a retired row reads 404 rather than 403; the frontend's earlier "no tests at all" is closed too — `npm test` is `vitest run` over 2 files and 9
tests, and the CI step fails rather than shrugging if the script or a test file goes missing. So this section's
original claim that the repository had none is no longer true, and it is corrected rather than left to rot.

| Command | What actually happens today |
| --- | --- |
| `mvn -B verify` | runs `gms_backend/src/test` — **19 test classes, 126 tests** on this machine (`Tests run: 121` was `8a83d37`'s CI line, before the fifth contract class arrived) — and writes `target/site/jacoco/index.html`. **Locally it needs a database and two environment variables:** at that 121-test baseline,
`DB_USERNAME`/`DB_PASSWORD` unset reported `Tests run: 113` plus one error in `SqlStatementCountPerRequestTest`
(`FATAL: role "gms_user" does not exist`) and **BUILD FAILURE**; with them exported against the operator's Homebrew
PostgreSQL 15.8 on `127.0.0.1:5432` the current tree reads `Tests run: 126, Failures: 0, Errors: 0, Skipped: 0` and BUILD
SUCCESS (measured on this machine on 2026-11-02). The refusal is deliberate — the class does not skip when the database is missing, because
a skipped guard reads as a passing one — and CI supplies the same three inputs through its `postgres:15` service
container. This list used to name six classes; the **thirteen** that arrived since are the tenancy and authorization guards (`RepositoryTenantScopeArchTest`, `TenantScopedReadArchTest`, `TenantParameterArchTest`, `EndpointInheritanceTest`, `EntityTenantColumnArchTest`, `EntityLazyAccessorArchTest`, `DenyByDefaultAuthorizationArchTest`, `TenantContextTest`, `TenantContextAuditorAwarePrincipalAttributionTest`, `NotificationRecipientMatchTest`) plus the three contract guards (`EmptyCollectionContractTest`, `SqlStatementCountPerRequestTest`, `RetiredRowReadContractTest`) |
| `mvn -DskipTests` | still the command the production image build uses (`gms_backend/Dockerfile:11`), so the shipped jar is not built from a tested compile — see INF-09 |
| `npm test` | **runs.** `gms_frontend/package.json` declares `"test": "vitest run"` and there are two suites — `ErrorBoundary.test.jsx` (4) and `ProtectedRoute.test.jsx` (5) — **9 tests, 2 files, green**. In CI the step is blocking and refuses two things it used to tolerate: a missing `test` script, and a script with no `src/**/*.test.*` file behind it (measured on `8a83d37`: `Test Files 2 passed (2)`, `Tests 9 passed (9)`). `--run` is passed explicitly so no future edit to the script can put a watcher on a runner |
| `npm run lint` | runs, and reports **16 errors across 7 files** plus 1 warning — measured from CI's own summary line at `8a83d37`, not from this table's memory; it used to say 47 errors across 22 files, a count that no longer holds. CI fails the job only on the classes that break at runtime (`no-undef` under `src/`, any fatal parse error): the same run reports **0 in the gated classes**, so the gate is green while the count is recorded, not ignored |
| JaCoCo | bound to `prepare-agent` + `report` at `verify`, so coverage is measured; there is still no coverage **threshold** that can fail a build |
| CI | **three** workflows now run on every pull request: `.github/workflows/ci.yml` (four jobs, and the `backend` job boots a `postgres:15` service container so the DB-backed tests really have a database), `.github/workflows/validate-workflows.yml` (actionlint plus fifteen structure guards over the yaml itself) and `.github/workflows/gate-coverage.yml` (a meta check that refuses a pull whose owed gates did not run). **Since 2026-11-02 `master` requires `Gate coverage`**, with `strict` and `enforce_admins` — a red or missing check now blocks a merge even for an administrator, demonstrated once in [docs/ENGINEERING_ROADMAP.md](docs/ENGINEERING_ROADMAP.md) Stage 5 criterion 4. The former `deploy-backend.yml` was **retired on 2026-11-01** — it SSHed to an EC2 host that is permanently closed — so a merge to `master` still deploys nothing anywhere |
| `scripts/verify-security.sh` | 18 executable security assertions against a local boot — see [docs/audit/BASELINE_STATUS.md](docs/audit/BASELINE_STATUS.md) §13.2 |

What this table's last paragraph used to say — "no integration tests against a real database, no frontend tests, no
end-to-end suite" — is no longer true of the first two and only partly true of the third, and the correction belongs
in the README rather than only in the roadmap:
- **Against a real database**: `SqlStatementCountPerRequestTest` drives nine real HTTP requests through the full
  filter chain on a `RANDOM_PORT` against a PostgreSQL and asserts how many statements each prepares; CI runs it
  against a container, not a mock. It is a per-request detector, not a schema-migration harness.
- **Frontend**: 9 Vitest + Testing Library tests over the two components that had been failing in the browser.
- **End-to-end**: `scripts/verify-mvp-e2e.sh` exists and last read **298 checks / 0 failed / 11 of 11 MVP features
  PROVED**. It drives a *running* backend, and since 2026-11-02 CI runs it: the `live-tenancy` job boots the jar
  the `backend` job built against a throwaway `postgres:15` on the runner's loopback and executes nine live steps
  through `scripts/ci-live-tenancy.sh` — the same command an operator types. The job that used to declare itself
  `skipping`, blocked on twelve secret names the repository does not have, now reads no secret at all.

What is still genuinely missing for safe refactoring is coverage **thresholds**, a Playwright browser journey, and
Testcontainers (the DB-backed tests use a real server, not a containerised-per-class one). Until those land, the
manual verification path alongside the script above is the Postman collection — 45 requests against roughly 230
endpoint mappings — documented in
[docs/WindsurfGMS-Postman-Testing-Guide.md](docs/WindsurfGMS-Postman-Testing-Guide.md). Run it against a disposable
database only.

## 📝 Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/new-feature`
3. Commit changes: `git commit -m 'Add new feature'`
4. Push to branch: `git push origin feature/new-feature`
5. Submit a pull request

## 📄 License

**No license has been chosen for this project.** The repository contains no `LICENSE` file, and this section
previously claimed MIT and linked a file that does not exist — corrected rather than quietly deleted, because
choosing a license is an owner decision with legal effect, not a documentation detail. Until one is recorded,
default copyright terms apply and nothing here may be assumed reusable.

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
