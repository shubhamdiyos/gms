import { useDispatch, useSelector } from 'react-redux';
import { useNavigate } from 'react-router-dom';
import { Box, Button, Paper, Typography } from '@mui/material';
import ChangePasswordForm from './ChangePasswordForm';
import { updateUser } from '../../store/slices/authSlice';
import { getDashboardRoute, getPrimaryRole } from '../../hooks/useAuth';
import { useAuth } from '../../hooks/useAuth';
import { ROUTES } from '../../constants';

/**
 * The step PRD J-01 names: an account that was handed a generated password changes it before
 * it is used.
 *
 * <p>The server says so — `AuthController.login` returns `requirePasswordChange` with the
 * token, and `SchoolServiceImpl:107`, `UserServiceImpl:98`, `ParentServiceImpl:301` and
 * `StudentServiceImpl:116` all set that flag on the account they create. The flag was
 * stored by the client and read by nobody, so a provisioned administrator kept the
 * rule-derivable password (`<username>@123`) for as long as the account lived. This screen
 * is where the flag is honoured, and `ProtectedRoute` holds every other route until it has
 * been.</p>
 */
const ChangePasswordPage = () => {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const { logout } = useAuth();
  const user = useSelector((state) => state.auth.user);

  const onSuccess = () => {
    dispatch(updateUser({ requirePasswordChange: false }));
    const dashboard = getDashboardRoute(getPrimaryRole(user?.roles));
    navigate(dashboard || ROUTES.LOGIN, { replace: true });
  };

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: 'background.default',
        p: 2,
      }}
    >
      <Paper sx={{ p: 4, maxWidth: 480, width: '100%' }}>
        <Typography variant="h5" component="h1" gutterBottom>
          Change your password
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          This account was created with a password issued by the system. Set your own before
          you continue.
        </Typography>

        <ChangePasswordForm onSuccess={onSuccess} submitLabel="Set password" />

        <Box sx={{ mt: 3 }}>
          <Button
            type="button"
            variant="text"
            onClick={() => logout()}
          >
            Sign out instead
          </Button>
        </Box>
      </Paper>
    </Box>
  );
};

export default ChangePasswordPage;
