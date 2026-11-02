import { expect, expectCleanConsole, test } from './console-guard.fixture.js';
import {
  API_ORIGIN,
  API_ROOT,
  provisionTenant,
  createStudentAccount,
  generatedUsername,
} from './helpers/api.js';

/**
 * The student self-service journey, in a browser, against the real backend.
 *
 * A student account is created by the administrator (`POST /students/create`), which
 * auto-provisions a user login with the generated username and the default password
 * `welcome123`. The account is flagged `requirePasswordChange`, so the first sign-in
 * lands on the rotation screen before the student reaches its dashboard.
 *
 * The student portal is read-only: profile, attendance, results, fees, timetable. The
 * backend enforces self-service through token-based endpoints (`/students/my-*`) that
 * resolve the caller from the JWT, not from a URL parameter. The negative test therefore
 * exercises the SPA's role-based routing — a student that navigates to `/admin/dashboard`
 * or `/parent/dashboard` is bounced by `ProtectedRoute` to the unauthorized page.
 *
 * Two tenants per test: the student's own (G) and a second one (H) for the cross-tenant
 * API refusal. The cross-tenant call goes directly to the absolute API origin, bypassing
 * the `vite preview` proxy, because the proxy strips the `Origin` header and the refusal
 * happens at the CORS layer before authentication.
 */

// The password the journeys set for themselves. Policy is the server's own (`PasswordValidator`:
// eight characters, upper, lower, digit, one of `@$!%*?&.#_`) and this satisfies it.
const ROTATED_PASSWORD = 'Stu_Journey_2026!';

// The default password the server mints for every student account.
const DEFAULT_PASSWORD = 'welcome123';

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

async function signInAndRotate(page, username) {
  await signIn(page, { username, password: DEFAULT_PASSWORD });
  await expect(page).toHaveURL(/\/change-password$/);
  await rotatePassword(page, { issued: DEFAULT_PASSWORD, next: ROTATED_PASSWORD });
}

test('a provisioned student reaches its dashboard and views profile and attendance', async ({
  request,
  page,
  consoleErrors,
}) => {
  const tenant = await provisionTenant(request, 'G');

  // Create a student in the tenant's classroom. The server auto-provisions a user account
  // with username = generated(firstName, lastName) and password = "welcome123".
  const studentFirstName = 'Ishaan';
  const studentLastName = tenant.pupilSurname;
  const student = await createStudentAccount(request, tenant.adminToken, {
    firstName: studentFirstName,
    lastName: studentLastName,
    email: `${studentFirstName.toLowerCase()}.${tenant.schoolCode.toLowerCase()}@e2e.invalid`,
    phoneNumber: '9710004001',
    dateOfBirth: '2010-06-20',
    gender: 'MALE',
    classId: tenant.classroomId,
    sectionId: null,
  });
  expect(student.status, `student creation must succeed (answered ${student.status})`).toBe(200);

  // The username the server generated for this student. The derivation is the same rule as
  // `initialPasswordFor` in the admin journey: first initial + last name, lowercased.
  const studentUsername = generatedUsername(studentFirstName, studentLastName);

  // Sign in and rotate the password. The SPA forces the rotation before any dashboard.
  await signInAndRotate(page, studentUsername);

  // The student dashboard loads after rotation.
  await expect(page).toHaveURL(/\/student\/dashboard$/);
  await expect(page.getByRole('heading', { name: 'Student Dashboard' })).toBeVisible();

  // Navigate to the profile page via the sidebar.
  await page.getByRole('listitem').filter({ hasText: 'Profile' }).click();
  await expect(page).toHaveURL(/\/student\/profile$/);
  await expect(page.getByRole('heading', { name: 'My Profile' })).toBeVisible();

  // The profile page shows the student's name and ID.
  await expect(page.getByText(studentFirstName)).toBeVisible();
  await expect(page.getByText(studentLastName)).toBeVisible();

  // Navigate to the attendance page via the sidebar.
  await page.getByRole('listitem').filter({ hasText: 'Attendance' }).click();
  await expect(page).toHaveURL(/\/student\/attendance$/);
  await expect(page.getByRole('heading', { name: 'My Attendance' })).toBeVisible();

  // The attendance page shows the statistics cards and the month selector.
  await expect(page.getByText('Total Days')).toBeVisible();
  await expect(page.getByText('Present Days')).toBeVisible();
  await expect(page.getByText('Absent Days')).toBeVisible();

  // A newly created student has no attendance records, so the page shows the empty state.
  await expect(page.getByText('No Attendance Records')).toBeVisible();

  expectCleanConsole(consoleErrors);
});

test('a student cannot reach the admin or parent dashboards via direct URL', async ({
  request,
  page,
  consoleErrors,
}) => {
  const tenant = await provisionTenant(request, 'H');

  // Create and sign in as a student.
  const studentFirstName = 'Riya';
  const studentLastName = tenant.pupilSurname;
  const student = await createStudentAccount(request, tenant.adminToken, {
    firstName: studentFirstName,
    lastName: studentLastName,
    email: `${studentFirstName.toLowerCase()}.${tenant.schoolCode.toLowerCase()}@e2e.invalid`,
    phoneNumber: '9710004002',
    dateOfBirth: '2010-07-25',
    gender: 'FEMALE',
    classId: tenant.classroomId,
    sectionId: null,
  });
  expect(student.status).toBe(200);
  const studentUsername = generatedUsername(studentFirstName, studentLastName);
  await signInAndRotate(page, studentUsername);
  await expect(page).toHaveURL(/\/student\/dashboard$/);

  // Attempt to navigate to the admin dashboard by direct URL. The `ProtectedRoute` component
  // checks the user's role against the route's required roles and redirects to /unauthorized
  // when they do not match.
  await page.goto('/admin/dashboard');
  await expect(page).toHaveURL(/\/unauthorized/);

  // Return to the student dashboard and attempt the parent route.
  await page.goto('/student/dashboard');
  await expect(page).toHaveURL(/\/student\/dashboard$/);

  await page.goto('/parent/dashboard');
  await expect(page).toHaveURL(/\/unauthorized/);

  // The student's own dashboard is still reachable after the refusals.
  await page.goto('/student/dashboard');
  await expect(page).toHaveURL(/\/student\/dashboard$/);
  await expect(page.getByRole('heading', { name: 'Student Dashboard' })).toBeVisible();

  expectCleanConsole(consoleErrors);
});

test('a student API call to another tenant is refused at the CORS layer', async ({
  request,
  page,
}) => {
  // This test does not use `consoleErrors` because the CORS refusal is the finding — a
  // console error is expected and is the evidence, not a defect.
  const tenant = await provisionTenant(request, 'I');

  // Create and sign in as a student to get a valid session token.
  const studentFirstName = 'Dev';
  const studentLastName = tenant.pupilSurname;
  const student = await createStudentAccount(request, tenant.adminToken, {
    firstName: studentFirstName,
    lastName: studentLastName,
    email: `${studentFirstName.toLowerCase()}.${tenant.schoolCode.toLowerCase()}@e2e.invalid`,
    phoneNumber: '9710004003',
    dateOfBirth: '2010-08-30',
    gender: 'MALE',
    classId: tenant.classroomId,
    sectionId: null,
  });
  expect(student.status).toBe(200);
  const studentUsername = generatedUsername(studentFirstName, studentLastName);
  await signIn(page, { username: studentUsername, password: DEFAULT_PASSWORD });
  await expect(page).toHaveURL(/\/change-password$/);
  await rotatePassword(page, { issued: DEFAULT_PASSWORD, next: ROTATED_PASSWORD });
  await expect(page).toHaveURL(/\/student\/dashboard$/);

  // Read the session token the SPA stored after login.
  const token = await page.evaluate(() => localStorage.getItem('gms_token'));
  expect(token, 'the session token must be present after login').toBeTruthy();

  // Attempt a cross-origin API call from the page. The `vite preview` proxy strips the
  // `Origin` header, so a same-origin call through the proxy would succeed. This test goes
  // around the proxy by posting to the absolute API origin, which is a different origin
  // from the page.
  const pageOrigin = new URL(page.url()).origin;
  expect(pageOrigin, 'the page and the API must be different origins for this to be a CORS test').not.toBe(
    API_ORIGIN,
  );

  // A `text/plain` body makes this a CORS-"simple" request, so the browser sends it without
  // asking permission first. The page can only observe that the call failed — Chrome never
  // reveals the server's answer to a refused cross-origin request.
  const inPage = await page.evaluate(async (url) => {
    try {
      const response = await fetch(url, {
        method: 'GET',
        headers: { 'Content-Type': 'text/plain;charset=UTF-8' },
      });
      return { blocked: false, status: response.status };
    } catch (error) {
      return { blocked: true, name: error.name, message: error.message };
    }
  }, `${API_ROOT}/students/profile`);

  expect(
    inPage,
    'a cross-origin call from the page must be blocked by the CORS filter',
  ).toEqual({ blocked: true, name: 'TypeError', message: 'Failed to fetch' });
});
