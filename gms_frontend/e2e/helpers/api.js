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
 * Nothing here is a secret. `GMS_E2E_PLATFORM_USERNAME` and `GMS_E2E_PLATFORM_PASSWORD` are the
 * same two names the live-tenancy harness already reads, for a platform account that
 * `SuperAdminBootstrapService` mints into a database which exists for one run; and the
 * administrator password this file returns is derived, not invented — see
 * `initialPasswordFor`, which is the server's own rule restated rather than a new one.
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
