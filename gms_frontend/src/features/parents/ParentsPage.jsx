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
  useCreateParentMutation,
  useGetParentsQuery,
  useGetStudentSearchQuery,
} from '../../store/api/apiSlice';

// The create flow writes the parent record and the PARENT login together. The children picker
// reads one page of the roll; the server's search ceiling applies, so the note below states how
// many pupils were loaded rather than pretending the list is the whole school.
const STUDENT_PICKER_SIZE = 100;

const schema = yup.object({
  firstName: yup.string().required('First name is required'),
  lastName: yup.string().required('Last name is required'),
  email: yup.string().email('Invalid email').required('Email is required'),
  phoneNumber: yup.string().nullable(),
  studentIds: yup
    .array()
    .of(yup.number())
    .min(1, 'Select at least one child')
    .required('Select at least one child'),
});

const ParentsPage = () => {
  const [open, setOpen] = useState(false);
  const [provisioned, setProvisioned] = useState(null);
  const { data: parents = [], isLoading, isError, error, refetch } = useGetParentsQuery();
  const { data: studentPage, isError: studentsError } = useGetStudentSearchQuery({
    page: 0,
    size: STUDENT_PICKER_SIZE,
    sort: 'lastName,asc',
  });
  const [createParent, { isLoading: creating }] = useCreateParentMutation();

  const students = Array.isArray(studentPage?.content) ? studentPage.content : [];
  const totalStudents = Number.isFinite(studentPage?.totalElements) ? studentPage.totalElements : students.length;

  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors },
  } = useForm({
    resolver: yupResolver(schema),
    defaultValues: { studentIds: [] },
  });

  const handleOpen = () => {
    reset({ firstName: '', lastName: '', email: '', phoneNumber: '', studentIds: [] });
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
    reset({ studentIds: [] });
  };

  const onSubmit = async (data) => {
    try {
      const created = await createParent({
        firstName: data.firstName.trim(),
        lastName: data.lastName.trim(),
        email: data.email.trim(),
        phoneNumber: data.phoneNumber?.trim() || null,
        studentIds: data.studentIds.map(Number),
      }).unwrap();
      toast.success('Parent created');
      if (created?.temporaryPassword) {
        setProvisioned({
          name: `${created.firstName} ${created.lastName}`.trim(),
          username: created.username,
          temporaryPassword: created.temporaryPassword,
        });
      }
      handleClose();
      refetch();
    } catch (err) {
      toast.error(err?.data?.message || 'Could not create the parent');
    }
  };

  const copyProvisionedCredentials = async () => {
    const text = `Username: ${provisioned?.username}\nTemporary password: ${provisioned?.temporaryPassword}`;
    try {
      await navigator.clipboard.writeText(text);
      toast.success('Credentials copied');
    } catch {
      toast.error('Could not copy — select the values manually');
    }
  };

  const columns = [
    { field: 'firstName', headerName: 'First Name', width: 150 },
    { field: 'lastName', headerName: 'Last Name', width: 150 },
    { field: 'email', headerName: 'Email', width: 220 },
    { field: 'phoneNumber', headerName: 'Phone', width: 140 },
    {
      field: 'students',
      headerName: 'Children',
      width: 320,
      sortable: false,
      renderCell: (params) => {
        const children = Array.isArray(params.value) ? params.value : [];
        if (children.length === 0) return '—';
        return (
          <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
            {children.map((child) => (
              <Chip
                key={child.id}
                size="small"
                label={`${child.firstName} ${child.lastName}`.trim()}
              />
            ))}
          </Box>
        );
      },
    },
    {
      field: 'enabled',
      headerName: 'Login',
      width: 110,
      sortable: false,
      renderCell: (params) => (
        <Chip
          label={params.value ? 'Active' : 'Not active'}
          size="small"
          color={params.value ? 'success' : 'default'}
        />
      ),
    },
  ];

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3, gap: 2 }}>
        <Box>
          <Typography variant="h4" component="h1" gutterBottom>
            Parents
          </Typography>
          <Typography variant="body1" color="text.secondary">
            Link a parent to their children and issue their portal login.
          </Typography>
        </Box>
        <Button variant="contained" startIcon={<Add />} onClick={handleOpen}>
          Add Parent
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
          Failed to load parents
          {error?.status ? ` (HTTP ${error.status})` : ''}: {error?.data?.message || 'unexpected error'}
        </Alert>
      )}

      {studentsError && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          The student roll could not be loaded, so a parent cannot be linked to a child right now.
        </Alert>
      )}

      <Paper sx={{ height: 560, width: '100%' }}>
        <DataGrid
          rows={parents}
          columns={columns}
          loading={isLoading}
          pageSizeOptions={[10, 25, 50]}
          initialState={{ pagination: { paginationModel: { pageSize: 10 } } }}
          disableRowSelectionOnClick
        />
      </Paper>

      <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
        <form onSubmit={handleSubmit(onSubmit)}>
          <DialogTitle>Add Parent</DialogTitle>
          <DialogContent>
            <Grid container spacing={2} sx={{ mt: 1 }}>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('firstName')}
                  fullWidth
                  label="First Name"
                  error={!!errors.firstName}
                  helperText={errors.firstName?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('lastName')}
                  fullWidth
                  label="Last Name"
                  error={!!errors.lastName}
                  helperText={errors.lastName?.message}
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
                <FormControl fullWidth error={!!errors.studentIds}>
                  <InputLabel id="parent-children-label">Children</InputLabel>
                  <Controller
                    name="studentIds"
                    control={control}
                    render={({ field }) => (
                      <Select
                        {...field}
                        multiple
                        labelId="parent-children-label"
                        label="Children"
                        input={<OutlinedInput label="Children" />}
                        renderValue={(selected) =>
                          selected
                            .map((id) => {
                              const student = students.find((candidate) => candidate.id === id);
                              return student
                                ? `${student.firstName} ${student.lastName}`.trim()
                                : `#${id}`;
                            })
                            .join(', ')
                        }
                      >
                        {students.map((student) => (
                          <MenuItem key={student.id} value={student.id}>
                            <ListItemText
                              primary={`${student.firstName} ${student.lastName}`.trim()}
                              secondary={`${student.studentId || ''}${
                                student.className ? ` · ${student.className}` : ''
                              }`}
                            />
                          </MenuItem>
                        ))}
                      </Select>
                    )}
                  />
                  <Typography
                    variant="caption"
                    color={errors.studentIds ? 'error' : 'text.secondary'}
                    sx={{ mt: 1, ml: 2 }}
                  >
                    {errors.studentIds?.message ||
                      (totalStudents > students.length
                        ? `Showing the first ${students.length} of ${totalStudents} students.`
                        : 'A parent can be linked to more than one child.')}
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

      {/*
        The generated login exists only in the create response — the server keeps the hash. If this
        is dismissed without relaying it, the parent needs a password reset.
      */}
      <Dialog open={Boolean(provisioned)} onClose={() => setProvisioned(null)} maxWidth="sm" fullWidth>
        <DialogTitle>Parent login created</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {provisioned?.name} can sign in with these credentials. The password is not stored and
            cannot be shown again; the parent must choose a new one on first sign-in.
          </Typography>
          <Box sx={{ bgcolor: 'action.hover', borderRadius: 1, p: 2 }}>
            <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
              Username: <strong>{provisioned?.username || '—'}</strong>
            </Typography>
            <Typography variant="body2" sx={{ fontFamily: 'monospace', mt: 1 }}>
              Temporary password: <strong>{provisioned?.temporaryPassword}</strong>
            </Typography>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setProvisioned(null)}>Close</Button>
          <Button variant="contained" onClick={copyProvisionedCredentials}>
            Copy credentials
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default ParentsPage;
