import { useMemo, useState } from 'react';
import {
  Box,
  Typography,
  Paper,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  MenuItem,
  Grid,
  Chip,
  IconButton,
  Tooltip,
  Button,
  Alert,
} from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import { Add, Edit, Delete } from '@mui/icons-material';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import {
  useGetStudentsQuery,
  useGetResultsForStudentQuery,
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
  remarks: yup.string(),
});

const ResultsPage = () => {
  const [selectedStudentId, setSelectedStudentId] = useState('');
  const [open, setOpen] = useState(false);
  const [editingResult, setEditingResult] = useState(null);

  const {
    data: students = [],
    isLoading: studentsLoading,
    error: studentsError,
  } = useGetStudentsQuery();

  const {
    data: results = [],
    isLoading,
    isError,
    error: resultsError,
  } = useGetResultsForStudentQuery(selectedStudentId, {
    skip: !selectedStudentId,
  });

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

  const studentOptions = useMemo(
    () =>
      (Array.isArray(students) ? students : [])
        .filter((s) => s && s.id != null)
        .map((s) => ({
          id: s.id,
          label: s.full_name || `${s.firstName ?? ''} ${s.lastName ?? ''}`.trim() || `Student #${s.id}`,
        })),
    [students],
  );

  const handleOpen = (result = null) => {
    setEditingResult(result);
    if (result) {
      reset({
        studentExamId: result.studentExamId ?? '',
        obtainedMarks: result.obtainedMarks ?? '',
        grade: result.grade || '',
        remarks: result.remarks || '',
      });
    } else {
      reset({
        studentExamId: '',
        obtainedMarks: '',
        grade: '',
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
    } catch {
      toast.error('Operation failed');
    }
  };

  const handleDelete = async (id) => {
    if (window.confirm('Are you sure you want to delete this result?')) {
      try {
        await deleteResult(id).unwrap();
        toast.success('Result deleted successfully');
      } catch {
        toast.error('Delete failed');
      }
    }
  };

  const columns = [
    { field: 'examName', headerName: 'Exam', minWidth: 160, flex: 1 },
    { field: 'subjectName', headerName: 'Subject', minWidth: 140, flex: 1 },
    { field: 'obtainedMarks', headerName: 'Marks Obtained', width: 130 },
    {
      field: 'grade',
      headerName: 'Grade',
      width: 100,
      renderCell: (params) => (
        <Chip
          label={params.value ?? '-'}
          color={params.value === 'A' || params.value === 'A+' ? 'success' : 'primary'}
          size="small"
        />
      ),
    },
    {
      field: 'percentage',
      headerName: 'Percentage',
      width: 130,
      renderCell: (params) => (params.value == null ? '-' : `${params.value}%`),
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
      width: 130,
      sortable: false,
      disableColumnMenu: true,
      renderCell: (params) => (
        <Box>
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
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>        <Box>
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
          disabled={!selectedStudentId}
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

      {/* Student selector */}
      <Paper sx={{ p: 2, mb: 3 }}>
        <Grid container spacing={2} alignItems="center">
          <Grid item xs={12} md={4}>
            <TextField
              select
              fullWidth
              label="Student"
              value={selectedStudentId}
              onChange={(e) => setSelectedStudentId(e.target.value)}
              SelectProps={{ native: true }}
              disabled={studentsLoading || studentOptions.length === 0}
            >
              <option value="">Select a student</option>
              {studentOptions.map((option) => (
                <option key={option.id} value={option.id}>
                  {option.label}
                </option>
              ))}
            </TextField>
          </Grid>
          <Grid item xs={12} md={8}>
            {studentsError && (
              <Alert severity="error">
                Could not load students: {studentsError.message || 'Failed to load students'}
              </Alert>
            )}
            {!studentsLoading && !studentsError && studentOptions.length === 0 && (
              <Alert severity="info">
                No students found for the current school. Add students first.
              </Alert>
            )}
          </Grid>
        </Grid>
      </Paper>

      {/* Results grid */}
      <Paper sx={{ height: 400, width: '100%' }}>
        {!selectedStudentId ? (
          <Box sx={{ p: 3 }}>
            <Typography variant="body1" color="text.secondary">
              Select a student to view their exam results.
            </Typography>
          </Box>
        ) : (
          <DataGrid
            rows={Array.isArray(results) ? results : []}
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
        )}
      </Paper>

      {isError && selectedStudentId && (
        <Alert severity="error" sx={{ mt: 2 }}>
          Could not load results: {resultsError?.message || 'Failed to load results'}
        </Alert>
      )}

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
              <Grid item xs={12}>
                <Typography variant="body2" color="text.secondary">
                  Percentage is calculated by the server from the obtained marks and the
                  subject&apos;s maximum marks.
                </Typography>
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
