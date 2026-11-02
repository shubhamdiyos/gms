import { Box, Paper, Typography } from '@mui/material';
import ChangePasswordForm from '../auth/ChangePasswordForm';

const SuperAdminSettingsPage = () => {
  return (
    <Box>
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          Settings
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Manage your SuperAdmin account security.
        </Typography>
      </Box>

      <Paper sx={{ p: 3, maxWidth: 500 }}>
        <Typography variant="h6" gutterBottom>
          Change Password
        </Typography>

        <ChangePasswordForm />
      </Paper>
    </Box>
  );
};

export default SuperAdminSettingsPage;
