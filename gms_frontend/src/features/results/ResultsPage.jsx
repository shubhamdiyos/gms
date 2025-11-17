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
} from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import { Add, Edit, Delete, Visibility, Grade } from '@mui/icons-material';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import {
  useGetResultsQuery,
  useRecordResultMutation,
  useUpdateResultMutation,
  useDeleteResultMutation,
} from '../../store/api/apiSlice';
import { toast } from 'react-toastify';

const schema = yup.object({
  studentExamId: yup
    .number()
    .typeError('Student Exam ID must be a number')
    .integer('Student Exam ID must be an integer')
    .positive('Student Exam ID must be positive')
    .required('Student Exam ID is required'),
  obtainedMarks: yup
    .number()
    .typeError('Obtained marks must be a number')
    .min(0, 'Obtained marks cannot be negative')
    .required('Obtained marks are required'),
  grade: yup.string().required('Grade is required'),
  percentage: yup
    .number()
    .typeError('Percentage must be a number')
    .min(0, 'Percentage cannot be negative')
    .max(100, 'Percentage cannot exceed 100')
    .required('Percentage is required'),
  remarks: yup.string(),
});

const ResultsPage = () => {
  const [open, setOpen] = useState(false);
  const [editingResult, setEditingResult] = useState(null);

  const {
    data: results = [],
    isLoading,
    refetch,
  } = useGetResultsQuery(undefined, { skip: true });

  const [recordResult] = useRecordResultMutation();
  const [updateResult] = useUpdateResultMutation();
  const [deleteResult] = useDeleteResultMutation();

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm({
    resolver: yupResolver(schema),
  });

  const handleOpen = (result = null) => {
    setEditingResult(result);
    if (result) {
      reset({
        studentExamId: result.studentExam?.id || result.studentExamId || '',
        obtainedMarks: result.obtainedMarks ?? '',
        grade: result.grade || '',
        percentage: result.percentage ?? '',
        remarks: result.remarks || '',
      });
    } else {
      reset({
        studentExamId: '',
        obtainedMarks: '',
        grade: '',
        percentage: '',
        remarks: '',
      });
    }
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
    setEditingResult(null);
  };

  const onSubmit = async (data) => {
    try {
      const payload = {
        studentExamId: Number(data.studentExamId),
        obtainedMarks: Number(data.obtainedMarks),
        grade: data.grade,
        percentage: data.percentage,
        remarks: data.remarks,
      };

      if (editingResult) {
        await updateResult({ id: editingResult.id, ...payload }).unwrap();
        toast.success('Result updated successfully');
      } else {
        await recordResult(payload).unwrap();
        toast.success('Result recorded successfully');
      }

      handleClose();
      // Note: getResults is currently reserved; for now we rely on other pages to view results.
    } catch (error) {
      toast.error('Operation failed');
    }
  };

  const handleDelete = async (id) => {
    if (window.confirm('Are you sure you want to delete this result?')) {
      try {
        await deleteResult(id).unwrap();
        toast.success('Result deleted successfully');
      } catch (error) {
        toast.error('Delete failed');
      }
    }
  };

  // Placeholder rows until a list endpoint is implemented; CRUD is wired already.
  const rows = results || [];

  const columns = [
    {
      field: 'studentExamId',
      headerName: 'Student Exam ID',
      width: 150,
      valueGetter: (params) => params.row.studentExamId || params.row.studentExam?.id,
    },
    {
      field: 'obtainedMarks',
      headerName: 'Marks',
      width: 100,
    },
    {
      field: 'grade',
      headerName: 'Grade',
      width: 100,
      renderCell: (params) => (
        <Chip
          label={params.value}
          color={params.value === 'A' || params.value === 'A+' ? 'success' : 'primary'}
          size="small"
        />
      ),
    },
    {
      field: 'percentage',
      headerName: 'Percentage',
      width: 130,
      renderCell: (params) => `${params.value ?? 0}%`,
    },
    {
      field: 'status',
      headerName: 'Status',
      width: 110,
      renderCell: (params) => (
        <Chip
          label={params.value === '1' ? 'Published' : 'Draft'}
          color={params.value === '1' ? 'success' : 'warning'}
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
            Result Management
          </Typography>
          <Typography variant="body1" color="text.secondary">
            Record and manage exam results for students.
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
          Add Result
        </Button>
      </Box>

      {/* Data Grid (placeholder until list endpoint is implemented) */}
      <Paper sx={{ height: 400, width: '100%' }}>
        <DataGrid
          rows={rows}
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
      <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
        <form onSubmit={handleSubmit(onSubmit)}>
          <DialogTitle>{editingResult ? 'Edit Result' : 'Add New Result'}</DialogTitle>
          <DialogContent>
            <Grid container spacing={2} sx={{ mt: 1 }}>
              <Grid item xs={12}>
                <TextField
                  {...register('studentExamId')}
                  fullWidth
                  label="Student Exam ID"
                  type="number"
                  error={!!errors.studentExamId}
                  helperText={errors.studentExamId?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('obtainedMarks')}
                  fullWidth
                  label="Obtained Marks"
                  type="number"
                  error={!!errors.obtainedMarks}
                  helperText={errors.obtainedMarks?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('grade')}
                  fullWidth
                  label="Grade"
                  error={!!errors.grade}
                  helperText={errors.grade?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('percentage')}
                  fullWidth
                  label="Percentage"
                  type="number"
                  error={!!errors.percentage}
                  helperText={errors.percentage?.message}
                />
              </Grid>
              <Grid item xs={12}>
                <TextField
                  {...register('remarks')}
                  fullWidth
                  label="Remarks"
                  multiline
                  rows={3}
                  error={!!errors.remarks}
                  helperText={errors.remarks?.message}
                />
              </Grid>
            </Grid>
          </DialogContent>
          <DialogActions>
            <Button onClick={handleClose}>Cancel</Button>
            <Button type="submit" variant="contained">
              {editingResult ? 'Update' : 'Create'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>
    </Box>
  );
};

export default ResultsPage;
