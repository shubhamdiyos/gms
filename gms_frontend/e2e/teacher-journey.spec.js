import { expect, expectCleanConsole, test } from './console-guard.fixture.js';
import {
  provisionTenant,
  claimOf,
  createTeacherAccount,
  createSubject,
  createTeacherAssignment,
  createStudentAccount,
} from './helpers/api.js';

/**
 * The teacher journey, in a browser, against the real backend.
 *
 * A teacher is not provisioned by `provisionTenant` — that fixture mints a school and its
 * administrator — so this file builds the teacher on top of the tenant the admin owns: an
 * employee row first (`POST /employees/create`), then a login for it (`POST /auth/self-register`).
 * The self-register endpoint hard-codes the role to TEACHER and does not set
 * `requirePasswordChange`, so the browser journey skips the rotation screen and goes straight
 * to the dashboard.
 *
 * The attendance page is the one feature the teacher owns. It opens on the Daily Register:
 * one mark per student for the whole day, no timetable slot required, everyone defaulted to
 * PRESENT. The journey proves the full loop: the class dropdown populates from the teacher's
 * assignments, selecting a class lists its students, the register saves, and the saved state
 * comes back. The console guard at the end is the mandate's "no browser-console errors on
 * the core journey" written as an assertion.
 *
 * One tenant per test, each labelled E/F/G/H to keep school codes and surnames distinct. The
 * teacher is assigned to the tenant's own classroom; the pupil registered for the assignment
 * to resolve is created in that same classroom, so the attendance roster is not empty.
 */

// The password the teacher self-registers with. Policy is the server's own (`PasswordValidator`:
// eight characters, upper, lower, digit, one of `@$!%*?&.#_`) and this satisfies it.
const TEACHER_PASSWORD = 'Tchr_E2E_2026!';

async function signIn(page, { username, password }) {
  await page.goto('/login');
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign In', exact: true }).click();
}

test('a provisioned teacher reaches its dashboard and loads the attendance page', async ({
  request,
  page,
  consoleErrors,
}) => {
  const tenant = await provisionTenant(request, 'E');
  const schoolId = claimOf(tenant.adminToken, 'schoolId');
  expect(schoolId, 'the admin token must carry the school the tenant just created').toBeTruthy();

  // Create a teacher employee and register a login for it. The username is chosen explicitly
  // rather than derived, because the self-register endpoint takes it as input — unlike the
  // student and parent paths, where the server generates it from the name.
  const teacherUsername = `tchr${tenant.schoolCode.toLowerCase()}`;
  const teacher = await createTeacherAccount(
    request,
    tenant.adminToken,
    {
      firstName: 'Test',
      lastName: 'Teacher',
      email: `teacher.${tenant.schoolCode.toLowerCase()}@e2e.invalid`,
      phoneNumber: '9710002001',
      designation: 'Mathematics Teacher',
      department: 'Mathematics',
      teaching: true,
    },
    schoolId,
    teacherUsername,
    TEACHER_PASSWORD,
  );
  expect(teacher.status, `teacher self-register must succeed (answered ${teacher.status})`).toBe(200);
  expect(teacher.empId, 'the employee row must be created before the login is registered').toBeTruthy();

  // Create a subject the teacher can be assigned to teach. The subject code must be unique
  // within the school; the tenant label in it keeps runs from colliding.
  const subject = await createSubject(request, tenant.adminToken, {
    name: `Mathematics ${tenant.schoolCode}`,
    code: `MATH${tenant.schoolCode}`,
  });
  expect(subject.status, `subject creation must succeed (answered ${subject.status})`).toBe(200);
  expect(subject.payload?.id, 'the subject must have an id for the assignment').toBeTruthy();

  // Assign the teacher to the tenant's classroom. The assignment is what populates the
  // "Select Class" dropdown on the attendance page — without it, the teacher sees an empty
  // dropdown and "No classrooms assigned" on the dashboard.
  const assignment = await createTeacherAssignment(request, tenant.adminToken, {
    teacherId: teacher.empId,
    subjectId: subject.payload.id,
    classroomId: tenant.classroomId,
    academicYear: '2025-2026',
  });
  expect(assignment.status, `teacher assignment must succeed (answered ${assignment.status})`).toBe(201);

  // Register a pupil in the teacher's classroom, so the attendance roster is not empty.
  const student = await createStudentAccount(request, tenant.adminToken, {
    firstName: 'Aarav',
    lastName: tenant.pupilSurname,
    email: `aarav.${tenant.schoolCode.toLowerCase()}@e2e.invalid`,
    phoneNumber: '9710003001',
    dateOfBirth: '2010-05-15',
    gender: 'MALE',
    classId: tenant.classroomId,
    sectionId: null,
  });
  expect(student.status, `student creation must succeed (answered ${student.status})`).toBe(200);

  // Sign in as the teacher. The self-register path does not set `requirePasswordChange`, so
  // the SPA goes straight to the teacher dashboard without the rotation screen.
  await signIn(page, { username: teacherUsername, password: TEACHER_PASSWORD });
  await expect(page).toHaveURL(/\/teacher\/dashboard$/);
  await expect(page.getByRole('heading', { name: 'Teacher Dashboard' })).toBeVisible();

  // The dashboard shows the teacher's name and the assigned classroom. The class count card
  // reads from `useGetTeacherClassesQuery`, which resolves to the assignment created above.
  // Scoped to the heading: the same words are also the sidebar item's label and the card's
  // overline, and a bare text locator resolves to several elements.
  await expect(page.getByRole('heading', { name: 'My Classes' })).toBeVisible();

  // Navigate to the attendance page via the sidebar.
  await page.getByRole('listitem').filter({ hasText: 'Attendance' }).click();
  await expect(page).toHaveURL(/\/teacher\/attendance$/);
  // `exact` because the empty state is an h6 that reads "Select a Class to Mark Attendance";
  // a substring match resolves to both headings while the class list is still loading.
  await expect(page.getByRole('heading', { name: 'Mark Attendance', exact: true })).toBeVisible();

  // The class dropdown is populated from `useGetTeacherClassesQuery`. Selecting a class
  // triggers `useGetTeacherStudentsQuery` filtered by that class, which populates the roster.
  const classSelect = page.getByLabel('Select Class');
  await classSelect.click();
  // The classroom created by `provisionTenant` is "Grade 9 A"; the dropdown item renders as
  // "Grade 9 A - 9 A" (name, grade, section).
  await page.getByRole('option').first().click();

  // The student row is on screen. The roster renders each student's name in a table cell.
  await expect(page.getByText(tenant.pupilSurname)).toBeVisible();

  // The Daily Register defaults every student to PRESENT, so with a pupil on the roster the
  // register has something to save the moment the class is chosen — no timetable period is
  // involved. Saving writes the daily record; the roster then shows it as saved and the Save
  // button settles back to disabled because nothing is left pending.
  const saveButton = page.getByRole('button', { name: 'Save Attendance' });
  await expect(saveButton).toBeVisible();
  await expect(saveButton).toBeEnabled();
  await saveButton.click();
  await expect(page.getByText(/attendance records? saved\./)).toBeVisible();
  await expect(page.getByText('PRESENT (saved)')).toBeVisible();
  await expect(saveButton).toBeDisabled();

  expectCleanConsole(consoleErrors);
});

test('a teacher without class assignments sees the empty state on the attendance page', async ({
  request,
  page,
  consoleErrors,
}) => {
  const tenant = await provisionTenant(request, 'F');
  const schoolId = claimOf(tenant.adminToken, 'schoolId');

  // Create a teacher without any class assignments. The attendance page should still load,
  // but the class dropdown will be empty and the page will show "Select a Class to Mark
  // Attendance" instead of a student roster.
  const teacherUsername = `tchr${tenant.schoolCode.toLowerCase()}`;
  const teacher = await createTeacherAccount(
    request,
    tenant.adminToken,
    {
      firstName: 'Empty',
      lastName: 'Teacher',
      email: `empty.${tenant.schoolCode.toLowerCase()}@e2e.invalid`,
      phoneNumber: '9710002002',
      designation: 'Unassigned Teacher',
      department: 'General',
      teaching: true,
    },
    schoolId,
    teacherUsername,
    TEACHER_PASSWORD,
  );
  expect(teacher.status).toBe(200);

  await signIn(page, { username: teacherUsername, password: TEACHER_PASSWORD });
  await expect(page).toHaveURL(/\/teacher\/dashboard$/);

  // The dashboard shows "No classrooms assigned" when the teacher has no assignments.
  await expect(page.getByText('No classrooms assigned')).toBeVisible();

  // Navigate to the attendance page.
  await page.getByRole('listitem').filter({ hasText: 'Attendance' }).click();
  await expect(page).toHaveURL(/\/teacher\/attendance$/);

  // The page shows the empty-state prompt instead of a roster.
  await expect(page.getByText('Select a Class to Mark Attendance')).toBeVisible();

  expectCleanConsole(consoleErrors);
});
