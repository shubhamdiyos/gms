import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { ROUTES } from '../../constants';
import { Box, CircularProgress } from '@mui/material';

const ProtectedRoute = ({ children, requiredRoles = [] }) => {
  const { isAuthenticated, hasRole, loading, user } = useAuth();
  const location = useLocation();

  // Show loading spinner while checking authentication
  if (loading) {
    return (
      <Box
        display="flex"
        justifyContent="center"
        alignItems="center"
        minHeight="100vh"
      >
        <CircularProgress />
      </Box>
    );
  }

  // Redirect to login if not authenticated
  if (!isAuthenticated) {
    return <Navigate to={ROUTES.LOGIN} state={{ from: location }} replace />;
  }

  // An account that was handed a generated password may only change it. The server says so
  // at login (`requirePasswordChange`), and this is where that is honoured: without this
  // gate the flag was stored and read by nobody, so the issued password stayed in use for
  // as long as the account lived — the step PRD J-01 names, and the one ACD-12 blocks.
  if (user?.requirePasswordChange && location.pathname !== ROUTES.CHANGE_PASSWORD) {
    return <Navigate to={ROUTES.CHANGE_PASSWORD} replace />;
  }

  // Check role-based access
  const required = (Array.isArray(requiredRoles) ? requiredRoles : [requiredRoles])
    .filter(Boolean);

  if (required.length > 0 && !hasRole(required)) {
    // Terminal page, not a silent bounce to the dashboard: the denied path must stay visible.
    return <Navigate to={ROUTES.UNAUTHORIZED} state={{ from: location.pathname }} replace />;
  }

  return children;
};

export default ProtectedRoute;
