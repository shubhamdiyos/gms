/**
 * The API half of an end-to-end run: the arranging that a browser cannot do for itself.
 *
 * A Playwright journey is only worth writing if the state it clicks through is reachable by a
 * real user, and here it is. Every call below is a route the SPA itself calls — `authService.js`
 * posts to `/auth/login`, `apiSlice.js` posts to `/schools/create` and `/classrooms/create` — so
 * the fixture arranges a tenant the way the product does, then hands the browser nothing but
 * credentials. What the tests then assert is the part no API script can see: which screen the
 * router lands on, what the grid renders, and whether the console stayed clean while it happened.
 *
 * Nothing here is a secret. `GMS_E2E_PLATFORM_USERNAME` and `GMS_E2E_PLATFORM_PASSWORD` name the
 * platform account that `SuperAdminBootstrapService` mints into a database existing for one run;
 * `scripts/ci-playwright-e2e.sh` exports them from the `GMS_SUPERADMIN_USERNAME` /
 * `GMS_SUPERADMIN_PASSWORD` pair the live-tenancy harness already requires, and feeds the same
 * pair to the boot as `APP_SUPERADMIN_*` — the two names application.properties actually bind.
 * The administrator credential this file returns is the one the creation response hands the
 * platform owner (`SchoolResponse.adminUsername` / `temporaryPassword`, populated only on create
 * because the stored row is a hash and the email may be undeliverable). `provisionTenant` still
 * decodes the JWT and refuses the run unless the token's `schoolId` is the school it just
 * created — a credential that names another tenant must not drive a browser at this one.
 */

export const API_ORIGIN = (process.env.GMS_E2E_API_ORIGIN ?? 'http://localhost:8080').replace(/\/$/, '');

// The production bundle asks for `/api/v1` against its own origin (see `getApiBaseUrl()` in
// `src/constants/index.js`), so the browser and this file reach the same API by different
// routes on purpose: a relative path through `vite preview`'s proxy for the page, and an
// absolute URL for arranging. They are the same server; only the CORS question differs — and
// that difference is what `the API refuses a browser…` measures, from the page, against this
// absolute origin rather than through the proxy that hides it.
export const API_ROOT = `${API_ORIGIN}/api/v1`;

let tenantsIssued = 0;

function requireEnv(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(
      `${name} is not set. The browser suite drives the same backend the live-tenancy harness boots, ` +
        'so it arranges its tenants through the platform account that harness names.',
    );
  }
  return value;
}

/**
 * The double-submit handshake `SecurityConfig` requires of every state-changing call.
 *
 * The boot this suite drives has CSRF enabled, so a POST without the `XSRF-TOKEN` cookie and
 * the matching `X-XSRF-TOKEN` header is answered 403 before any controller runs. Playwright's
 * `APIRequestContext` keeps cookies on its own, so the GET below is all the state this needs;
 * the token is cached per context because a new test gets a new context and therefore a new
 * cookie jar. Keying the cache by context rather than module-wide is the part that matters:
 * a token from a previous test's jar would be compared against this test's cookie and refused.
 */
const csrfTokens = new WeakMap();

async function csrfTokenFor(request) {
  const cached = csrfTokens.get(request);
  if (cached) return cached;

  const response = await request.get(`${API_ROOT}/auth/csrf-token`);
  const payload = await response.json().catch(() => null);
  if (!response.ok() || !payload?.token) {
    throw new Error(
      `the CSRF handshake failed (answered ${response.status()}); every state-changing call ` +
        'needs the XSRF-TOKEN cookie this route issues',
    );
  }
  csrfTokens.set(request, payload.token);
  return payload.token;
}

/** A JSON POST with the CSRF handshake already done. Every write in this file goes through it. */
export async function postJson(request, route, data, token) {
  const headers = {
    'Content-Type': 'application/json',
    'X-XSRF-TOKEN': await csrfTokenFor(request),
  };
  if (token) headers.Authorization = `Bearer ${token}`;
  return request.post(`${API_ROOT}${route}`, { data, headers });
}

async function post(request, route, body, token) {
  const response = await postJson(request, route, body, token);
  return { status: response.status(), payload: await response.json().catch(() => null) };
}

/**
 * The JWT a successful login issues.
 *
 * `AuthController.login` no longer echoes the token in the response body: the credential is
 * issued as the HttpOnly `gms_auth` cookie, which the cookie-authenticated SPA and Flutter
 * client read implicitly. The fixture needs an explicit credential because it acts as several
 * identities at once (owner and intruder in the same test), so it lifts the token out of the
 * `Set-Cookie` header and sends it as a bearer token — a shape `JwtAuthenticationFilter`
 * still accepts for API clients. Reading the body first keeps this working against a build
 * that has not migrated yet.
 */
function tokenFromLogin(response, payload) {
  if (payload?.accessToken) return payload.accessToken;
  const cookies = response.headers()['set-cookie'] ?? '';
  const match = cookies.match(/(?:^|[,\s])gms_auth=([^;,\s]+)/);
  return match ? match[1] : null;
}

/** Signs in over the API and returns the credential the rest of the fixture will use. */
export async function login(request, credentials) {
  const response = await postJson(request, '/auth/login', credentials);
  const payload = await response.json().catch(() => null);
  return { status: response.status(), payload, token: tokenFromLogin(response, payload) };
}

/**
 * The password the fixture sets on an administrator whose rotation it performs itself.
 *
 * `PasswordChangeEnforcementFilter` blocks every business call from an account still carrying
 * `requirePasswordChange`, so a tenant whose classroom is arranged by the fixture cannot use
 * the issued password for that write. Journeys that drive the rotation screen keep the issued
 * credential (`keepIssuedPassword: true`); the rest are rotated here and handed the new one.
 */
const TENANT_ADMIN_PASSWORD = 'Tenant_Admin_2026!';

/** Completes the forced rotation over the API, the same call the SPA's screen makes. */
export async function changePassword(request, currentPassword, newPassword, token) {
  const response = await postJson(
    request,
    '/auth/change-password',
    { oldPassword: currentPassword, newPassword },
    token,
  );
  return { status: response.status() };
}

/** Creates the classroom a journey registers pupils into. */
export async function createClassroom(request, token, body = { name: 'Grade 9 A', grade: '9', section: 'A' }) {
  const response = await postJson(request, '/classrooms/create', body, token);
  return { status: response.status(), payload: await response.json().catch(() => null) };
}

/**
 * The message for a fixture call that did not answer the way the product's own contract says it
 * must. The server's body is quoted because it is the server's diagnosis — `GlobalExceptionHandler`
 * names the rejected field and the constraint it broke — and a fixture that printed only a status
 * code sent the reader off to reproduce a 400 by hand.
 */
function arrangeFailed(route, result, sent) {
  return (
    `${route} was not created (answered ${result.status})\n` +
    `  sent: ${JSON.stringify(sent).slice(0, 240)}\n` +
    `  said: ${JSON.stringify(result.payload ?? '(no body)').slice(0, 400)}`
  );
}

/**
 * One claim out of the access token, without a JWT library.
 *
 * `AuthController.login` answers with a token and a flag and no user object; the SPA reads the rest
 * of the identity by decoding the token (`authService.js:28-38`). This is the same move, for the one
 * claim the fixture has to be sure about: which school the credential it derived actually belongs
 * to.
 */
function claimOf(token, claim) {
  const payload = token.split('.')[1];
  if (!payload) throw new Error('the access token is not a JWT, so nothing here can be trusted');
  return JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'))[claim];
}

/**
 * One school, its administrator, and a classroom to register a pupil into.
 *
 * A school code has to be unique per tenant on this database, so the tag comes from the run —
 * CI stamps one per job, a laptop stamps one per `npm run e2e` — and a counter distinguishes the
 * tenants inside it. Each test gets its own tenant rather than sharing one: a journey that
 * rotates a password spends that credential, and two tests signing in as the same account with
 * two different passwords is a race dressed up as a fixture.
 */
export async function provisionTenant(request, label, { keepIssuedPassword = false } = {}) {
  const tag = requireEnv('GMS_E2E_TAG');
  const schoolCode = `E2E${tag}${++tenantsIssued}${label}`;
  // The tag is in the administrator's name as well as the school code, and that is not decoration:
  // `SchoolServiceImpl.generateUniqueUsername` suffixes a colliding base (`pa1` becomes `pa11`)
  // while `tempPassword` stays the *unsuffixed* base, so a second run over the same database that
  // re-used a name would be handed a credential that belongs to the first run's school. The second
  // run measured exactly that: the fixture logged in, the server said 200, and the classroom it
  // then created collided with the one already in that other tenant. `tenantOwnsTheSchoolItClaims`
  // below is the guard for the general case.
  const adminFullName = `Playwright ${label}${tag}${tenantsIssued}`;

  const platform = await login(request, {
    username: requireEnv('GMS_E2E_PLATFORM_USERNAME'),
    password: requireEnv('GMS_E2E_PLATFORM_PASSWORD'),
  });
  if (platform.status !== 200 || !platform.token) {
    throw new Error(
      `the platform account could not log in (answered ${platform.status}); is the backend on ${API_ORIGIN}?`,
    );
  }
  const platformToken = platform.token;

  const created = await post(
    request,
    '/schools/create',
    {
      schoolName: `Playwright ${label} Academy`,
      schoolCode,
      email: `office.${schoolCode.toLowerCase()}@e2e.invalid`,
      adminFullName,
      adminEmail: `admin.${schoolCode.toLowerCase()}@e2e.invalid`,
      address: '1 Browser Row',
      phone: '9800001001',
      principalName: 'Head of Browser',
      establishedYear: 2018,
      boardAffiliation: 'CBSE',
    },
    platformToken,
  );
  if (created.status !== 200 || !created.payload?.id) {
    throw new Error(arrangeFailed(`school ${schoolCode}`, created, { schoolCode, adminFullName }));
  }

  const admin = {
    username: created.payload.adminUsername,
    password: created.payload.temporaryPassword,
  };
  if (!admin.username || !admin.password) {
    throw new Error(
      `school ${schoolCode} was created but the response carried no administrator credential ` +
        `(${JSON.stringify(created.payload).slice(0, 240)}); the fixture cannot sign in as the ` +
        'admin it just provisioned',
    );
  }

  // The account the server just provisioned is issued a temporary password and flagged
  // `requirePasswordChange`; `PasswordChangeEnforcementFilter` refuses every business call
  // until that flag clears. A journey that clicks through the rotation screen therefore keeps
  // the issued credential and arranges its classroom after rotating (`keepIssuedPassword`),
  // while a journey that only needs a tenant rotates over the API here, the same call the
  // screen makes, and continues with the new credential.
  const asAdmin = await login(request, admin);
  if (asAdmin.status !== 200 || !asAdmin.token) {
    throw new Error(
      `the provisioned administrator could not log in with the server's own rule ` +
        `(answered ${asAdmin.status}) — has SchoolServiceImpl's password derivation changed?`,
    );
  }
  // `AuthTokenResponse` carries the flag at the top level — the SPA decodes the rest of the
  // user from the JWT itself (authService.js:28-38), so this is the whole contract.
  if (asAdmin.payload?.requirePasswordChange !== true) {
    throw new Error(
      'the provisioned administrator was not asked to change its password, so the rotation this ' +
        'suite clicks through would be testing a screen no new account ever sees',
    );
  }

  // The credential was derived rather than returned, so a 200 here is not yet proof that it belongs
  // to the school above. It has to be checked, or a run over a database that already holds that
  // username arranges one tenant and drives the browser at another — which is what the second run
  // of this suite did, and it surfaced as a 400 on a classroom name that only collided in the
  // wrong tenant.
  const tokenSchoolId = claimOf(asAdmin.token, 'schoolId');
  if (tokenSchoolId !== created.payload.id) {
    throw new Error(
      `the derived credential signed into school ${tokenSchoolId}, not school ${created.payload.id} ` +
        `(${schoolCode}) that this fixture just created - two schools have been handed the same ` +
        'administrator username, so the name that generates it has to be unique per run',
    );
  }

  if (keepIssuedPassword) {
    return {
      schoolCode,
      schoolId: created.payload.id,
      admin,
      adminToken: asAdmin.token,
      classroomId: null,
      pupilSurname: `${label}Pupil`,
    };
  }

  const rotated = await changePassword(
    request,
    admin.password,
    TENANT_ADMIN_PASSWORD,
    asAdmin.token,
  );
  if (rotated.status !== 200) {
    throw new Error(
      `the issued administrator password could not be rotated (answered ${rotated.status}); ` +
        'every write below would be refused while the flag is set',
    );
  }

  const adminCredential = { username: admin.username, password: TENANT_ADMIN_PASSWORD };
  const afterRotation = await login(request, adminCredential);
  if (afterRotation.status !== 200 || !afterRotation.token) {
    throw new Error(
      `the rotated administrator credential did not sign in (answered ${afterRotation.status})`,
    );
  }

  const classroomBody = { name: 'Grade 9 A', grade: '9', section: 'A' };
  const classroom = await createClassroom(request, afterRotation.token, classroomBody);
  if (classroom.status !== 200) {
    throw new Error(arrangeFailed('classroom', classroom, classroomBody));
  }

  return {
    schoolCode,
    schoolId: created.payload.id,
    admin: adminCredential,
    adminToken: afterRotation.token,
    classroomId: classroom.payload?.id,
    pupilSurname: `${label}Pupil`,
  };
}

/**
 * Reads the roll the way the page does, for the assertion that a row the browser created is
 * really in the tenant rather than only in the grid's local state.
 */
export async function searchStudents(request, token, query) {
  const response = await request.get(`${API_ROOT}/students/search?query=${encodeURIComponent(query)}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return { status: response.status(), payload: await response.json().catch(() => null) };
}

/**
 * The username the server will derive from a first name and last name.
 *
 * `UsernameGenerator.generateUsername` takes the first character of the first name, appends the
 * whole last name, lowercases everything, and strips anything outside `[a-z0-9]`. If the result
 * already exists, it suffixes a counter — but for the first account with a given name the bare
 * base is what the server writes, and the tests rely on matching that derivation to log in.
 */
export function generatedUsername(firstName, lastName) {
  return (firstName.charAt(0) + lastName).toLowerCase().replace(/[^a-z0-9]/g, '');
}

/** One claim out of the access token, without a JWT library (re-exported for test convenience). */
export { claimOf };

/**
 * Creates a student and its auto-provisioned user account in one step.
 *
 * The server's `StudentServiceImpl.createUserForStudent` mints a login whose username is
 * `generatedUsername(firstName, lastName)` and whose password is the literal `welcome123`
 * (satisfies `PasswordValidator`: upper, lower, digit, special). The account is flagged
 * `requirePasswordChange`, so the browser journey will click through the rotation screen
 * before it reaches any dashboard.
 */
export async function createStudentAccount(request, adminToken, studentData) {
  const response = await postJson(request, '/students/create', studentData, adminToken);
  const payload = await response.json().catch(() => null);
  return { status: response.status(), payload };
}

/**
 * Creates a parent linked to one or more students, and the auto-provisioned user account.
 *
 * `ParentServiceImpl.createParent` resolves the student ids through the school-scoped query,
 * then `createUserForParent` mints a login the same way the student path does — generated
 * username, a randomly issued temporary password returned once in the create response
 * (`ParentResponse.temporaryPassword`), and `requirePasswordChange` set. The `studentIds` field on
 * `ParentRequest` is what builds the `parent_student` link rows the backend later consults
 * to decide whether a parent may read a child's attendance, results, and fees (F-3).
 */
export async function createParentAccount(request, adminToken, parentData) {
  const response = await postJson(request, '/parents/create', parentData, adminToken);
  const payload = await response.json().catch(() => null);
  return { status: response.status(), payload };
}

/**
 * Creates a teacher employee and registers a login for it.
 *
 * Two calls because the domain splits them: `POST /employees/create` writes the staff row,
 * then `POST /auth/self-register` mints the user account linked to that employee. The
 * self-register endpoint hard-codes the role to TEACHER and sets `requirePasswordChange` to
 * false — a teacher account created this way can sign in and go straight to the dashboard
 * without the rotation step that student and parent accounts demand.
 */
export async function createTeacherAccount(request, adminToken, employeeData, schoolId, username, password) {
  const empResponse = await postJson(request, '/employees/create', employeeData, adminToken);
  const empPayload = await empResponse.json().catch(() => null);
  if (empResponse.status() !== 200 || !empPayload?.id) {
    return { status: empResponse.status(), payload: empPayload, empId: null, username };
  }

  const regResponse = await postJson(
    request,
    '/auth/self-register',
    { empId: empPayload.id, schoolId, username, password },
    adminToken,
  );
  const regPayload = await regResponse.json().catch(() => null);
  return { status: regResponse.status(), payload: regPayload, empId: empPayload.id, username };
}

/** Creates a subject the teacher can be assigned to teach. */
export async function createSubject(request, adminToken, subjectData) {
  const response = await postJson(request, '/subjects/create', subjectData, adminToken);
  const payload = await response.json().catch(() => null);
  return { status: response.status(), payload };
}

/** Assigns a teacher to a classroom/subject pair for an academic year. */
export async function createTeacherAssignment(request, adminToken, assignmentData) {
  const response = await postJson(request, '/teacher-assignments', assignmentData, adminToken);
  const payload = await response.json().catch(() => null);
  return { status: response.status(), payload };
}
