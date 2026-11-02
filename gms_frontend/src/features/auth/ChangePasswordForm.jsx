import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import {
  Box,
  Button,
  TextField,
  InputAdornment,
  IconButton,
  Alert,
} from '@mui/material';
import { Lock, Visibility, VisibilityOff } from '@mui/icons-material';
import { toast } from 'react-toastify';
import { useChangePasswordMutation } from '../../store/api/apiSlice';

const passwordPolicy = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&.#_])[A-Za-z\d@$!%*?&.#_]{8,}$/;

const schema = yup.object({
  oldPassword: yup
    .string()
    .required('Current password is required'),
  newPassword: yup
    .string()
    .required('New password is required')
    .min(8, 'New password must be at least 8 characters')
    .max(100, 'New password must be at most 100 characters')
    .matches(passwordPolicy, 'Use at least 8 characters with upper, lower, digit and one of @$!%*?&.#_'),
  confirmNewPassword: yup
    .string()
    .oneOf([yup.ref('newPassword'), null], 'Passwords must match')
    .required('Please confirm the new password'),
});

/**
 * The credential rotation every account may perform, and the one a provisioned account is
 * held at until it has.
 *
 * <p>The policy check is the server's own: `PasswordValidator` accepts a password with at
 * least eight characters, one upper, one lower, one digit and one of `@$!%*?&.#_`. The
 * form mirrors it so the caller is told before the round trip, and the server still decides
 * — a form that accepted what the server refuses would be a worse experience, not a safer
 * one.</p>
 */
const ChangePasswordForm = ({ onSuccess, submitLabel = 'Update Password' }) => {
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
      if (onSuccess) {
        onSuccess();
      }
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
    <>
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
            {isLoading ? 'Updating...' : submitLabel}
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
    </>
  );
};

export default ChangePasswordForm;
