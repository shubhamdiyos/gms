import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import {
  Box,
  Paper,
  TextField,
  Button,
  Typography,
  InputAdornment,
  IconButton,
  Alert,
} from '@mui/material';
import { Lock, Visibility, VisibilityOff } from '@mui/icons-material';
import { toast } from 'react-toastify';
import { useChangePasswordMutation } from '../../store/api/apiSlice';

const schema = yup.object({
  oldPassword: yup
    .string()
    .required('Current password is required'),
  newPassword: yup
    .string()
    .required('New password is required')
    .min(8, 'New password must be at least 8 characters'),
  confirmNewPassword: yup
    .string()
    .oneOf([yup.ref('newPassword'), null], 'Passwords must match')
    .required('Please confirm the new password'),
});

const SuperAdminSettingsPage = () => {
  const [showOldPassword, setShowOldPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [changePassword, { isLoading }] = useChangePasswordMutation();
  const [apiError, setApiError] = useState('');

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm({
    resolver: yupResolver(schema),
    defaultValues: {
      oldPassword: '',
      newPassword: '',
      confirmNewPassword: '',
    },
  });

  const onSubmit = async (data) => {
    setApiError('');
    try {
      await changePassword({
        oldPassword: data.oldPassword,
        newPassword: data.newPassword,
      }).unwrap();
      toast.success('Password changed successfully');
      reset();
    } catch (error) {
      if (error?.status === 400) {
        setApiError('Invalid current password or new password does not meet requirements.');
      } else if (error?.status === 401) {
        setApiError('You are not authorized. Please login again.');
      } else {
        setApiError('Failed to change password. Please try again.');
      }
    }
  };

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

        {apiError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {apiError}
          </Alert>
        )}

        <form onSubmit={handleSubmit(onSubmit)}>
          <TextField
            {...register('oldPassword')}
            fullWidth
            margin="normal"
            label="Current Password"
            type={showOldPassword ? 'text' : 'password'}
            error={!!errors.oldPassword}
            helperText={errors.oldPassword?.message}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <Lock color="action" />
                </InputAdornment>
              ),
              endAdornment: (
                <InputAdornment position="end">
                  <IconButton onClick={() => setShowOldPassword((prev) => !prev)} edge="end">
                    {showOldPassword ? <VisibilityOff /> : <Visibility />}
                  </IconButton>
                </InputAdornment>
              ),
            }}
          />

          <TextField
            {...register('newPassword')}
            fullWidth
            margin="normal"
            label="New Password"
            type={showNewPassword ? 'text' : 'password'}
            error={!!errors.newPassword}
            helperText={errors.newPassword?.message}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <Lock color="action" />
                </InputAdornment>
              ),
              endAdornment: (
                <InputAdornment position="end">
                  <IconButton onClick={() => setShowNewPassword((prev) => !prev)} edge="end">
                    {showNewPassword ? <VisibilityOff /> : <Visibility />}
                  </IconButton>
                </InputAdornment>
              ),
            }}
          />

          <TextField
            {...register('confirmNewPassword')}
            fullWidth
            margin="normal"
            label="Confirm New Password"
            type="password"
            error={!!errors.confirmNewPassword}
            helperText={errors.confirmNewPassword?.message}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <Lock color="action" />
                </InputAdornment>
              ),
            }}
          />

          <Box sx={{ mt: 3, display: 'flex', gap: 2 }}>
            <Button
              type="submit"
              variant="contained"
              color="primary"
              disabled={isLoading}
            >
              {isLoading ? 'Updating...' : 'Update Password'}
            </Button>
            <Button
              type="button"
              variant="outlined"
              onClick={() => {
                reset();
                setApiError('');
              }}
            >
              Reset
            </Button>
          </Box>
        </form>
      </Paper>
    </Box>
  );
};

export default SuperAdminSettingsPage;
