import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import ProtectedRoute from './ProtectedRoute';
import { useAuth } from '../../hooks/useAuth';

vi.mock('../../hooks/useAuth', () => ({
  useAuth: vi.fn(),
}));

const ADMIN = { username: 'gsilva', roles: ['ADMIN'], requirePasswordChange: false };
const ADMIN_MUST_ROTATE = { ...ADMIN, requirePasswordChange: true };

const renderAt = (path, user, requiredRoles = []) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route
          path="/change-password"
          element={<div>the rotation screen</div>}
        />
        <Route
          path="/unauthorized"
          element={<div>the denied screen</div>}
        />
        <Route
          path="*"
          element={
            <ProtectedRoute requiredRoles={requiredRoles}>
              <div>the screen behind the guard</div>
            </ProtectedRoute>
          }
        />
      </Routes>
    </MemoryRouter>
  );

describe('ProtectedRoute', () => {
  beforeEach(() => {
    useAuth.mockReturnValue({
      isAuthenticated: true,
      loading: false,
      user: ADMIN,
      hasRole: () => true,
    });
  });

  it('lets a settled account through', () => {
    renderAt('/admin/students', ADMIN);

    expect(screen.getByText('the screen behind the guard')).toBeInTheDocument();
  });

  it('refuses to show any other screen to an account that must change its password', () => {
    useAuth.mockReturnValue({
      isAuthenticated: true,
      loading: false,
      user: ADMIN_MUST_ROTATE,
      hasRole: () => true,
    });

    renderAt('/admin/students', ADMIN_MUST_ROTATE);

    // The account is authenticated and authorised, and it still cannot use the portal
    // until it has set its own password: the flag the server sends at login was stored
    // and read by nobody until now.
    expect(screen.getByText('the rotation screen')).toBeInTheDocument();
    expect(screen.queryByText('the screen behind the guard')).not.toBeInTheDocument();
  });

  it('lets the rotation screen itself through, or the gate would be a loop', () => {
    useAuth.mockReturnValue({
      isAuthenticated: true,
      loading: false,
      user: ADMIN_MUST_ROTATE,
      hasRole: () => true,
    });

    renderAt('/change-password', ADMIN_MUST_ROTATE);

    expect(screen.getByText('the rotation screen')).toBeInTheDocument();
  });

  it('still refuses a role that may not act', () => {
    useAuth.mockReturnValue({
      isAuthenticated: true,
      loading: false,
      user: { username: 't', roles: ['TEACHER'], requirePasswordChange: false },
      hasRole: () => false,
    });

    renderAt('/admin/fees', { username: 't', roles: ['TEACHER'] }, ['ADMIN']);

    expect(screen.getByText('the denied screen')).toBeInTheDocument();
    expect(screen.queryByText('the screen behind the guard')).not.toBeInTheDocument();
  });

  it('sends an unauthenticated caller to the login screen', () => {
    useAuth.mockReturnValue({
      isAuthenticated: false,
      loading: false,
      user: null,
      hasRole: () => false,
    });

    render(
      <MemoryRouter initialEntries={['/admin/students']}>
        <Routes>
          <Route path="/login" element={<div>the login screen</div>} />
          <Route
            path="*"
            element={
              <ProtectedRoute>
                <div>the screen behind the guard</div>
              </ProtectedRoute>
            }
          />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('the login screen')).toBeInTheDocument();
  });
});
