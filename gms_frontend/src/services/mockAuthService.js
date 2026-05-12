/**
 * Mock Auth Service
 *
 * Used when VITE_MOCK_AUTH=true to bypass the backend entirely.
 * Simulates login for all roles so the frontend team can work
 * without a running backend.
 *
 * TO DISABLE: set VITE_MOCK_AUTH=false (or remove it) in .env
 * and redeploy — zero code changes needed.
 */

// Mock users keyed by username. Password is always accepted.
const MOCK_USERS = {
  superadmin: {
    username: 'superadmin',
    roles: ['SUPERADMIN'],
    schoolId: null,
    empId: 1,
    requirePasswordChange: false,
  },
  admin: {
    username: 'admin',
    roles: ['ADMIN'],
    schoolId: 1,
    empId: 2,
    requirePasswordChange: false,
  },
  teacher: {
    username: 'teacher',
    roles: ['TEACHER'],
    schoolId: 1,
    empId: 3,
    requirePasswordChange: false,
  },
  student: {
    username: 'student',
    roles: ['STUDENT'],
    schoolId: 1,
    empId: 4,
    requirePasswordChange: false,
  },
  parent: {
    username: 'parent',
    roles: ['PARENT'],
    schoolId: 1,
    empId: 5,
    requirePasswordChange: false,
  },
};

/**
 * Build a fake (non-verifiable) JWT-shaped token so downstream code
 * that calls jwtDecode() doesn't break. Expiry is set 24h from now.
 */
const buildFakeToken = (user) => {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const exp = Math.floor(Date.now() / 1000) + 86400; // 24 h
  const payload = btoa(
    JSON.stringify({
      sub: user.username,
      roles: user.roles,
      schoolId: user.schoolId,
      empId: user.empId,
      exp,
      iat: Math.floor(Date.now() / 1000),
    })
  );
  const signature = btoa('mock-signature');
  return `${header}.${payload}.${signature}`;
};

const MockAuthService = {
  /**
   * Simulates login. Any password is accepted; username must match
   * a known mock user (falls back to superadmin if unknown).
   */
  login(credentials) {
    const { username } = credentials;
    // Fallback to superadmin if username not in list
    const user = MOCK_USERS[username.toLowerCase()] || MOCK_USERS.superadmin;
    const token = buildFakeToken(user);
    return Promise.resolve({ token, user });
  },
};

export default MockAuthService;
