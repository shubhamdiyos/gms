import { useMemo, useState } from 'react';
import {
  Box,
  Typography,
  Paper,
  Grid,
  Chip,
  Button,
  Alert,
  Tooltip,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
} from '@mui/material';
import { Add, Grade } from '@mui/icons-material';
import { DataGrid } from '@mui/x-data-grid';
import {
  useGetTeacherClassesQuery,
  useGetTeacherStudentsQuery,
  useGetResultsForStudentQuery,
} from '../../store/api/apiSlice';

const WRITE_UNAVAILABLE_REASON =
  'Result entry is admin-only: POST, PUT and DELETE /api/v1/results all require the ADMIN authority.';

const TeacherGradesPage = () => {
  const [selectedClass, setSelectedClass] = useState('');
  const [selectedStudent, setSelectedStudent] = useState('');
  const [selectedExam, setSelectedExam] = useState('');

  const { data: classes = [] } = useGetTeacherClassesQuery();
  const { data: students = [] } = useGetTeacherStudentsQuery();

  const studentId = selectedStudent === '' ? null : Number(selectedStudent);

  const { data: results = [], isLoading: resultsLoading } = useGetResultsForStudentQuery(
    studentId,
    { skip: !studentId },
  );

  const classStudents = useMemo(() => {
    if (selectedClass === '') return [];
    const classId = Number(selectedClass);
    return students.filter((student) => student.classId === classId);
  }, [students, selectedClass]);

  const examOptions = useMemo(
    () =>
      results.reduce((acc, result) => {
        if (result?.studentExamId && !acc.some((option) => option.value === result.studentExamId)) {
          acc.push({
            value: result.studentExamId,
            label: `${result.examName ?? 'Exam'} - ${result.subjectName ?? 'Subject'}`,
          });
        }
        return acc;
      }, []),
    [results],
  );

  const visibleResults = useMemo(
    () =>
      selectedExam === ''
        ? results
        : results.filter((result) => result.studentExamId === Number(selectedExam)),
    [results, selectedExam],
  );

  const handleClassChange = (event) => {
    setSelectedClass(event.target.value);
    setSelectedStudent('');
    setSelectedExam('');
  };

  const handleStudentChange = (event) => {
    setSelectedStudent(event.target.value);
    setSelectedExam('');
  };

  const columns = [
    { field: 'studentName', headerName: 'Student', width: 160 },
    { field: 'examName', headerName: 'Exam', width: 160 },
    { field: 'subjectName', headerName: 'Subject', width: 150 },
    { field: 'obtainedMarks', headerName: 'Marks', width: 100 },
    {
      field: 'grade',
      headerName: 'Grade',
      width: 100,
      renderCell: (params) => (
        <Chip
          label={params.value ?? 'N/A'}
          color={params.value === 'A' || params.value === 'A+' ? 'success' : 'primary'}
          size="small"
        />
      ),
    },
    {
      field: 'percentage',
      headerName: 'Percentage',
      width: 120,
      renderCell: (params) => `${params.value ?? 0}%`,
    },
    {
      field: 'status',
      headerName: 'Status',
      width: 120,
      renderCell: (params) => (
        <Chip
          label={params.value === '1' ? 'Published' : 'Draft'}
          color={params.value === '1' ? 'success' : 'warning'}
          size="small"
        />
      ),
    },
    { field: 'studentExamId', headerName: 'Student Exam', width: 140 },
    { field: 'remarks', headerName: 'Remarks', width: 200 },
  ];

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box>
          <Typography variant="h4" component="h1" gutterBottom>
            Grade Management
          </Typography>
          <Typography variant="body1" color="text.secondary">
            Review student exam results for your classes.
          </Typography>
        </Box>
        <Tooltip title={WRITE_UNAVAILABLE_REASON}>
          <Box>
            <Button
              variant="contained"
              startIcon={<Add />}
              disabled
              sx={{ background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' }}
            >
              Add Result
            </Button>
          </Box>
        </Tooltip>
      </Box>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Grid container spacing={3}>
          <Grid item xs={12} md={4}>
            <FormControl fullWidth>
              <InputLabel>Select Class</InputLabel>
              <Select value={selectedClass} onChange={handleClassChange} label="Select Class">
                {classes.map((classroom) => (
                  <MenuItem key={classroom.id} value={classroom.id}>
                    {classroom.name} - {classroom.grade} {classroom.section}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>
          <Grid item xs={12} md={4}>
            <FormControl fullWidth disabled={!selectedClass}>
              <InputLabel>Select Student</InputLabel>
              <Select value={selectedStudent} onChange={handleStudentChange} label="Select Student">
                {classStudents.map((student) => (
                  <MenuItem key={student.id} value={student.id}>
                    {`${student.firstName ?? ''} ${student.lastName ?? ''}`.trim()} (ID:{' '}
                    {student.studentId})
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>
          <Grid item xs={12} md={4}>
            <FormControl fullWidth disabled={!studentId || examOptions.length === 0}>
              <InputLabel>Select Exam</InputLabel>
              <Select value={selectedExam} onChange={(e) => setSelectedExam(e.target.value)} label="Select Exam">
                <MenuItem value="">All exams</MenuItem>
                {examOptions.map((option) => (
                  <MenuItem key={option.value} value={option.value}>
                    {option.label}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>
        </Grid>
      </Paper>

      {classStudents.length === 0 && selectedClass !== '' && (
        <Alert severity="info" sx={{ mb: 3 }}>
          No students are assigned to this class.
        </Alert>
      )}

      {studentId && !resultsLoading && results.length === 0 && (
        <Alert severity="info" sx={{ mb: 3 }}>
          No results recorded for this student yet.
        </Alert>
      )}

      <Alert severity="warning" sx={{ mb: 3 }}>
        {WRITE_UNAVAILABLE_REASON} Results shown here come from GET /api/v1/results/student/
        {'{studentId}'}.
      </Alert>

      {selectedClass !== '' && (
        <Paper sx={{ p: 3, mb: 3 }}>
          <Typography variant="h6" gutterBottom sx={{ display: 'flex', alignItems: 'center' }}>
            <Grade sx={{ mr: 1 }} />
            Students in Selected Class ({classStudents.length})
          </Typography>
          <Grid container spacing={2}>
            {classStudents.map((student) => (
              <Grid item xs={12} md={6} lg={4} key={student.id}>
                <Paper sx={{ p: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Box>
                    <Typography variant="subtitle2">
                      {`${student.firstName ?? ''} ${student.lastName ?? ''}`.trim()}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      ID: {student.studentId}
                    </Typography>
                  </Box>
                  <Button
                    size="small"
                    variant={studentId === student.id ? 'contained' : 'outlined'}
                    onClick={() => {
                      setSelectedStudent(student.id);
                      setSelectedExam('');
                    }}
                  >
                    View Results
                  </Button>
                </Paper>
              </Grid>
            ))}
          </Grid>
        </Paper>
      )}

      {studentId && (
        <Paper sx={{ height: 400, width: '100%' }}>
          <DataGrid
            rows={visibleResults}
            columns={columns}
            loading={resultsLoading}
            pageSizeOptions={[10, 25, 50]}
            initialState={{ pagination: { paginationModel: { pageSize: 10 } } }}
            disableRowSelectionOnClick
          />
        </Paper>
      )}
    </Box>
  );
};

export default TeacherGradesPage;
