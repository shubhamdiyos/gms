import { expect, expectCleanConsole, test } from './console-guard.fixture.js';
import {
  API_ORIGIN,
  API_ROOT,
  login,
  postJson,
  provisionTenant,
  searchStudents,
} from './helpers/api.js';

/**
 * The core admin journey, in a browser, against the real backend.
 *
 * PRD J-01 is one sentence long — an administrator is issued an account, signs in, rotates the
 * password it was handed, and registers a pupil — and until this file every clause of it had only
 * ever been proven over HTTP. `scripts/verify-mvp-e2e.sh` asks the API whether `/students/create`
 * accepts a payload; it cannot know that the SPA bounces a signed-in administrator away from
 * `/admin/dashboard`, that the grid renders a row the user can see, or that the whole trip leaves
 * something in the devtools console. The three journey tests therefore end with
 * `expectCleanConsole()`, which is the mandate's "no browser-console errors on the core journey"
 * written as an assertion rather than as a hope.
 *
 * Three journeys and one refusal, four tenants across them, each provisioned through the API in the
 * first line: a journey that rotates a password spends the credential it started with, so sharing
 * one account between tests would make the second test depend on the first having run in exactly
 * the right order. `label` ('A', 'B', 'C', 'D') is what makes the school codes — and so the pupil
 * surnames — differ per tenant, which is what lets the third test search for another school's pupil
 * by name and mean it. The fourth test needs no tenant at all: it refuses before it authenticates.
 */

// The password the journeys set for themselves. Policy is the server's own (`PasswordValidator`:
// eight characters, upper, lower, digit, one of `@$!%*?&.#_`) and this satisfies it; it is a
// throwaway credential on a database that exists for one run, in the same class as the platform
// account the live-tenancy harness mints.
const ROTATED_PASSWORD = 'Browser_Journey_2026!';

async function signIn(page, { username, password }) {
  await page.goto('/login');
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign In', exact: true }).click();
}

async function rotatePassword(page, { issued, next }) {
  await page.getByLabel('Current Password').fill(issued);
  await page.getByLabel('New Password', { exact: true }).fill(next);
  await page.getByLabel('Confirm New Password').fill(next);
  await page.getByRole('button', { name: 'Set password' }).click();
  await expect(page.getByText('Password changed successfully')).toBeVisible();
}

/** Signs in with the credential the server issued, then performs the rotation the SPA demands. */
async function signInAndRotate(page, tenant) {
  await signIn(page, tenant.admin);
  await expect(page).toHaveURL(/\/change-password$/);
  await rotatePassword(page, { issued: tenant.admin.password, next: ROTATED_PASSWORD });
}

test('a provisioned administrator reaches its dashboard only after rotating the password it was issued', async ({
  request,
  page,
  consoleErrors,
}) => {
  const tenant = await provisionTenant(request, 'A', { keepIssuedPassword: true });

  await signIn(page, tenant.admin);

  // The screen the server's flag decides, not the one the user chose: `AuthController.login`
  // returns `requirePasswordChange`, `useAuth` routes on it, and `ProtectedRoute` holds every
  // other route until it has been honoured. Going there directly is the negative half of the same
  // claim — an account with an un-rotated password gets the rotation screen even when it asks for
  // a dashboard by URL.
  await expect(page.getByRole('heading', { level: 1, name: 'Change your password' })).toBeVisible();
  await page.goto('/admin/dashboard');
  await expect(page).toHaveURL(/\/change-password$/);

  await rotatePassword(page, { issued: tenant.admin.password, next: ROTATED_PASSWORD });

  await expect(page).toHaveURL(/\/admin\/dashboard$/);
  await expect(page.getByRole('heading', { name: 'Admin Dashboard' })).toBeVisible();

  // The flag is cleared where it was set: the SPA replaced `requirePasswordChange` in its own
  // stored user before navigating, so a reload stays on the dashboard instead of re-asking.
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Admin Dashboard' })).toBeVisible();
  await expect(page).toHaveURL(/\/admin\/dashboard$/);

  expectCleanConsole(consoleErrors);
});

test('the administrator registers a pupil through the dialog and the roll shows it', async ({
  request,
  page,
  consoleErrors,
}) => {
  // Provisioned with the fixture's rotation, so the classroom exists before the browser opens
  // the dashboard. It has to: `AdminDashboard` reads the classroom list on mount, and RTK Query
  // then serves that cached (empty) list to the Students page — creating the classroom after
  // the dashboard had loaded left the dialog's Classroom select with no options at all, which
  // is the timeout this test used to spend ten seconds on. The forced rotation is test one's
  // claim; this test is about the dialog.
  const tenant = await provisionTenant(request, 'B');
  expect(tenant.classroomId, 'the fixture must hand back the classroom it created').toBeTruthy();

  await signIn(page, tenant.admin);
  await expect(page).toHaveURL(/\/admin\/dashboard$/);
  await expect(page.getByRole('heading', { name: 'Admin Dashboard' })).toBeVisible();

  await page.getByRole('button', { name: 'Students', exact: true }).click();
  await expect(page).toHaveURL(/\/admin\/students$/);
  await expect(page.getByRole('heading', { level: 1, name: 'Students Management' })).toBeVisible();
  // A school created one minute ago has no pupils, and the grid says which kind of empty it is.
  await expect(page.getByText('No students yet')).toBeVisible();

  await page.getByRole('button', { name: 'Add Student' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText('Add New Student')).toBeVisible();

  await dialog.getByLabel('First Name').fill('Ishaan');
  await dialog.getByLabel('Last Name').fill(tenant.pupilSurname);
  await dialog.getByLabel('Email').fill(`ishaan.${tenant.schoolCode.toLowerCase()}@e2e.invalid`);
  await dialog.getByLabel('Phone Number').fill('9710001234');
  await dialog.getByLabel('Date of Birth').fill('2010-04-11');
  await dialog.getByRole('combobox', { name: 'Gender' }).click();
  // `exact` because 'Male' is a substring of 'Female', and MUI renders both as options.
  await page.getByRole('option', { name: 'Male', exact: true }).click();
  // The one classroom this tenant owns, created once the rotation above cleared the
  // password-change gate.
  await dialog.getByRole('combobox', { name: 'Classroom' }).click();
  await page.getByRole('option', { name: 'Grade 9 A' }).click();

  await dialog.getByRole('button', { name: 'Create' }).click();
  await expect(page.getByText('Student created successfully')).toBeVisible();

  // The row is on screen. `row` rather than a cell role because that is what the accessibility
  // snapshot of this very page measured (`row "STD002 Ishaan BPupil …"`); a `gridcell` locator
  // silently matched nothing here for ten seconds, and a locator that never matches is the one
  // kind of assertion that can pass while the feature is broken.
  await expect(page.getByRole('row', { name: tenant.pupilSurname })).toBeVisible();

  // Search is server-side in this page — `debouncedQuery` is what goes over the wire — so typing
  // the surname is a request, not a filter over what is already loaded.
  const search = page.getByPlaceholder('Search by name, student ID, or email');
  await search.fill(tenant.pupilSurname);
  await expect(page.getByRole('row', { name: tenant.pupilSurname })).toBeVisible();

  // Reconciled against the API the page does not control: same tenant, same surname, one result.
  // A row that exists only in the grid's local state would pass everything above and fail here.
  const audit = await searchStudents(request, tenant.adminToken, tenant.pupilSurname);
  expect(audit.status).toBe(200);
  expect(audit.payload?.totalElements).toBe(1);

  expectCleanConsole(consoleErrors);
});

test("a second school's administrator searches for the first school's pupil and finds nothing", async ({
  request,
  page,
  consoleErrors,
}) => {
  // School A's pupil, created over HTTP by A's own administrator: the registration path is proven
  // in the browser by the test above, and what is under test here is what a stranger can retrieve.
  const owner = await provisionTenant(request, 'C');
  const intruder = await provisionTenant(request, 'D', { keepIssuedPassword: true });
  expect(owner.classroomId, 'the fixture must hand back the classroom it created').toBeTruthy();

  const registered = await postJson(
    request,
    '/students/create',
    {
      firstName: 'Kiran',
      lastName: owner.pupilSurname,
      email: `kiran.${owner.schoolCode.toLowerCase()}@e2e.invalid`,
      phoneNumber: '9710009876',
      dateOfBirth: '2011-02-02',
      gender: 'FEMALE',
      // `classId` is `@NotNull` on `StudentRequest` and has to name a classroom belonging to the
      // school the token is from — a stranger's classroom answers 403, a nonexistent one 404, and
      // omitting it is the 400 this test's first run measured.
      classId: owner.classroomId,
      sectionId: null,
    },
    owner.adminToken,
  );
  expect(registered.status(), 'school C must be able to register its own pupil').toBe(200);

  // Now the same words, typed by school D's administrator into the same screen.
  await signInAndRotate(page, intruder);
  await page.getByRole('button', { name: 'Students', exact: true }).click();
  await page.getByPlaceholder('Search by name, student ID, or email').fill(owner.pupilSurname);

  // Isolation here is a filtered empty list, not a refusal: the tenant predicate is in the query,
  // so the server answers 200 with nothing in it and the page says so in its own words. That is
  // the user-visible half of what `verify-c4-owned-reads.sh` proves at the route layer.
  await expect(page.getByText('No students match this search')).toBeVisible();
  await expect(page.getByRole('row', { name: owner.pupilSurname })).toHaveCount(0);

  // A locator that matches nothing makes `toHaveCount(0)` pass whether the feature works or the
  // selector is wrong, so the same search string is run twice more over the API below — once as
  // the school that owns the pupil, once as the stranger. The owner's 1 is what turns the
  // intruder's 0 from a tautology into a measurement.
  const asIntruder = await login(request, {
    username: intruder.admin.username,
    password: ROTATED_PASSWORD,
  });
  const intruderToken = asIntruder.token;
  const ownRoll = await searchStudents(request, owner.adminToken, owner.pupilSurname);
  const crossTenant = await searchStudents(request, intruderToken, owner.pupilSurname);
  expect(ownRoll.status).toBe(200);
  expect(ownRoll.payload?.totalElements, 'school C owns the pupil it registered').toBe(1);
  expect(crossTenant.status).toBe(200);
  expect(crossTenant.payload?.totalElements, 'school D must not see school C\'s roll').toBe(0);

  expectCleanConsole(consoleErrors);
});

/**
 * The one request this suite makes that the proxy does not carry: a page calling an API on some
 * other origin.
 *
 * The journeys above reach the backend through `vite preview`'s proxy, which drops `Origin` so the
 * call reads as the same-origin call it is (see the comment in `vite.config.js`). That choice has a
 * cost — the proxy cannot also be the thing that proves the filter behind it refuses — so this test
 * goes around it. The page is served from the web port and posts to the absolute API origin, which
 * is a different origin, and the deployment's `cors.allowed-origins` names neither. The mandate's
 * rule for a security claim is a negative test, and this is the negative test for the CORS layer:
 * it needs no account, because the refusal happens before authentication.
 *
 * `expectCleanConsole()` is deliberately absent. The console line is the finding.
 */
test('the API refuses a browser whose origin the deployment has not allowed', async ({
  page,
  request,
}) => {
  await page.goto('/login');
  const pageOrigin = new URL(page.url()).origin;
  expect(pageOrigin, 'the page and the API must be different origins for this to be a CORS test').not.toBe(
    API_ORIGIN,
  );

  // A `text/plain` body makes this a CORS-"simple" request, so the browser sends it without asking
  // permission first: what arrives at the server is the real POST.
  const inPage = await page.evaluate(async (url) => {
    try {
      const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'text/plain;charset=UTF-8' },
        body: '{"username":"nobody","password":"nothing"}',
      });
      return { blocked: false, status: response.status };
    } catch (error) {
      // Chrome never tells the page what the server answered — that is the point — so all a script
      // on the refusing origin can observe is that the call failed. It is not evidence of *why*,
      // which is what the second half of this test is for.
      return { blocked: true, name: error.name, message: error.message };
    }
  }, `${API_ROOT}/auth/login`);

  expect(
    inPage,
    'a page on an origin the deployment did not list must not be able to read the answer',
  ).toEqual({ blocked: true, name: 'TypeError', message: 'Failed to fetch' });

  // The same call, sent from outside the browser with the page's `Origin` header on it, so the
  // refusal is attributable to the filter rather than to the network stack. `APIRequestContext` is
  // not bound by what a browser may claim, which is exactly the difference this needs.
  const refused = await request.post(`${API_ROOT}/auth/login`, {
    data: '{"username":"nobody","password":"nothing"}',
    headers: { 'Content-Type': 'text/plain;charset=UTF-8', Origin: pageOrigin },
  });
  expect(refused.status(), 'the CORS filter must refuse an origin the deployment did not list').toBe(
    403,
  );
  expect(
    refused.headers()['access-control-allow-origin'],
    'a refusal must not hand the stranger an allow-origin header',
  ).toBeFalsy();
  expect(await refused.text()).toContain('Invalid CORS request');
});
