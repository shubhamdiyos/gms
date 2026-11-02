import { expect, expectCleanConsole, test } from './console-guard.fixture.js';
import {
  API_ROOT,
  provisionTenant,
  createStudentAccount,
  createParentAccount,
  generatedUsername,
} from './helpers/api.js';

/**
 * The parent journey, in a browser, against the real backend.
 *
 * A parent account is created by the administrator (`POST /parents/create`), which links
 * the parent to one or more students and auto-provisions a user login. The username is
 * generated from the parent's name, the password is the default `welcome123`, and the
 * account is flagged `requirePasswordChange`. The browser journey clicks through the
 * rotation screen before the parent reaches its dashboard.
 *
 * The parent portal is read-only: children list, attendance, results, fees. The backend
 * enforces parent-child validation on every scoped endpoint (F-3 fix): a parent can read
 * only the data of students linked to it through the `parent_student` join table. The
 * negative test creates two students with two separate parents in the same school, then
 * calls the API directly as Parent A targeting Student B — the backend answers 403 because
 * the parent-child link does not exist, even though both are in the same tenant.
 *
 * Two tenants per test: the parent's own (J) and a second one (K) for the cross-tenant
 * API refusal at the CORS layer. The cross-tenant call goes directly to the absolute API
 * origin, bypassing the `vite preview` proxy.
 */

// The password the journeys set for themselves. Policy is the server's own (`PasswordValidator`:
// eight characters, upper, lower, digit, one of `@$!%*?&.#_`) and this satisfies it.
const ROTATED_PASSWORD = 'Par_Journey_2026!';

// The default password the server mints for every parent account.
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

test('a provisioned parent reaches its dashboard and views child information', async ({
  request,
  page,
  consoleErrors,
}) => {
  const tenant = await provisionTenant(request, 'J');

  // Create a student in the tenant's classroom. The parent will be linked to this student.
  const studentFirstName = 'Anaya';
  const studentLastName = tenant.pupilSurname;
  const student = await createStudentAccount(request, tenant.adminToken, {
    firstName: studentFirstName,
    lastName: studentLastName,
    email: `${studentFirstName.toLowerCase()}.${tenant.schoolCode.toLowerCase()}@e2e.invalid`,
    phoneNumber: '9710005001',
    dateOfBirth: '2010-09-10',
    gender: 'FEMALE',
    classId: tenant.classroomId,
    sectionId: null,
  });
  expect(student.status, `student creation must succeed (answered ${student.status})`).toBe(200);
  expect(student.payload?.id, 'the student must have an id for the parent link').toBeTruthy();

  // Create a parent linked to the student. The server auto-provisions a user account with
  // username = generated(firstName, lastName) and password = "welcome123".
  const parentFirstName = 'Sunita';
  const parentLastName = 'JParent';
  const parent = await createParentAccount(request, tenant.adminToken, {
    firstName: parentFirstName,
    lastName: parentLastName,
    email: `${parentFirstName.toLowerCase()}.${tenant.schoolCode.toLowerCase()}@e2e.invalid`,
    phoneNumber: '9710005002',
    studentIds: [student.payload.id],
  });
  expect(parent.status, `parent creation must succeed (answered ${parent.status})`).toBe(200);

  // The username the server generated for this parent.
  const parentUsername = generatedUsername(parentFirstName, parentLastName);

  // Sign in and rotate the password. The SPA forces the rotation before any dashboard.
  await signInAndRotate(page, parentUsername);

  // The parent dashboard loads after rotation.
  await expect(page).toHaveURL(/\/parent\/dashboard$/);
  await expect(page.getByRole('heading', { name: 'Parent Dashboard' })).toBeVisible();

  // The dashboard shows the parent's name and the child's information.
  await expect(page.getByText('My Children')).toBeVisible();
  await expect(page.getByText(studentFirstName)).toBeVisible();
  await expect(page.getByText(studentLastName)).toBeVisible();

  // Navigate to the children page via the sidebar.
  await page.getByRole('listitem').filter({ hasText: 'Children' }).click();
  await expect(page).toHaveURL(/\/parent\/children$/);
  await expect(page.getByRole('heading', { name: 'My Children' })).toBeVisible();

  // The children page shows the child's card with personal information.
  await expect(page.getByText(studentFirstName)).toBeVisible();

  // The page has tabs for Overview, Attendance, Grades, and Fees. The Overview tab is
  // active by default and shows the child's personal information and quick stats.
  await expect(page.getByRole('tab', { name: 'Overview' })).toBeVisible();
  await expect(page.getByRole('tab', { name: 'Attendance' })).toBeVisible();
  await expect(page.getByRole('tab', { name: 'Grades' })).toBeVisible();
  await expect(page.getByRole('tab', { name: 'Fees' })).toBeVisible();

  // Click the Attendance tab to verify it loads.
  await page.getByRole('tab', { name: 'Attendance' }).click();
  // The attendance tab shows statistics cards. A newly created student has no records, so
  // the page shows "Marked Records" with 0.
  await expect(page.getByText('Marked Records')).toBeVisible();

  // Click the Fees tab to verify it loads.
  await page.getByRole('tab', { name: 'Fees' }).click();
  await expect(page.getByText('Fee Records')).toBeVisible();

  expectCleanConsole(consoleErrors);
});

test('a parent API call for another student in the same school is refused with 403', async ({
  request,
  page,
  consoleErrors,
}) => {
  const tenant = await provisionTenant(request, 'K');

  // Create two students in the same classroom.
  const studentA = await createStudentAccount(request, tenant.adminToken, {
    firstName: 'Vivaan',
    lastName: tenant.pupilSurname,
    email: `vivaan.${tenant.schoolCode.toLowerCase()}@e2e.invalid`,
    phoneNumber: '9710006001',
    dateOfBirth: '2010-10-10',
    gender: 'MALE',
    classId: tenant.classroomId,
    sectionId: null,
  });
  expect(studentA.status).toBe(200);

  const studentB = await createStudentAccount(request, tenant.adminToken, {
    firstName: 'Kiara',
    lastName: `KOther${tenant.schoolCode}`,
    email: `kiara.${tenant.schoolCode.toLowerCase()}@e2e.invalid`,
    phoneNumber: '9710006002',
    dateOfBirth: '2010-11-11',
    gender: 'FEMALE',
    classId: tenant.classroomId,
    sectionId: null,
  });
  expect(studentB.status).toBe(200);

  // Create Parent A linked to Student A only.
  const parentAFirstName = 'Meera';
  const parentALastName = 'KParentA';
  const parentA = await createParentAccount(request, tenant.adminToken, {
    firstName: parentAFirstName,
    lastName: parentALastName,
    email: `${parentAFirstName.toLowerCase()}.${tenant.schoolCode.toLowerCase()}@e2e.invalid`,
    phoneNumber: '9710006003',
    studentIds: [studentA.payload.id],
  });
  expect(parentA.status).toBe(200);

  // Sign in as Parent A.
  const parentAUsername = generatedUsername(parentAFirstName, parentALastName);
  await signInAndRotate(page, parentAUsername);
  await expect(page).toHaveURL(/\/parent\/dashboard$/);

  // Read the session token the SPA stored after login.
  const token = await page.evaluate(() => localStorage.getItem('gms_token'));
  expect(token, 'the session token must be present after login').toBeTruthy();

  // Attempt to access Student B's attendance via the API. The backend enforces parent-child
  // validation (F-3 fix): Parent A is not linked to Student B, so the call must be refused
  // with 403 even though both are in the same school.
  const asParentA = await request.get(
    `${API_ROOT}/parents/children/${studentB.payload.id}/attendance`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  expect(
    asParentA.status(),
    'Parent A must not be able to read Student B\'s attendance (F-3 parent-child validation)',
  ).toBe(403);

  // The same refusal applies to results and fees endpoints.
  const asParentAResults = await request.get(
    `${API_ROOT}/parents/children/${studentB.payload.id}/results`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  expect(asParentAResults.status(), 'Parent A must not read Student B\'s results').toBe(403);

  const asParentAFees = await request.get(
    `${API_ROOT}/parents/children/${studentB.payload.id}/fees`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  expect(asParentAFees.status(), 'Parent A must not read Student B\'s fees').toBe(403);

  // Parent A can still access Student A's own data (positive control).
  const asParentAOwn = await request.get(
    `${API_ROOT}/parents/children/${studentA.payload.id}/attendance`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  expect(
    asParentAOwn.status(),
    'Parent A must be able to read Student A\'s attendance (own child)',
  ).toBe(200);

  // The parent dashboard is still reachable after the refusals.
  await page.goto('/parent/dashboard');
  await expect(page).toHaveURL(/\/parent\/dashboard$/);
  await expect(page.getByRole('heading', { name: 'Parent Dashboard' })).toBeVisible();

  expectCleanConsole(consoleErrors);
});

test('a parent cannot reach the admin or student dashboards via direct URL', async ({
  request,
  page,
  consoleErrors,
}) => {
  const tenant = await provisionTenant(request, 'L');

  // Create a student and a parent linked to it.
  const student = await createStudentAccount(request, tenant.adminToken, {
    firstName: 'Arjun',
    lastName: tenant.pupilSurname,
    email: `arjun.${tenant.schoolCode.toLowerCase()}@e2e.invalid`,
    phoneNumber: '9710007001',
    dateOfBirth: '2010-12-12',
    gender: 'MALE',
    classId: tenant.classroomId,
    sectionId: null,
  });
  expect(student.status).toBe(200);

  const parentFirstName = 'Priya';
  const parentLastName = 'LParent';
  const parent = await createParentAccount(request, tenant.adminToken, {
    firstName: parentFirstName,
    lastName: parentLastName,
    email: `${parentFirstName.toLowerCase()}.${tenant.schoolCode.toLowerCase()}@e2e.invalid`,
    phoneNumber: '9710007002',
    studentIds: [student.payload.id],
  });
  expect(parent.status).toBe(200);

  const parentUsername = generatedUsername(parentFirstName, parentLastName);
  await signInAndRotate(page, parentUsername);
  await expect(page).toHaveURL(/\/parent\/dashboard$/);

  // Attempt to navigate to the admin dashboard by direct URL.
  await page.goto('/admin/dashboard');
  await expect(page).toHaveURL(/\/unauthorized/);

  // Attempt to navigate to the student dashboard by direct URL.
  await page.goto('/student/dashboard');
  await expect(page).toHaveURL(/\/unauthorized/);

  // The parent's own dashboard is still reachable after the refusals.
  await page.goto('/parent/dashboard');
  await expect(page).toHaveURL(/\/parent\/dashboard$/);
  await expect(page.getByRole('heading', { name: 'Parent Dashboard' })).toBeVisible();

  expectCleanConsole(consoleErrors);
});
