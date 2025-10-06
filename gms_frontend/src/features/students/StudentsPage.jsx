import { useState } from 'react';
import {
  Box,
  Typography,
  Button,
  Paper,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Grid,
  Chip,
  IconButton,
  Tooltip,
  MenuItem,
  FormControl,
  InputLabel,
  Select,
} from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import {
  Add,
  Edit,
  Delete,
  Visibility,
  Person,
  ToggleOff,
  ToggleOn,
} from '@mui/icons-material';
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import {
  useGetStudentsQuery,
  useCreateStudentMutation,
  useUpdateStudentMutation,
  useToggleStudentMutation,
  useGetClassroomsQuery,
} from '../../store/api/apiSlice';
import { toast } from 'react-toastify';

const schema = yup.object({
  firstName: yup.string().required('First name is required'),
  lastName: yup.string().required('Last name is required'),
  email: yup.string().email('Invalid email').required('Email is required'),
  phoneNumber: yup.string().required('Phone number is required'),
  dateOfBirth: yup.date().required('Date of birth is required'),
  gender: yup.string().required('Gender is required'),
  classroomId: yup.number().required('Classroom is required'),
});

const StudentsPage = () => {
  const [open, setOpen] = useState(false);
  const [editingStudent, setEditingStudent] = useState(null);
  
  const { data: students = [], isLoading, refetch, error: studentsError } = useGetStudentsQuery();
  const { data: classrooms = [], error: classroomsError } = useGetClassroomsQuery();

  console.log('📚 StudentsPage - Students:', students);
  console.log('🏫 StudentsPage - Classrooms:', classrooms);
  console.log('❌ StudentsPage - Errors:', { studentsError, classroomsError });
  const [createStudent] = useCreateStudentMutation();
  const [updateStudent] = useUpdateStudentMutation();
  const [toggleStudent] = useToggleStudentMutation();

  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors },
  } = useForm({
    resolver: yupResolver(schema),
  });

  const handleOpen = (student = null) => {
    setEditingStudent(student);
    if (student) {
      reset({
        ...student,
        dateOfBirth: student.dateOfBirth ? new Date(student.dateOfBirth).toISOString().split('T')[0] : '',
      });
    } else {
      reset();
    }
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
    setEditingStudent(null);
    reset();
  };

  const onSubmit = async (data) => {
    try {
      if (editingStudent) {
        await updateStudent({ id: editingStudent.id, ...data }).unwrap();
        toast.success('Student updated successfully');
      } else {
        await createStudent(data).unwrap();
        toast.success('Student created successfully');
      }
      handleClose();
      refetch();
    } catch (error) {
      toast.error('Operation failed');
    }
  };

  const handleToggle = async (id, isActive) => {
    try {
      await toggleStudent({ id, isActive }).unwrap();
      toast.success(`Student ${isActive ? 'activated' : 'deactivated'} successfully`);
      refetch();
    } catch (error) {
      toast.error('Toggle failed');
    }
  };

  const getClassroomName = (classroomId) => {
    const classroom = classrooms.find(c => c.id === classroomId);
    return classroom ? classroom.className : 'Unknown';
  };

  const columns = [
    {
      field: 'studentId',
      headerName: 'Student ID',
      width: 120,
    },
    {
      field: 'fullName',
      headerName: 'Full Name',
      width: 200,
      renderCell: (params) => (
        <Box sx={{ display: 'flex', alignItems: 'center' }}>
          <Person sx={{ mr: 1, color: '#1976d2' }} />
          {params.row.fullName || `${params.row.firstName} ${params.row.lastName}`}
        </Box>
      ),
    },
    {
      field: 'email',
      headerName: 'Email',
      width: 200,
    },
    {
      field: 'phoneNumber',
      headerName: 'Phone',
      width: 130,
    },
    {
      field: 'classroomId',
      headerName: 'Class',
      width: 120,
      renderCell: (params) => getClassroomName(params.value),
    },
    {
      field: 'gender',
      headerName: 'Gender',
      width: 100,
    },
    {
      field: 'status',
      headerName: 'Status',
      width: 100,
      renderCell: (params) => (
        <Chip
          label={params.value === '1' ? 'Active' : 'Inactive'}
          color={params.value === '1' ? 'success' : 'error'}
          size="small"
        />
      ),
    },
    {
      field: 'actions',
      headerName: 'Actions',
      width: 180,
      sortable: false,
      renderCell: (params) => (
        <Box>
          <Tooltip title="View">
            <IconButton size="small">
              <Visibility />
            </IconButton>
          </Tooltip>
          <Tooltip title="Edit">
            <IconButton size="small" onClick={() => handleOpen(params.row)}>
              <Edit />
            </IconButton>
          </Tooltip>
          <Tooltip title={params.row.status === '1' ? 'Deactivate' : 'Activate'}>
            <IconButton 
              size="small" 
              onClick={() => handleToggle(params.row.id, params.row.status !== '1')}
            >
              {params.row.status === '1' ? <ToggleOn color="success" /> : <ToggleOff />}
            </IconButton>
          </Tooltip>
        </Box>
      ),
    },
  ];

  return (
    <Box>
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box>
          <Typography variant="h4" component="h1" gutterBottom>
            Students Management
          </Typography>
          <Typography variant="body1" color="text.secondary">
            Manage student records and information
          </Typography>
        </Box>
        <Button
          variant="contained"
          startIcon={<Add />}
          onClick={() => handleOpen()}
          sx={{
            background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
            '&:hover': {
              background: 'linear-gradient(135deg, #5a6fd8 0%, #6a4190 100%)',
            },
          }}
        >
          Add Student
        </Button>
      </Box>

      {/* Data Grid */}
      <Paper sx={{ height: 600, width: '100%' }}>
        <DataGrid
          rows={students && Array.isArray(students) ? students : []}
          columns={columns}
          loading={isLoading}
          pageSizeOptions={[10, 25, 50]}
          initialState={{
            pagination: {
              paginationModel: { pageSize: 10 },
            },
          }}
          disableRowSelectionOnClick
          sx={{
            '& .MuiDataGrid-cell:hover': {
              color: 'primary.main',
            },
          }}
        />
      </Paper>

      {/* Add/Edit Dialog */}
      <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
        <form onSubmit={handleSubmit(onSubmit)}>
          <DialogTitle>
            {editingStudent ? 'Edit Student' : 'Add New Student'}
          </DialogTitle>
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
                  label="Email"
                  type="email"
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
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('dateOfBirth')}
                  fullWidth
                  label="Date of Birth"
                  type="date"
                  InputLabelProps={{ shrink: true }}
                  error={!!errors.dateOfBirth}
                  helperText={errors.dateOfBirth?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControl fullWidth error={!!errors.gender}>
                  <InputLabel>Gender</InputLabel>
                  <Controller
                    name="gender"
                    control={control}
                    render={({ field }) => (
                      <Select {...field} label="Gender">
                        <MenuItem value="MALE">Male</MenuItem>
                        <MenuItem value="FEMALE">Female</MenuItem>
                        <MenuItem value="OTHER">Other</MenuItem>
                      </Select>
                    )}
                  />
                  {errors.gender && (
                    <Typography variant="caption" color="error" sx={{ mt: 1, ml: 2 }}>
                      {errors.gender?.message}
                    </Typography>
                  )}
                </FormControl>
              </Grid>
              <Grid item xs={12}>
                <FormControl fullWidth error={!!errors.classroomId}>
                  <InputLabel>Classroom</InputLabel>
                  <Controller
                    name="classroomId"
                    control={control}
                    render={({ field }) => (
                      <Select {...field} label="Classroom">
                        {classrooms.map((classroom) => (
                          <MenuItem key={classroom.id} value={classroom.id}>
                            {classroom.className}
                          </MenuItem>
                        ))}
                      </Select>
                    )}
                  />
                  {errors.classroomId && (
                    <Typography variant="caption" color="error" sx={{ mt: 1, ml: 2 }}>
                      {errors.classroomId?.message}
                    </Typography>
                  )}
                </FormControl>
              </Grid>
            </Grid>
          </DialogContent>
          <DialogActions>
            <Button onClick={handleClose}>Cancel</Button>
            <Button type="submit" variant="contained">
              {editingStudent ? 'Update' : 'Create'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>
    </Box>
  );
};

export default StudentsPage;
