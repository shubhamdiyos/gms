# Demo / showcase run

This directory turns a running GMS backend into the tenant a presenter can click through, and
proves it did so.

Everything here is created through the product's own API — the platform owner signs in,
`POST /schools/create` provisions the school, and every pupil, fee, attendance row, exam and
announcement below that is a request the React SPA or the Flutter client already makes. There
is no direct SQL, no fixture loader and no test-only endpoint, so a demo that runs is a demo of
the real stack.

| Script | What it does |
|---|---|
| [`run-demo.sh`](run-demo.sh) | One command: (optionally) bring Docker up, seed, verify, serve the SPA and Flutter web. |
| [`seed-demo.sh`](seed-demo.sh) | Builds or reuses the tenant. Idempotent; safe to run twice. |
| [`verify-demo.sh`](verify-demo.sh) | Read-only. Signs in as all five demo roles and checks the data each screen reads. |

## One command

Against a backend that is already running:

```bash
GMS_SUPERADMIN_USERNAME=... GMS_SUPERADMIN_PASSWORD=... scripts/demo/run-demo.sh
```

From a machine with Docker and a filled-in `.env` (the same one `docker compose` uses, which must
carry `APP_SUPERADMIN_USERNAME`, `APP_SUPERADMIN_PASSWORD` and `SECURITY_JWT_SECRET`):

```bash
scripts/demo/run-demo.sh --docker
```

Either form writes `/tmp/gms-demo-tenant.env` (mode `600`), prints an access sheet, and then
serves:

| Client | URL | Notes |
|---|---|---|
| React SPA | <http://localhost:5173> | `npm run dev`; needs `gms_frontend/node_modules` |
| Flutter web | <http://localhost:3000> | serves `mobile/build/web`; built on first use if missing |
| API | <http://localhost:8080/api/v1> | the backend you started |

Ctrl-C stops the two web servers; the backend and database are left running. Useful flags:

```bash
scripts/demo/run-demo.sh --seed-only      # seed + verify, serve nothing
scripts/demo/run-demo.sh --no-verify      # skip the read-back
scripts/demo/run-demo.sh --no-react --no-flutter
```

Ports `5173` and `3000` are the two the backend allows by default
(`cors.allowed-origins`). Another port needs the backend restarted with
`CORS_ALLOWED_ORIGINS=http://localhost:<port>`; the runner warns when that is the case.

## The access sheet

`seed-demo.sh` prints the sheet at the end; it is reproduced here with the values the defaults
produce. `DEMO_PASSWORD` is **not** in this repository: pass it in, or let the seeder generate
one, and read it from the state file or the terminal that ran the seed.

| Role | Username | Password |
|---|---|---|
| SUPERADMIN (platform owner) | configured on the backend | never printed by these scripts |
| ADMIN | `dadmin` | the one demo password |
| TEACHER | `demo.teacher` | the one demo password |
| ACCOUNTANT | `demo.accountant` | the one demo password |
| STUDENT | `lfarouk` (Leila Farouk) | the one demo password |
| PARENT | `pfarouk` (Priya Farouk) | the one demo password |

Usernames are the server's derivation (first initial + surname, lowercased). If a name is
already taken, the server appends a digit and the seeder records the name it actually got, so
always read the sheet rather than assuming.

The first sign-in of a freshly issued account is forced through a password change; the seeder
does that rotation itself, which is why the demo accounts sign in directly.

## What is seeded

One school (`DEMO001`, "Demo Public School") with:

- the current academic year, one classroom (`Grade 9 A`), two subjects (Mathematics, Science);
- one teacher employee with a login, one accountant login;
- four pupils, one of them (`Leila Farouk`) with a login, and her parent's login linked to her;
- two teacher assignments and a Monday–Friday timetable (periods 1–2);
- two fee heads, five student-fee rows, and one part payment (`₹1,000`, reference
  `DEMO-RCPT-001`) recorded as the accountant;
- one exam, Leila's registration for the Mathematics paper, and her published result
  (`78`, grade `B+`);
- five school days of attendance with `PRESENT` / `ABSENT` / `LATE` mixed;
- one published announcement and one in-app notification.

## The eleven MVP features, and where to click

The frozen scope is [docs/PRODUCT_REQUIREMENTS.md §8](../../docs/PRODUCT_REQUIREMENTS.md); this
is the presenter's view of it. Every row is read back by `verify-demo.sh`.

| # | MVP item | React SPA | Flutter web |
|---|---|---|---|
| 1 | Superadmin auth + school provisioning | `superadmin/schools`, `superadmin/dashboard` — this tenant was created by that route | login screen → role home |
| 2 | RBAC for all six roles | each login lands on its own dashboard; other roles' routes redirect | `/students`, `/attendance/mark`, `/fees/collect`, `/attendance/child` are role-guarded |
| 3 | Student CRUD, per-school ids | `admin/students` | `/students` (admin), paged search |
| 4 | Academic setup | `admin/academic-years`, `admin/classrooms`, `admin/subjects` | home dashboard reads the current year |
| 5 | Server-side pagination, sort, search | `admin/students` pager and search box | `/students` pager and search box |
| 6 | Attendance with mixed statuses | `teacher/attendance` | `/attendance/mark` |
| 7 | One readable timetable | `student/timetable` | teacher and student home show the timetable |
| 8 | A result visible to the family | `student/results` | `/results` (student and parent) |
| 9 | Announcements + notifications | `admin/announcements`; the bell in the layout | home dashboard |
| 10 | Aggregated dashboards | `admin/dashboard`, `accountant/dashboard`, `teacher/dashboard`, `student/dashboard`, `parent/dashboard`, `superadmin/dashboard` | role home |
| 11 | Tenant isolation | log in as this school's admin: only `DEMO001` data appears, though the platform owns other schools | same, through the same API |

Item 11 is a mechanism, not a screen. Its negative proof is the live suite
`scripts/ci-live-tenancy.sh`, which boots its own backend and asserts that one tenant cannot read
another's rows; the demo shows the positive half — each role sees exactly its own school.

## Verifying without a browser

```bash
scripts/demo/verify-demo.sh
```

It signs in with the five demo logins from the state file, discovers the seeded attendance date
from Leila's own feed, and checks 32 responses across the dashboards, student directory, fees,
attendance, results, timetable, announcements, the notification bell, and every parent child
feed. It exits non-zero if any screen would open empty or error.

## Verifying in a real browser

`verify-demo.sh` proves the API answers; the two sweep scripts prove the screens render. Both
drive headless Chromium through Playwright, sign in with the state-file credentials, open every
route the role has, and fail on a missing heading, a "could not be loaded" banner, a console
error, an uncaught page error or any 5xx response. One screenshot per screen lands under
`artifacts/demo-sweep/` (git-ignored), and the verdicts are written to `sweep.json` next to them.

```bash
# React SPA on :5173 — six roles, 32 screens
node scripts/demo/browser-sweep.mjs

# Flutter web on :3000 — five roles, 14 screens
node scripts/demo/browser-sweep-flutter.mjs
```

Run them while `run-demo.sh` is serving; they read `DEMO_PASSWORD` from
`/tmp/gms-demo-tenant.env` (or the environment, which wins). The superadmin role is skipped
unless `GMS_SUPERADMIN_USERNAME` and `GMS_SUPERADMIN_PASSWORD` are exported — the platform
owner's password is never in the state file. Other role usernames can be overridden with
`GMS_DEMO_<ROLE>_USERNAME`; targets with `GMS_DEMO_WEB_URL`, `GMS_DEMO_FLUTTER_URL`,
`GMS_DEMO_SWEEP_OUT` and `GMS_DEMO_ENV`.

Playwright is resolved from `gms_frontend/node_modules`, so the SPA dependencies must be
installed at least once (`cd gms_frontend && npm ci && npx playwright install chromium`). Both
scripts exit non-zero on any failure and print `N passed, M failed` — that line is the evidence
to quote, not the screenshots alone.

What was last seen green on this tenant: **32/32** React screens across superadmin, admin,
teacher, accountant, student and parent, and **14/14** Flutter screens across admin, teacher,
accountant, student and parent, with zero console/5xx noise. The Postgres-backed Maven
integration tests are not part of this check and need Docker on `localhost:5433`; see
[`gms_backend/src/test/java/com/gms/BasePostgresIntegrationTest.java`](../../gms_backend/src/test/java/com/gms/BasePostgresIntegrationTest.java).

## Rerunning and resetting

- **Rerun** is safe. The seeder reuses the school, year, classroom, subjects, employees, logins,
  timetable, fees and exam, and only adds what is missing. The one value the API cannot give back
  is an exam subject id, so it lives in the state file; if the file is lost while the exam exists,
  the seeder stops with an instruction instead of creating a duplicate.
- **A second tenant**: `DEMO_SCHOOL_CODE=DEMO002 DEMO_SCHOOL_NAME='Second Demo School' ...`. Use
  another `GMS_DEMO_ENV` file too, or the first tenant's state is replaced.
- **A clean slate**: point the backend at an empty database. Do not delete rows from the demo
  tenant while it is being shown; the seeder treats what it finds as authoritative.
- **The timetable is never replaced.** Attendance rows reference its slots without a cascade, so
  an existing timetable is read and reused. To change the schedule, use a fresh database.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `GET /schools` answers 403 | the platform owner is still flagged `requirePasswordChange`. Boot with `APP_SUPERADMIN_FORCE_PASSWORD_CHANGE=false`, or sign in once and rotate the password. |
| "school exists but no administrator signs in with the demo password" | the tenant was seeded with another password, or `DEMO_PASSWORD` was regenerated after the state file was lost. Use the original password, or a fresh database. |
| `exam … exists but its subject ids are not in …` | the state file was lost. Use a fresh database, or delete that exam through the API first. |
| Login works but pages are empty | the backend is not the one that was seeded — check `GMS_BASE_URL` and that the SPA is pointed at the same host. |

The platform owner's password is deliberately never written to the state file, never printed,
and never committed. Rotate it the same way you would any production credential.
