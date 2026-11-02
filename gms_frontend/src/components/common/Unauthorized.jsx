import { useLocation, useNavigate } from 'react-router-dom';
import { Box, Button, Paper, Typography } from '@mui/material';
import { Lock } from '@mui/icons-material';
import { useAuth, getDashboardRoute } from '../../hooks/useAuth';

const Unauthorized = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { getUserRole, logout } = useAuth();
  const userRole = getUserRole();
  const dashboardPath = getDashboardRoute(userRole);
  const attemptedPath = location.state?.from;

  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', pt: 4 }}>
      <Paper sx={{ p: 4, maxWidth: 560, width: '100%', textAlign: 'center' }}>
        <Lock color="primary" sx={{ fontSize: 44, mb: 1 }} />
        <Typography variant="h5" component="h1" gutterBottom>
          Not available for your role
        </Typography>
        <Typography variant="body1" color="text.secondary" paragraph>
          {userRole
            ? `${userRole} accounts cannot open this screen.`
            : 'This account has no portal role that can open a screen.'}
        </Typography>
        {attemptedPath && (
          <Typography variant="body2" color="text.secondary" paragraph>
            Requested: {attemptedPath}
          </Typography>
        )}
        <Box sx={{ display: 'flex', gap: 2, justifyContent: 'center', mt: 3 }}>
          {dashboardPath && (
            <Button
              variant="contained"
              onClick={() => navigate(dashboardPath)}
              sx={{
                background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                '&:hover': {
                  background: 'linear-gradient(135deg, #5a6fd8 0%, #6a4190 100%)',
                },
              }}
            >
              Go to my screen
            </Button>
          )}
          <Button variant="outlined" onClick={logout}>
            Log out
          </Button>
        </Box>
      </Paper>
    </Box>
  );
};

export default Unauthorized;
