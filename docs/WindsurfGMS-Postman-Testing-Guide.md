# WindsurfGMS Postman Testing Guide

> **Status note (2026-11-01).** This guide previously asserted that tenant isolation was
> enforced and that cross-school requests returned `403`. Repository audit findings
> [TEN-01…TEN-07](audit/REPOSITORY_AUDIT.md) and attack path **AP-3** in
> [SECURITY_AUDIT.md](audit/SECURITY_AUDIT.md) show that claim is **not** true today: the
> endpoints inherited from `AbstractCRUDController` are tenant-unchecked. Scenarios below
> are therefore written as *verification* steps with known-failing expectations, not as
> statements of current behaviour. See §"Known failures".

## 🎯 Overview

This guide provides instructions for testing the GMS School Management System API using
Postman. The collection covers roughly 19% of the live controller surface (finding
**DOC-05**); the remainder is exercised by the automated cross-tenant tests specified in
the *Verification method* lines of findings **TEN-01…TEN-07** in
[REPOSITORY_AUDIT.md](audit/REPOSITORY_AUDIT.md).

## 📁 Collection files

| File | Contents |
| --- | --- |
| `../../WindsurfGMS-Postman-Collection-Part1.json` | Auth, schools, users, employees |
| `../../WindsurfGMS-Postman-Collection-Part2.json` | Academic, assessment, finance, communication |

Both files reference secrets only through Postman variables — no literal credential is
stored in them (finding **DOC-02**).

## 🔐 Prerequisites: credentials come from the environment

Never hardcode a password in a request body, in this guide, or in a collection file.
Create a Postman environment with these variables and fill them from your local `.env`:

| Variable | Value source | Notes |
| --- | --- | --- |
| `base_url` | `http://localhost:8080` | or the deployed origin |
| `superadmin_username` | `APP_SUPERADMIN_USERNAME` | bootstrap default is being removed (**SEC-03**) |
| `superadmin_password` | `APP_SUPERADMIN_PASSWORD` | never committed |
| `admin_username` | a school admin you created yourself | |
| `admin_password` | same | |
| `second_school_admin_*` | a *different* tenant's admin | required for cross-tenant scenarios |
| `jwt_token` | set by the login test's post-request script | |

The initial SuperAdmin is created by `SuperAdminBootstrapRunner`; its properties and the
forced first-login password change are documented in
[authenticationQuickReference.md](../gms_backend/memory-bank/authenticationQuickReference.md).

## 📋 Testing workflow

### 1. Authentication and setup
1. Import both collection files.
2. Select the environment from the table above.
3. Run **Superadmin Login** (`POST {{base_url}}/api/v1/auth/login`) with
   `{{superadmin_username}}` / `{{superadmin_password}}`.
4. Confirm the response carries `token` and `expiresIn`, and that the script stored
   `{{jwt_token}}`.

### 2. School management
1. `GET /schools` — a SuperAdmin sees every school; a school ADMIN must see exactly one.
2. `GET /schools/{ownId}` — `200`.
3. `GET /schools/{otherId}` — must not disclose the other tenant.
4. `POST /schools/create` — use `{{admin_email}}` (your own address) for the new tenant's
   administrator.
5. `PUT /schools/update` — only the caller's own school.

### 3. Users and employees
Manage users and employees inside the caller's own school only.

### 4. Academic management
Subjects, classrooms, students — verify isolation for each entity family.

### 5. Assessment and finance
Exams, results, fee structures, invoices, payments — the paths covered by findings
**FIN-01…FIN-08**.

## 🛡️ Cross-tenant security scenarios

Run each scenario as **School A's ADMIN** against a resource owned by **School B**.

| # | Request | Required outcome | Status on `master` |
| --- | --- | --- | --- |
| S1 | `GET /students` as A, then `GET /students/{B.id}` | `404` (no disclosure) | ❌ **fails** — inherited endpoint, no tenant predicate |
| S2 | `PUT /students/update` with `B.id` | `403`/`404` | ⚠️ depends on service check |
| S3 | `DELETE /students/{B.id}` | `404` | ❌ **fails** — `AbstractCRUDController.deleteById` |
| S4 | `DELETE /schools/{B.id}` as an ADMIN | `403` | ❌ **fails** — **AP-3**, any ADMIN can delete another tenant |
| S5 | `GET /fee-payments/student/{B.stuId}` | `404` | ❌ **fails** — **AP-4** |
| S6 | `GET /results` filtered to B's student | `404`/empty | ❌ **fails** |
| S7 | A PARENT reads another student's profile | `403` | ❌ **fails** (path rule ordering, **SEC-06**) |
| S8 | `POST /users/create` requesting `roles:["SUPERADMIN"]` | rejected | ❌ **fails** — **AP-2** escalation |

Also verify the *positive* cases so fixes do not break legitimate access: a STUDENT can
read `GET /students/profile` for themselves, a PARENT can read their linked children, a
TEACHER can read their assigned classroom.

## 🧪 Test data management

- Provision data **during the run** (school → admin → student) and capture IDs into
  variables; the collections already do this.
- Do not reuse a shared long-lived dataset — a test that mutates another test's rows makes
  cross-tenant failures look like flakiness.
- Admin accounts created through `POST /schools/create` currently receive a password
  derived from the username (**ACD-12**). That generator is scheduled for removal; until
  then, treat every account created that way as *publicly guessable* and use it only
  against a disposable database.

## 📊 Interpreting results

`200` on any ❌ row above is a **confirmed tenant-isolation defect**, not a passing test.
Record the finding ID from [REPOSITORY_AUDIT.md](audit/REPOSITORY_AUDIT.md) in the test run
notes and re-run after the security remediation branch merges.

### Environment variables
- `{{base_url}}` — API origin
- `{{jwt_token}}` — set automatically after login
- `{{school_id}}`, `{{user_id}}`, `{{employee_id}}` — captured during creation

## 🛠️ Troubleshooting

1. **`401` on login** — confirm the account exists; there is no public self-service
   provisioning for privileged roles (**SEC-09**).
2. **`403` on a portal route as STUDENT/PARENT** — expected today; caused by
   `SecurityConfig` matcher ordering (**SEC-06**), not by your token.
3. **`500` with a stack trace body** — the global exception handler leaks internals
   (**SEC-08**); capture the response, it is evidence.
4. **Empty `getMy*` responses** — several student/parent portal endpoints are stubs
   (**ACD-09**).

## 📈 Best practices

1. Run the collection against a **disposable** database only.
2. Never point a collection at production Neon data (**mandate §25.1–2**).
3. Add a new request for every endpoint you change; the collection is the contract smoke
   test until OpenAPI generation lands (**BKL-09**).
4. Keep results, not secrets, in committed files.
