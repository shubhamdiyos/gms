import { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  Grid,
  InputLabel,
  ListItemText,
  MenuItem,
  OutlinedInput,
  Paper,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import { Add } from '@mui/icons-material';
import { Controller, useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import { toast } from 'react-toastify';
import {
  useCreateUserMutation,
  useGetEmployeesQuery,
  useGetUsersQuery,
} from '../../store/api/apiSlice';

// STUDENT and PARENT accounts are minted by their own creation flows (StudentsPage / ParentsPage),
// where the record and the login are written in one transaction. This screen is for staff logins.
const ROLE_OPTIONS = [
  { value: 'TEACHER', label: 'Teacher' },
  { value: 'ACCOUNTANT', label: 'Accountant' },
  { value: 'ADMIN', label: 'Administrator' },
];

const schema = yup.object({
  fullName: yup.string().required('Full name is required'),
  username: yup
    .string()
    .required('Username is required')
    .matches(/^[a-zA-Z0-9._-]+$/, 'Use letters, digits, dot, underscore or hyphen'),
  email: yup.string().email('Invalid email').required('Email is required'),
  phoneNumber: yup.string().nullable(),
  password: yup
    .string()
    .required('Password is required')
    .min(8, 'At least 8 characters')
    .matches(/[a-z]/, 'Needs a lowercase letter')
    .matches(/[A-Z]/, 'Needs an uppercase letter')
    .matches(/\d/, 'Needs a digit')
    .matches(/[@$!%*?&.#_]/, 'Needs a special character (@ $ ! % * ? & . # _)'),
  roles: yup.array().of(yup.string()).min(1, 'Choose at least one role').required(),
  employeeId: yup
    .number()
    .nullable()
    .transform((value, original) => (original === '' || original == null ? null : value)),
});

const UsersPage = () => {
  const [open, setOpen] = useState(false);
  const { data: users = [], isLoading, isError, error, refetch } = useGetUsersQuery();
  const { data: employees = [], isError: employeesError } = useGetEmployeesQuery();
  const [createUser, { isLoading: creating }] = useCreateUserMutation();

  const {
    register,
    handleSubmit,
    reset,
    control,
    watch,
    setError,
    formState: { errors },
  } = useForm({
    resolver: yupResolver(schema),
    defaultValues: { roles: [], employeeId: '' },
  });

  const selectedRoles = watch('roles') || [];
  const needsEmployee = selectedRoles.includes('TEACHER');

  const handleOpen = () => {
    reset({ roles: [], employeeId: '' });
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
    reset({ roles: [], employeeId: '' });
  };

  const onSubmit = async (data) => {
    if (needsEmployee && data.employeeId == null) {
      setError('employeeId', {
        type: 'manual',
        message: 'A teacher login must be linked to a staff record',
      });
      return;
    }
    try {
      await createUser({
        username: data.username.trim(),
        fullName: data.fullName.trim(),
        email: data.email.trim(),
        phoneNumber: data.phoneNumber?.trim() || null,
        password: data.password,
        roles: data.roles,
        employeeId: data.employeeId == null ? null : Number(data.employeeId),
      }).unwrap();
      toast.success('Account created — the user must change this password at first sign-in');
      handleClose();
      refetch();
    } catch (err) {
      toast.error(err?.data?.message || 'Could not create the account');
    }
  };

  // GET /employees answers the raw entity shape (`full_name`, numeric `id`, often a null
  // employeeId), so the label is resolved by primary key and the display name falls back to the
  // split name fields.
  const employeeName = (employee) =>
    employee.full_name || `${employee.firstName || ''} ${employee.lastName || ''}`.trim();

  const getEmployeeLabel = (employeeRefId) => {
    if (!employeeRefId) return '—';
    const employee = employees.find((candidate) => candidate.id === employeeRefId);
    return employee ? employeeName(employee) : `#${employeeRefId}`;
  };

  const columns = [
    { field: 'username', headerName: 'Username', width: 150 },
    { field: 'fullName', headerName: 'Full Name', width: 190 },
    { field: 'email', headerName: 'Email', width: 220 },
    {
      field: 'roles',
      headerName: 'Roles',
      width: 190,
      sortable: false,
      renderCell: (params) => (
        <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap' }}>
          {(params.value || []).map((role) => (
            <Chip key={role} label={role} size="small" />
          ))}
        </Stack>
      ),
    },
    {
      field: 'employeeRefId',
      headerName: 'Staff record',
      width: 200,
      sortable: false,
      renderCell: (params) => getEmployeeLabel(params.value),
    },
    {
      field: 'requirePasswordChange',
      headerName: 'First login',
      width: 130,
      sortable: false,
      renderCell: (params) => (
        <Chip
          label={params.value ? 'Must rotate' : 'Set by user'}
          size="small"
          color={params.value ? 'warning' : 'default'}
        />
      ),
    },
    {
      field: 'enabled',
      headerName: 'Status',
      width: 100,
      sortable: false,
      renderCell: (params) => (
        <Chip
          label={params.value ? 'Active' : 'Disabled'}
          size="small"
          color={params.value ? 'success' : 'error'}
        />
      ),
    },
  ];

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3, gap: 2 }}>
        <Box>
          <Typography variant="h4" component="h1" gutterBottom>
            User Accounts
          </Typography>
          <Typography variant="body1" color="text.secondary">
            Staff logins for this school. Student and parent logins are created with their records.
          </Typography>
        </Box>
        <Button variant="contained" startIcon={<Add />} onClick={handleOpen}>
          Add Account
        </Button>
      </Box>

      {isError && (
        <Alert
          severity="error"
          sx={{ mb: 2 }}
          action={
            <Button color="inherit" size="small" onClick={refetch}>
              Retry
            </Button>
          }
        >
          Failed to load accounts
          {error?.status ? ` (HTTP ${error.status})` : ''}: {error?.data?.message || 'unexpected error'}
        </Alert>
      )}

      {employeesError && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          Staff records could not be loaded, so a new teacher account cannot be linked to one right
          now. Accountants and administrators can still be created.
        </Alert>
      )}

      <Paper sx={{ height: 600, width: '100%' }}>
        <DataGrid
          rows={users}
          columns={columns}
          loading={isLoading}
          pageSizeOptions={[10, 25, 50]}
          initialState={{ pagination: { paginationModel: { pageSize: 10 } } }}
          disableRowSelectionOnClick
        />
      </Paper>

      <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
        <form onSubmit={handleSubmit(onSubmit)}>
          <DialogTitle>Add Account</DialogTitle>
          <DialogContent>
            <Grid container spacing={2} sx={{ mt: 1 }}>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('fullName')}
                  fullWidth
                  label="Full Name"
                  error={!!errors.fullName}
                  helperText={errors.fullName?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('username')}
                  fullWidth
                  label="Username"
                  error={!!errors.username}
                  helperText={errors.username?.message || 'The name used to sign in'}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('email')}
                  fullWidth
                  type="email"
                  label="Email"
                  error={!!errors.email}
                  helperText={errors.email?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('phoneNumber')}
                  fullWidth
                  label="Phone Number"
                  error={!!errors.phoneNumber}
                  helperText={errors.phoneNumber?.message}
                />
              </Grid>
              <Grid item xs={12}>
                <TextField
                  {...register('password')}
                  fullWidth
                  type="password"
                  label="First password"
                  error={!!errors.password}
                  helperText={
                    errors.password?.message ||
                    'At least 8 characters with upper and lower case, a digit and a special character. The user is asked to change it at first sign-in.'
                  }
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControl fullWidth error={!!errors.roles}>
                  <InputLabel id="account-roles-label">Roles</InputLabel>
                  <Controller
                    name="roles"
                    control={control}
                    render={({ field }) => (
                      <Select
                        {...field}
                        multiple
                        labelId="account-roles-label"
                        label="Roles"
                        input={<OutlinedInput label="Roles" />}
                        renderValue={(selected) =>
                          selected
                            .map((role) => ROLE_OPTIONS.find((option) => option.value === role)?.label || role)
                            .join(', ')
                        }
                      >
                        {ROLE_OPTIONS.map((option) => (
                          <MenuItem key={option.value} value={option.value}>
                            <ListItemText primary={option.label} />
                          </MenuItem>
                        ))}
                      </Select>
                    )}
                  />
                  {errors.roles && (
                    <Typography variant="caption" color="error" sx={{ mt: 1, ml: 2 }}>
                      {errors.roles?.message}
                    </Typography>
                  )}
                </FormControl>
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControl fullWidth error={!!errors.employeeId}>
                  <InputLabel id="account-employee-label">Staff record</InputLabel>
                  <Controller
                    name="employeeId"
                    control={control}
                    render={({ field }) => (
                      <Select
                        {...field}
                        labelId="account-employee-label"
                        label="Staff record"
                        value={field.value ?? ''}
                      >
                        <MenuItem value="">None</MenuItem>
                        {employees.map((employee) => (
                          <MenuItem key={employee.id} value={employee.id}>
                            {employeeName(employee)}
                            {employee.designation ? ` · ${employee.designation}` : ''}
                          </MenuItem>
                        ))}
                      </Select>
                    )}
                  />
                  <Typography variant="caption" color={errors.employeeId ? 'error' : 'text.secondary'} sx={{ mt: 1, ml: 2 }}>
                    {errors.employeeId?.message ||
                      (needsEmployee
                        ? 'Required for a teacher: classes, students and timetable hang off this record.'
                        : 'Optional for non-teaching staff.')}
                  </Typography>
                </FormControl>
              </Grid>
            </Grid>
          </DialogContent>
          <DialogActions>
            <Button onClick={handleClose}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={creating}>
              Create
            </Button>
          </DialogActions>
        </form>
      </Dialog>
    </Box>
  );
};

export default UsersPage;
