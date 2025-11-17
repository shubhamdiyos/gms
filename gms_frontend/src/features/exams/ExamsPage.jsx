import { useState } from 'react';
import {
  Box,
  Typography,
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
  Button,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
} from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import {
  Add,
  Edit,
  Delete,
  Visibility,
  Assignment,
} from '@mui/icons-material';
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import {
  useGetExamsQuery,
  useCreateExamMutation,
  useUpdateExamMutation,
  useDeleteExamMutation,
} from '../../store/api/apiSlice';
import { toast } from 'react-toastify';
import dayjs from 'dayjs';

const EXAM_TYPES = ['UNIT_TEST', 'MID_TERM', 'HALF_YEARLY', 'ANNUAL', 'FINAL'];

const schema = yup.object({
  name: yup.string().required('Exam name is required'),
  description: yup.string(),
  examType: yup.string().required('Exam type is required'),
  academicYear: yup.string().required('Academic year is required'),
  examDate: yup.string().required('Exam date is required'),
  maxMarks: yup
    .number()
    .typeError('Max marks must be a number')
    .positive('Max marks must be positive')
    .required('Max marks are required'),
  passingMarks: yup
    .number()
    .typeError('Passing marks must be a number')
    .positive('Passing marks must be positive')
    .required('Passing marks are required'),
  durationMinutes: yup
    .number()
    .typeError('Duration must be a number')
    .positive('Duration must be positive')
    .required('Duration is required'),
});

const ExamsPage = () => {
  const [open, setOpen] = useState(false);
  const [editingExam, setEditingExam] = useState(null);

  const {
    data: exams = [],
    isLoading,
    refetch,
  } = useGetExamsQuery();

  const [createExam] = useCreateExamMutation();
  const [updateExam] = useUpdateExamMutation();
  const [deleteExam] = useDeleteExamMutation();

  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors },
  } = useForm({
    resolver: yupResolver(schema),
  });

  const handleOpen = (exam = null) => {
    setEditingExam(exam);
    if (exam) {
      reset({
        ...exam,
        examDate: exam.examDate ? dayjs(exam.examDate).format('YYYY-MM-DD') : '',
      });
    } else {
      reset({
        name: '',
        description: '',
        examType: 'UNIT_TEST',
        academicYear: '',
        examDate: dayjs().format('YYYY-MM-DD'),
        maxMarks: '',
        passingMarks: '',
        durationMinutes: '',
      });
    }
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
    setEditingExam(null);
  };

  const onSubmit = async (data) => {
    try {
      const payload = {
        ...data,
        maxMarks: Number(data.maxMarks),
        passingMarks: Number(data.passingMarks),
        durationMinutes: Number(data.durationMinutes),
      };

      if (editingExam) {
        await updateExam({ id: editingExam.id, ...payload }).unwrap();
        toast.success('Exam updated successfully');
      } else {
        await createExam(payload).unwrap();
        toast.success('Exam created successfully');
      }

      handleClose();
      refetch();
    } catch (error) {
      toast.error('Operation failed');
    }
  };

  const handleDelete = async (id) => {
    if (window.confirm('Are you sure you want to delete this exam?')) {
      try {
        await deleteExam(id).unwrap();
        toast.success('Exam deleted successfully');
        refetch();
      } catch (error) {
        toast.error('Delete failed');
      }
    }
  };

  const columns = [
    {
      field: 'name',
      headerName: 'Exam Name',
      width: 220,
      renderCell: (params) => (
        <Box sx={{ display: 'flex', alignItems: 'center' }}>
          <Assignment sx={{ mr: 1, color: '#1976d2' }} />
          {params.value}
        </Box>
      ),
    },
    {
      field: 'examType',
      headerName: 'Type',
      width: 130,
      renderCell: (params) => (
        <Chip
          label={params.value}
          color={params.value === 'ANNUAL' || params.value === 'FINAL' ? 'primary' : 'default'}
          size="small"
        />
      ),
    },
    {
      field: 'academicYear',
      headerName: 'Academic Year',
      width: 150,
    },
    {
      field: 'examDate',
      headerName: 'Exam Date',
      width: 140,
      renderCell: (params) => (params.value ? dayjs(params.value).format('MMM DD, YYYY') : '-'),
    },
    {
      field: 'maxMarks',
      headerName: 'Max Marks',
      width: 120,
    },
    {
      field: 'passingMarks',
      headerName: 'Pass Marks',
      width: 120,
    },
    {
      field: 'durationMinutes',
      headerName: 'Duration (min)',
      width: 130,
    },
    {
      field: 'status',
      headerName: 'Status',
      width: 110,
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
      width: 170,
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
          <Tooltip title="Delete">
            <IconButton size="small" onClick={() => handleDelete(params.row.id)}>
              <Delete />
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
            Exam Management
          </Typography>
          <Typography variant="body1" color="text.secondary">
            Manage school exams, schedules, and basic details.
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
          Add Exam
        </Button>
      </Box>

      {/* Data Grid */}
      <Paper sx={{ height: 600, width: '100%' }}>
        <DataGrid
          rows={exams}
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
          <DialogTitle>{editingExam ? 'Edit Exam' : 'Add New Exam'}</DialogTitle>
          <DialogContent>
            <Grid container spacing={2} sx={{ mt: 1 }}>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('name')}
                  fullWidth
                  label="Exam Name"
                  error={!!errors.name}
                  helperText={errors.name?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControl fullWidth error={!!errors.examType}>
                  <InputLabel>Exam Type</InputLabel>
                  <Controller
                    name="examType"
                    control={control}
                    render={({ field }) => (
                      <Select {...field} label="Exam Type">
                        {EXAM_TYPES.map((type) => (
                          <MenuItem key={type} value={type}>
                            {type}
                          </MenuItem>
                        ))}
                      </Select>
                    )}
                  />
                  {errors.examType && (
                    <Typography variant="caption" color="error" sx={{ mt: 1, ml: 2 }}>
                      {errors.examType?.message}
                    </Typography>
                  )}
                </FormControl>
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('academicYear')}
                  fullWidth
                  label="Academic Year"
                  placeholder="e.g., 2024-2025"
                  error={!!errors.academicYear}
                  helperText={errors.academicYear?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('examDate')}
                  fullWidth
                  label="Exam Date"
                  type="date"
                  InputLabelProps={{ shrink: true }}
                  error={!!errors.examDate}
                  helperText={errors.examDate?.message}
                />
              </Grid>
              <Grid item xs={12} md={4}>
                <TextField
                  {...register('maxMarks')}
                  fullWidth
                  label="Max Marks"
                  type="number"
                  error={!!errors.maxMarks}
                  helperText={errors.maxMarks?.message}
                />
              </Grid>
              <Grid item xs={12} md={4}>
                <TextField
                  {...register('passingMarks')}
                  fullWidth
                  label="Passing Marks"
                  type="number"
                  error={!!errors.passingMarks}
                  helperText={errors.passingMarks?.message}
                />
              </Grid>
              <Grid item xs={12} md={4}>
                <TextField
                  {...register('durationMinutes')}
                  fullWidth
                  label="Duration (minutes)"
                  type="number"
                  error={!!errors.durationMinutes}
                  helperText={errors.durationMinutes?.message}
                />
              </Grid>
              <Grid item xs={12}>
                <TextField
                  {...register('description')}
                  fullWidth
                  label="Description"
                  multiline
                  rows={3}
                  error={!!errors.description}
                  helperText={errors.description?.message}
                />
              </Grid>
            </Grid>
          </DialogContent>
          <DialogActions>
            <Button onClick={handleClose}>Cancel</Button>
            <Button type="submit" variant="contained">
              {editingExam ? 'Update' : 'Create'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>
    </Box>
  );
};

export default ExamsPage;
