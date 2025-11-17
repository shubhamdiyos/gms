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
import { Add, Edit, Delete, Grade } from '@mui/icons-material';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import {
  useGetTeacherClassesQuery,
  useGetTeacherStudentsQuery,
  useRecordResultMutation,
  useUpdateResultMutation,
  useDeleteResultMutation,
  useGetResultsForStudentExamQuery,
} from '../../store/api/apiSlice';
import { toast } from 'react-toastify';

const schema = yup.object({
  studentId: yup
    .number()
    .typeError('Student ID must be a number')
    .integer('Student ID must be an integer')
    .positive('Student ID must be positive')
    .required('Student ID is required'),
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

const TeacherGradesPage = () => {
  const [open, setOpen] = useState(false);
  const [editingResult, setEditingResult] = useState(null);
  const [selectedClass, setSelectedClass] = useState('');
  const [selectedExam, setSelectedExam] = useState('');

  const { data: classes = [], isLoading: classesLoading } = useGetTeacherClassesQuery();
  const { data: students = [], isLoading: studentsLoading } = useGetTeacherStudentsQuery();
  const { data: results = [], isLoading: resultsLoading } = useGetResultsForStudentExamQuery(
    selectedExam || undefined,
    { skip: !selectedExam }
  );

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

  const handleClassChange = (event) => {
    setSelectedClass(event.target.value);
    setSelectedExam(''); // Reset exam when class changes
  };

  const handleExamChange = (event) => {
    setSelectedExam(event.target.value);
  };

  const handleOpen = (result = null) => {
    setEditingResult(result);
    if (result) {
      reset({
        studentId: result.studentId || '',
        obtainedMarks: result.obtainedMarks ?? '',
        grade: result.grade || '',
        percentage: result.percentage ?? '',
        remarks: result.remarks || '',
      });
    } else {
      reset({
        studentId: '',
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
        studentExamId: parseInt(selectedExam), // This would need to be calculated from student + exam
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

  // Filter students by selected class
  const filteredStudents = students.filter(student =>
    !selectedClass || student.classroomId === parseInt(selectedClass)
  );

  const columns = [
    {
      field: 'studentName',
      headerName: 'Student Name',
      width: 200,
      valueGetter: (params) => params.row.studentName || 'N/A',
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
            Grade Management
          </Typography>
          <Typography variant="body1" color="text.secondary">
            Record and manage student exam results for your classes.
          </Typography>
        </Box>
        <Button
          variant="contained"
          startIcon={<Add />}
          onClick={() => handleOpen()}
          disabled={!selectedClass || !selectedExam}
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

      {/* Filters */}
      <Paper sx={{ p: 3, mb: 3 }}>
        <Grid container spacing={3}>
          <Grid item xs={12} md={6}>
            <FormControl fullWidth>
              <InputLabel>Select Class</InputLabel>
              <Select
                value={selectedClass}
                onChange={handleClassChange}
                label="Select Class"
              >
                {classes.map((classroom) => (
                  <MenuItem key={classroom.id} value={classroom.id}>
                    {classroom.name} - {classroom.grade}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>
          <Grid item xs={12} md={6}>
            <FormControl fullWidth disabled={!selectedClass}>
              <InputLabel>Select Exam</InputLabel>
              <Select
                value={selectedExam}
                onChange={handleExamChange}
                label="Select Exam"
              >
                {/* Mock exam options - in real app, fetch from API */}
                <MenuItem value="1">Mid-term Exam 2024</MenuItem>
                <MenuItem value="2">Final Exam 2024</MenuItem>
                <MenuItem value="3">Quarterly Assessment</MenuItem>
              </Select>
            </FormControl>
          </Grid>
        </Grid>
      </Paper>

      {/* Students List */}
      {selectedClass && (
        <Paper sx={{ p: 3, mb: 3 }}>
          <Typography variant="h6" gutterBottom sx={{ display: 'flex', alignItems: 'center' }}>
            <Grade sx={{ mr: 1 }} />
            Students in Selected Class ({filteredStudents.length})
          </Typography>
          <Grid container spacing={2}>
            {filteredStudents.map((student) => (
              <Grid item xs={12} md={6} lg={4} key={student.id}>
                <Paper sx={{ p: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Box>
                    <Typography variant="subtitle2">
                      {student.firstName} {student.lastName}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      ID: {student.studentId}
                    </Typography>
                  </Box>
                  <Button
                    size="small"
                    variant="outlined"
                    onClick={() => handleOpen()}
                    disabled={!selectedExam}
                  >
                    Grade
                  </Button>
                </Paper>
              </Grid>
            ))}
          </Grid>
        </Paper>
      )}

      {/* Results Data Grid */}
      {selectedExam && (
        <Paper sx={{ height: 400, width: '100%' }}>
          <DataGrid
            rows={results}
            columns={columns}
            loading={resultsLoading}
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
      )}

      {/* Add/Edit Dialog */}
      <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
        <form onSubmit={handleSubmit(onSubmit)}>
          <DialogTitle>{editingResult ? 'Edit Result' : 'Add New Result'}</DialogTitle>
          <DialogContent>
            <Grid container spacing={2} sx={{ mt: 1 }}>
              <Grid item xs={12}>
                <FormControl fullWidth>
                  <InputLabel>Select Student</InputLabel>
                  <Select
                    {...register('studentId')}
                    value={editingResult?.studentId || ''}
                    label="Select Student"
                    error={!!errors.studentId}
                  >
                    {filteredStudents.map((student) => (
                      <MenuItem key={student.id} value={student.id}>
                        {student.firstName} {student.lastName} (ID: {student.studentId})
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
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

export default TeacherGradesPage;
