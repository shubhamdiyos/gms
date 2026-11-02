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
 * The administrator credential this file returns is derived, not invented: see
 * `initialPasswordFor`, which restates the server's own rule. That restatement is exactly why
 * `provisionTenant` decodes the JWT and refuses the run unless the token's `schoolId` is the
 * school it just created — `ACD-13` records the case where the rule and the stored row diverge.
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
 * The username the server will have given this school's administrator.
 *
 * `<first initial><surname>`, lowercased, with anything outside `[a-z0-9]` removed —
 * `SchoolServiceImpl.java:97-98`. `scripts/verify-mvp-e2e.sh:211` keeps the same rule in bash
 * for the same reason: the provisioning response does not carry the credential, so the only way
 * to ask for it afterwards is to derive it the way the writer does. Restating a rule the server
 * owns is a coupling, and it is a deliberate one — if that line ever changes, the fixture fails
 * loudly at `signInWithIssuedPassword` rather than quietly testing some other account.
 */
export function initialPasswordFor(fullName) {
  const parts = fullName.trim().split(/\s+/);
  const base = `${parts[0].charAt(0)}${parts.length > 1 ? parts[1] : ''}`
    .toLowerCase()
    .replace(/[^a-z0-9]/g, '');
  return { username: base, password: `${base}@123` };
}

async function post(request, route, body, token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  const response = await request.post(`${API_ROOT}${route}`, { data: body, headers });
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
export async function provisionTenant(request, label) {
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

  const platform = await post(request, '/auth/login', {
    username: requireEnv('GMS_E2E_PLATFORM_USERNAME'),
    password: requireEnv('GMS_E2E_PLATFORM_PASSWORD'),
  });
  if (platform.status !== 200 || !platform.payload?.accessToken) {
    throw new Error(
      `the platform account could not log in (answered ${platform.status}); is the backend on ${API_ORIGIN}?`,
    );
  }
  const platformToken = platform.payload.accessToken;

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

  const admin = initialPasswordFor(adminFullName);

  // The account the server just provisioned can already hold a token: `requirePasswordChange`
  // is a flag the login response carries and the SPA obeys, and it is the SPA's own rule, not
  // the API's (no server route refuses a write for it). So the classroom below is created with
  // the issued password, and the browser then does the rotation a human would have done first.
  const asAdmin = await post(request, '/auth/login', admin);
  if (asAdmin.status !== 200 || !asAdmin.payload?.accessToken) {
    throw new Error(
      `the provisioned administrator could not log in with the server's own rule ` +
        `(answered ${asAdmin.status}) — has SchoolServiceImpl's password derivation changed?`,
    );
  }
  // `AuthTokenResponse` carries the flag at the top level, beside the token — the SPA decodes the
  // rest of the user from the JWT itself (authService.js:28-38), so this is the whole contract.
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
  const tokenSchoolId = claimOf(asAdmin.payload.accessToken, 'schoolId');
  if (tokenSchoolId !== created.payload.id) {
    throw new Error(
      `the derived credential signed into school ${tokenSchoolId}, not school ${created.payload.id} ` +
        `(${schoolCode}) that this fixture just created - two schools have been handed the same ` +
        'administrator username, so the name that generates it has to be unique per run',
    );
  }

  const classroomBody = { name: 'Grade 9 A', grade: '9', section: 'A' };
  const classroom = await post(request, '/classrooms/create', classroomBody, asAdmin.payload.accessToken);
  if (classroom.status !== 200) {
    throw new Error(arrangeFailed('classroom', classroom, classroomBody));
  }

  return {
    schoolCode,
    admin,
    adminToken: asAdmin.payload.accessToken,
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
  const response = await request.post(`${API_ROOT}/students/create`, {
    data: studentData,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${adminToken}` },
  });
  const payload = await response.json().catch(() => null);
  return { status: response.status(), payload };
}

/**
 * Creates a parent linked to one or more students, and the auto-provisioned user account.
 *
 * `ParentServiceImpl.createParent` resolves the student ids through the school-scoped query,
 * then `createUserForParent` mints a login the same way the student path does — generated
 * username, `welcome123` password, `requirePasswordChange` set. The `studentIds` field on
 * `ParentRequest` is what builds the `parent_student` link rows the backend later consults
 * to decide whether a parent may read a child's attendance, results, and fees (F-3).
 */
export async function createParentAccount(request, adminToken, parentData) {
  const response = await request.post(`${API_ROOT}/parents/create`, {
    data: parentData,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${adminToken}` },
  });
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
  const empResponse = await request.post(`${API_ROOT}/employees/create`, {
    data: employeeData,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${adminToken}` },
  });
  const empPayload = await empResponse.json().catch(() => null);
  if (empResponse.status() !== 200 || !empPayload?.id) {
    return { status: empResponse.status(), payload: empPayload, empId: null, username };
  }

  const regResponse = await request.post(`${API_ROOT}/auth/self-register`, {
    data: { empId: empPayload.id, schoolId, username, password },
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${adminToken}` },
  });
  const regPayload = await regResponse.json().catch(() => null);
  return { status: regResponse.status(), payload: regPayload, empId: empPayload.id, username };
}

/** Creates a subject the teacher can be assigned to teach. */
export async function createSubject(request, adminToken, subjectData) {
  const response = await request.post(`${API_ROOT}/subjects/create`, {
    data: subjectData,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${adminToken}` },
  });
  const payload = await response.json().catch(() => null);
  return { status: response.status(), payload };
}

/** Assigns a teacher to a classroom/subject pair for an academic year. */
export async function createTeacherAssignment(request, adminToken, assignmentData) {
  const response = await request.post(`${API_ROOT}/teacher-assignments`, {
    data: assignmentData,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${adminToken}` },
  });
  const payload = await response.json().catch(() => null);
  return { status: response.status(), payload };
}
