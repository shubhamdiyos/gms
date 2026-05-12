/**
 * Mock Base Query
 *
 * Intercepts every RTK Query call when VITE_MOCK_AUTH=true and returns
 * the appropriate mock data. Pattern match on method + URL path.
 *
 * To disable: set VITE_MOCK_AUTH=false (or remove it) in .env — no other changes needed.
 */

import * as M from './mockData';

const delay = (ms = 250) => new Promise((r) => setTimeout(r, ms));

// In-memory mutable state so mutations (create/update/delete) feel real during the session
let _schools      = [...M.schools];
let _students     = [...M.students];
let _employees    = [...M.employees];
let _classrooms   = [...M.classrooms];
let _subjects     = [...M.subjects];
let _academicYears = [...M.academicYears];
let _fees         = [...M.fees];
let _exams        = [...M.exams];
let _results      = [...M.results];
let _notifications = [...M.notifications];
let _announcements = [...M.announcements];
let _sections     = [...M.sections];
let nextId        = 100; // id counter for new records

const ok   = (data)  => ({ data });
const err  = (msg, status = 400) => ({ error: { status, data: { message: msg } } });
// Strip query-string before extracting the trailing numeric id segment
const idOf = (url)   => parseInt(url.split('?')[0].split('/').filter(Boolean).pop(), 10);

export const mockBaseQuery = () => async (args) => {
  await delay();
  // RTK Query passes args as a plain string when query() returns a string,
  // or as an object { url, method, body, params } when it returns an object.
  const url    = typeof args === 'string' ? args : (args?.url ?? '');
  const method = typeof args === 'string' ? 'GET' : (args?.method ?? 'GET');
  const body   = typeof args === 'string' ? undefined : args?.body;

  const m = method.toUpperCase();
  const u = url.replace(/^\//, ''); // strip leading slash

  // ── AUTH ────────────────────────────────────────────────────────────────────
  if (u === 'auth/login' && m === 'POST')
    return ok({ accessToken: 'mock-token', expiresIn: 86400, requirePasswordChange: false });

  if (u === 'auth/change-password' && m === 'POST')
    return ok({});

  // ── SCHOOLS ─────────────────────────────────────────────────────────────────
  if (u === 'schools' && m === 'GET')
    return ok(_schools);

  if (u === 'schools/create' && m === 'POST') {
    const rec = { id: ++nextId, isActive: true, ...body };
    _schools.push(rec);
    return ok(rec);
  }

  if (u === 'schools/update' && m === 'PUT') {
    _schools = _schools.map(s => s.id === body.id ? { ...s, ...body } : s);
    return ok(body);
  }

  if (u.startsWith('schools/toggle') && m === 'PATCH') {
    const params = new URLSearchParams(u.split('?')[1]);
    const id = parseInt(params.get('id'), 10);
    const isActive = params.get('isActive') === 'true';
    _schools = _schools.map(s => s.id === id ? { ...s, isActive } : s);
    return ok({});
  }

  if (u.startsWith('schools/') && m === 'GET')
    return ok(_schools.find(s => s.id === idOf(u)) || err('Not found', 404));

  // ── ACADEMIC YEARS ──────────────────────────────────────────────────────────
  if (u === 'academic-years' && m === 'GET')  return ok(_academicYears);
  if (u === 'academic-years' && m === 'POST') {
    const rec = { id: ++nextId, ...body };
    _academicYears.push(rec);
    return ok(rec);
  }
  if (u.startsWith('academic-years/') && m === 'PUT') {
    _academicYears = _academicYears.map(a => a.id === idOf(u) ? { ...a, ...body } : a);
    return ok(body);
  }
  if (u.startsWith('academic-years/') && m === 'DELETE') {
    _academicYears = _academicYears.filter(a => a.id !== idOf(u));
    return ok({});
  }

  // ── CLASSROOMS ──────────────────────────────────────────────────────────────
  if (u === 'classrooms' && m === 'GET')  return ok(_classrooms);
  if (u === 'classrooms/create' && m === 'POST') {
    const rec = { id: ++nextId, ...body };
    _classrooms.push(rec);
    return ok(rec);
  }
  if (u.startsWith('classrooms/') && m === 'PUT') {
    _classrooms = _classrooms.map(c => c.id === idOf(u) ? { ...c, ...body } : c);
    return ok(body);
  }
  if (u.startsWith('classrooms/') && m === 'DELETE') {
    _classrooms = _classrooms.filter(c => c.id !== idOf(u));
    return ok({});
  }

  // ── SECTIONS ────────────────────────────────────────────────────────────────
  if (u === 'sections' && m === 'GET')  return ok(_sections);
  if (u === 'sections/create' && m === 'POST') {
    const rec = { id: ++nextId, ...body };
    _sections.push(rec);
    return ok(rec);
  }

  // ── SUBJECTS ────────────────────────────────────────────────────────────────
  if (u === 'subjects' && m === 'GET')  return ok(_subjects);
  if (u === 'subjects/create' && m === 'POST') {
    const rec = { id: ++nextId, ...body };
    _subjects.push(rec);
    return ok(rec);
  }
  if (u.startsWith('subjects/update/') && m === 'PUT') {
    _subjects = _subjects.map(s => s.id === idOf(u) ? { ...s, ...body } : s);
    return ok(body);
  }
  if (u.startsWith('subjects/') && m === 'DELETE') {
    _subjects = _subjects.filter(s => s.id !== idOf(u));
    return ok({});
  }

  // ── EMPLOYEES ───────────────────────────────────────────────────────────────
  if (u === 'employees' && m === 'GET')  return ok(_employees);
  if (u === 'employees/create' && m === 'POST') {
    const rec = { id: ++nextId, isActive: true, ...body };
    _employees.push(rec);
    return ok(rec);
  }
  if (u === 'employees/update' && m === 'PUT') {
    _employees = _employees.map(e => e.id === body.id ? { ...e, ...body } : e);
    return ok(body);
  }
  if (u.startsWith('employees/toggle') && (m === 'DELETE' || m === 'PATCH')) {
    const params = new URLSearchParams(u.split('?')[1]);
    const id = parseInt(params.get('id'), 10);
    const isActive = params.get('isActive') === 'true';
    _employees = _employees.map(e => e.id === id ? { ...e, isActive } : e);
    return ok({});
  }

  // ── STUDENTS (admin list) ───────────────────────────────────────────────────
  if (u === 'students' && m === 'GET')  return ok(_students);
  if (u === 'students/create' && m === 'POST') {
    const rec = { id: ++nextId, isActive: true, ...body };
    _students.push(rec);
    return ok(rec);
  }
  if (u === 'students/update' && m === 'PUT') {
    _students = _students.map(s => s.id === body.id ? { ...s, ...body } : s);
    return ok(body);
  }
  if (u.startsWith('students/toggle') && m === 'PATCH') {
    const params = new URLSearchParams(u.split('?')[1]);
    const id = parseInt(params.get('id'), 10);
    const isActive = params.get('isActive') === 'true';
    _students = _students.map(s => s.id === id ? { ...s, isActive } : s);
    return ok({});
  }

  // ── STUDENT ROLE ENDPOINTS ──────────────────────────────────────────────────
  if (u === 'students/profile'      && m === 'GET') return ok(M.studentProfile);
  if (u === 'students/my-classes'   && m === 'GET') return ok(M.myClasses);
  if (u === 'students/my-subjects'  && m === 'GET') return ok(M.mySubjects);
  if (u === 'students/my-attendance'&& m === 'GET') return ok(M.myAttendance);
  if (u === 'students/my-results'   && m === 'GET') return ok(M.myResults);
  if (u === 'students/my-fees'      && m === 'GET') return ok(M.myFees);
  if (u === 'students/my-timetable' && m === 'GET') return ok(M.myTimetable);

  // ── TEACHER ROLE ENDPOINTS ──────────────────────────────────────────────────
  if (u === 'teachers/profile'  && m === 'GET') return ok(M.teacherProfile);
  if (u === 'teachers/classes'  && m === 'GET') return ok(M.teacherClasses);
  if (u === 'teachers/students' && m === 'GET') return ok(M.teacherStudents);

  // ── PARENT ENDPOINTS ────────────────────────────────────────────────────────
  if (u === 'parents/children' && m === 'GET') return ok(M.parentChildren);
  if (u.startsWith('parents/children/') && u.endsWith('/attendance') && m === 'GET') return ok(M.childAttendance);
  if (u.startsWith('parents/children/') && u.endsWith('/results')    && m === 'GET') return ok(M.childResults);
  if (u.startsWith('parents/children/') && u.endsWith('/fees')       && m === 'GET') return ok(M.childFees);

  // ── ATTENDANCE ──────────────────────────────────────────────────────────────
  if (u.startsWith('attendance/classroom/') && m === 'GET') return ok(M.attendanceRecords);
  if (u.startsWith('attendance/student/')   && m === 'GET') return ok(M.myAttendance);
  if (u === 'attendance' && m === 'POST')                   return ok({ success: true });

  // ── FEES ─────────────────────────────────────────────────────────────────────
  if (u === 'fees' && m === 'GET')  return ok(_fees);
  if (u === 'fees' && m === 'POST') {
    const rec = { id: ++nextId, ...body };
    _fees.push(rec);
    return ok(rec);
  }
  if (u.startsWith('fees/') && m === 'PUT') {
    _fees = _fees.map(f => f.id === idOf(u) ? { ...f, ...body } : f);
    return ok(body);
  }
  if (u.startsWith('fees/') && m === 'DELETE') {
    _fees = _fees.filter(f => f.id !== idOf(u));
    return ok({});
  }
  if (u.startsWith('student-fees/student/') && m === 'GET') return ok(M.studentFeesByStudent);

  // ── EXAMS ────────────────────────────────────────────────────────────────────
  if ((u === 'exams' || u.startsWith('exams/academic-year')) && m === 'GET') return ok(_exams);
  if (u === 'exams' && m === 'POST') {
    const rec = { id: ++nextId, ...body };
    _exams.push(rec);
    return ok(rec);
  }
  if (u.startsWith('exams/') && m === 'PUT') {
    _exams = _exams.map(e => e.id === idOf(u) ? { ...e, ...body } : e);
    return ok(body);
  }
  if (u.startsWith('exams/') && m === 'DELETE') {
    _exams = _exams.filter(e => e.id !== idOf(u));
    return ok({});
  }

  // ── RESULTS ──────────────────────────────────────────────────────────────────
  if (u === 'results' && m === 'GET')  return ok(_results);
  if (u === 'results' && m === 'POST') {
    const rec = { id: ++nextId, ...body };
    _results.push(rec);
    return ok(rec);
  }
  if (u.startsWith('results/') && m === 'PUT') {
    _results = _results.map(r => r.id === idOf(u) ? { ...r, ...body } : r);
    return ok(body);
  }
  if (u.startsWith('results/') && m === 'DELETE') {
    _results = _results.filter(r => r.id !== idOf(u));
    return ok({});
  }
  if (u.startsWith('results/student-exam/') && m === 'GET')
    return ok(_results.filter(r => r.studentExamId === idOf(u)));
  if (u.startsWith('results/student/') && m === 'GET')
    return ok(_results.filter(r => r.studentId === idOf(u)));

  // ── NOTIFICATIONS ─────────────────────────────────────────────────────────────
  if (u === 'notifications' && m === 'GET') return ok(_notifications);
  if (u.endsWith('/read') && m === 'PATCH') {
    const id = parseInt(u.split('/')[1], 10); // notifications/{id}/read
    _notifications = _notifications.map(n => n.id === id ? { ...n, isRead: true } : n);
    return ok({});
  }

  // ── ANNOUNCEMENTS ─────────────────────────────────────────────────────────────
  if (u === 'announcements' && m === 'GET')  return ok(_announcements);
  if (u === 'announcements' && m === 'POST') {
    const rec = { id: ++nextId, isPublished: false, ...body };
    _announcements.push(rec);
    return ok(rec);
  }
  if (u.startsWith('announcements/') && m === 'PUT') {
    _announcements = _announcements.map(a => a.id === idOf(u) ? { ...a, ...body } : a);
    return ok(body);
  }
  if (u.startsWith('announcements/') && m === 'DELETE') {
    _announcements = _announcements.filter(a => a.id !== idOf(u));
    return ok({});
  }
  if (u.endsWith('/publish')   && m === 'POST') {
    const id = idOf(u.replace('/publish', ''));
    _announcements = _announcements.map(a => a.id === id ? { ...a, isPublished: true,  publishedAt: new Date().toISOString().split('T')[0] } : a);
    return ok({});
  }
  if (u.endsWith('/unpublish') && m === 'POST') {
    const id = idOf(u.replace('/unpublish', ''));
    _announcements = _announcements.map(a => a.id === id ? { ...a, isPublished: false, publishedAt: null } : a);
    return ok({});
  }

  // ── TIMETABLE ─────────────────────────────────────────────────────────────────
  if (u === 'timetables/teacher/me' && m === 'GET') return ok(M.teacherTimetable);

  // ── PING / HEALTH ─────────────────────────────────────────────────────────────
  if (u === 'ping' || u.startsWith('actuator')) return ok({ status: 'UP (mock)' });

  // ── FALLBACK ──────────────────────────────────────────────────────────────────
  console.warn(`[MockBaseQuery] Unhandled: ${m} /${u}`);
  return ok([]);
};
