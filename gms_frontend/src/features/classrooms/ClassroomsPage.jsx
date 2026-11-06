import { useEffect, useState } from 'react';
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
  Divider,
  Alert,
} from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import {
  Add,
  Edit,
  Delete,
  Visibility,
  Class,
  AssignmentInd,
} from '@mui/icons-material';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import {
  useGetClassroomsQuery,
  useCreateClassroomMutation,
  useUpdateClassroomMutation,
  useDeleteClassroomMutation,
  useGetEmployeesQuery,
  useGetSubjectsQuery,
  useGetAcademicYearsQuery,
  useGetClassroomAssignmentsQuery,
  useCreateTeacherAssignmentMutation,
  useDeleteTeacherAssignmentMutation,
  useGetClassroomTimetableQuery,
  useSaveClassroomTimetableMutation,
} from '../../store/api/apiSlice';
import { toast } from 'react-toastify';

const schema = yup.object({
  name: yup.string().required('Class name is required').max(100, 'Name must be at most 100 characters'),
  grade: yup.string().required('Grade is required').max(20, 'Grade must be at most 20 characters'),
  section: yup.string().required('Section is required').max(20, 'Section must be at most 20 characters'),
});

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];
const MAX_PERIODS = 12;

const ClassroomsPage = () => {
  const [open, setOpen] = useState(false);
  const [editingClassroom, setEditingClassroom] = useState(null);
  
  const { data: classrooms = [], isLoading, refetch } = useGetClassroomsQuery();
  const [createClassroom] = useCreateClassroomMutation();
  const [updateClassroom] = useUpdateClassroomMutation();
  const [deleteClassroom] = useDeleteClassroomMutation();

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm({
    resolver: yupResolver(schema),
  });

  const handleOpen = (classroom = null) => {
    setEditingClassroom(classroom);
    if (classroom) {
      reset(classroom);
    } else {
      reset();
    }
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
    setEditingClassroom(null);
    reset();
  };

  const onSubmit = async (data) => {
    try {
      if (editingClassroom) {
        await updateClassroom({ id: editingClassroom.id, ...data }).unwrap();
        toast.success('Classroom updated successfully');
      } else {
        await createClassroom(data).unwrap();
        toast.success('Classroom created successfully');
      }
      handleClose();
      refetch();
    } catch (error) {
      toast.error('Operation failed');
    }
  };

  const handleDelete = async (id) => {
    if (window.confirm('Are you sure you want to delete this classroom?')) {
      try {
        await deleteClassroom(id).unwrap();
        toast.success('Classroom deleted successfully');
        refetch();
      } catch (error) {
        toast.error('Delete failed');
      }
    }
  };

  // ---- Teaching roster & weekly timetable -------------------------------------------------
  // A teacher sees this classroom only through a TeacherAssignment, and attendance can only be
  // saved against a scheduled period, so both are managed here per classroom.
  const [roster, setRoster] = useState(null);
  const [rosterForm, setRosterForm] = useState({ teacherId: '', subjectId: '', academicYear: '' });
  const [periodForm, setPeriodForm] = useState({ assignmentId: '', dayOfWeek: 'MONDAY', period: '1' });
  const [savingRoster, setSavingRoster] = useState(false);

  const { data: employees = [] } = useGetEmployeesQuery(undefined, { skip: !roster });
  const { data: subjects = [] } = useGetSubjectsQuery(undefined, { skip: !roster });
  const { data: academicYears = [] } = useGetAcademicYearsQuery(undefined, { skip: !roster });
  const { data: assignments = [] } = useGetClassroomAssignmentsQuery(roster?.id, { skip: !roster });
  const { data: timetable } = useGetClassroomTimetableQuery(roster?.id, { skip: !roster });
  const [createAssignment] = useCreateTeacherAssignmentMutation();
  const [deleteAssignment] = useDeleteTeacherAssignmentMutation();
  const [saveTimetable] = useSaveClassroomTimetableMutation();

  const slots = Array.isArray(timetable?.slots) ? timetable.slots : [];
  const teachers = employees.filter(
    (employee) => employee.teaching === true || employee.designation === 'TEACHER'
  );
  const teacherName = (employee) => employee.fullName || employee.full_name || 'Teacher';
  const assignmentLabel = (assignment) =>
    `${assignment.teacher?.fullName || 'Teacher'} · ${assignment.subject?.name || 'Subject'} · ${assignment.academicYear}`;
  const periodPayload = (slot) => ({
    assignmentId: slot.assignment?.id,
    dayOfWeek: slot.dayOfWeek,
    period: slot.period,
  });

  const openRoster = (classroom) => {
    const currentYear =
      academicYears.find((year) => year.current)?.name || academicYears[0]?.name || '';
    setRosterForm({ teacherId: '', subjectId: '', academicYear: currentYear });
    setPeriodForm({ assignmentId: '', dayOfWeek: 'MONDAY', period: '1' });
    setRoster(classroom);
  };

  // The years query starts only once the dialog is open, so the first render after opening has no
  // list to choose from — fill the field as soon as the data lands, without overwriting typing.
  useEffect(() => {
    if (!roster || academicYears.length === 0) return;
    const currentYear = academicYears.find((year) => year.current)?.name || academicYears[0]?.name;
    if (!currentYear) return;
    setRosterForm((prev) =>
      prev.academicYear ? prev : { ...prev, academicYear: currentYear }
    );
  }, [roster, academicYears]);

  const handleAddAssignment = async () => {
    const { teacherId, subjectId, academicYear } = rosterForm;
    if (!teacherId || !subjectId || !academicYear.trim()) {
      toast.error('Choose a teacher and a subject, and name the academic year');
      return;
    }
    setSavingRoster(true);
    try {
      await createAssignment({
        teacherId: Number(teacherId),
        subjectId: Number(subjectId),
        classroomId: roster.id,
        academicYear: academicYear.trim(),
      }).unwrap();
      toast.success('Teacher assigned');
      setRosterForm((prev) => ({ ...prev, teacherId: '', subjectId: '' }));
    } catch (err) {
      toast.error(err?.data?.message || 'Could not assign the teacher');
    } finally {
      setSavingRoster(false);
    }
  };

  const handleDeleteAssignment = async (assignment) => {
    if (
      !window.confirm(
        `Remove ${assignmentLabel(assignment)}? Any periods scheduled for it are removed too.`
      )
    ) {
      return;
    }
    try {
      if (slots.some((slot) => slot.assignment?.id === assignment.id)) {
        await saveTimetable({
          classroomId: roster.id,
          academicYear: timetable?.academicYear || assignment.academicYear,
          slots: slots
            .filter((slot) => slot.assignment?.id !== assignment.id)
            .map(periodPayload),
        }).unwrap();
      }
      await deleteAssignment(assignment.id).unwrap();
      toast.success('Assignment removed');
    } catch (err) {
      toast.error(err?.data?.message || 'Could not remove the assignment');
    }
  };

  const handleAddPeriod = async () => {
    const assignment = assignments.find(
      (row) => String(row.id) === String(periodForm.assignmentId)
    );
    const period = Number(periodForm.period);
    if (!assignment) {
      toast.error('Choose which assignment the period belongs to');
      return;
    }
    if (!Number.isInteger(period) || period < 1 || period > MAX_PERIODS) {
      toast.error(`Period must be a whole number from 1 to ${MAX_PERIODS}`);
      return;
    }
    if (slots.some((slot) => slot.dayOfWeek === periodForm.dayOfWeek && slot.period === period)) {
      toast.error('That day and period is already scheduled');
      return;
    }
    setSavingRoster(true);
    try {
      await saveTimetable({
        classroomId: roster.id,
        academicYear: assignment.academicYear,
        slots: [...slots.map(periodPayload), { assignmentId: assignment.id, dayOfWeek: periodForm.dayOfWeek, period }],
      }).unwrap();
      toast.success('Period scheduled');
    } catch (err) {
      toast.error(err?.data?.message || 'Could not save the timetable');
    } finally {
      setSavingRoster(false);
    }
  };

  const handleRemovePeriod = async (slot) => {
    try {
      await saveTimetable({
        classroomId: roster.id,
        academicYear: timetable?.academicYear || slot.assignment?.academicYear,
        slots: slots.filter((row) => row.id !== slot.id).map(periodPayload),
      }).unwrap();
      toast.success('Period removed');
    } catch (err) {
      toast.error(err?.data?.message || 'Could not update the timetable');
    }
  };

  const columns = [
    {
      field: 'name',
      headerName: 'Class Name',
      width: 150,
      renderCell: (params) => (
        <Box sx={{ display: 'flex', alignItems: 'center' }}>
          <Class sx={{ mr: 1, color: '#f57c00' }} />
          {params.value}
        </Box>
      ),
    },
    {
      field: 'grade',
      headerName: 'Grade',
      width: 100,
    },
    {
      field: 'section',
      headerName: 'Section',
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
      width: 190,
      sortable: false,
      renderCell: (params) => (
        <Box>
          <Tooltip title="Teaching roster & timetable">
            <IconButton size="small" onClick={() => openRoster(params.row)}>
              <AssignmentInd />
            </IconButton>
          </Tooltip>
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
            Classrooms Management
          </Typography>
          <Typography variant="body1" color="text.secondary">
            Manage classroom setup and capacity
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
          Add Classroom
        </Button>
      </Box>

      {/* Data Grid */}
      <Paper sx={{ height: 600, width: '100%' }}>
        <DataGrid
          rows={classrooms}
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
          <DialogTitle>
            {editingClassroom ? 'Edit Classroom' : 'Add New Classroom'}
          </DialogTitle>
          <DialogContent>
            <Grid container spacing={2} sx={{ mt: 1 }}>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('name')}
                  fullWidth
                  label="Class Name"
                  error={!!errors.name}
                  helperText={errors.name?.message}
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
                  {...register('section')}
                  fullWidth
                  label="Section"
                  error={!!errors.section}
                  helperText={errors.section?.message}
                />
              </Grid>
            </Grid>
          </DialogContent>
          <DialogActions>
            <Button onClick={handleClose}>Cancel</Button>
            <Button type="submit" variant="contained">
              {editingClassroom ? 'Update' : 'Create'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>

      {/* Teaching roster: the assignments and scheduled periods that make a classroom visible to
          its teachers and make an attendance register possible. */}
      <Dialog open={Boolean(roster)} onClose={() => setRoster(null)} maxWidth="md" fullWidth>
        <DialogTitle>Teaching roster — {roster?.name}</DialogTitle>
        <DialogContent dividers>
          <Typography variant="subtitle2" gutterBottom>
            Assignments
          </Typography>
          {assignments.length === 0 ? (
            <Alert severity="info" sx={{ mb: 2 }}>
              No teacher takes this class yet. Without an assignment the class is invisible to the
              teacher portal.
            </Alert>
          ) : (
            <Box sx={{ mb: 2 }}>
              {assignments.map((assignment) => (
                <Box key={assignment.id} sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                  <Chip label={assignmentLabel(assignment)} />
                  <Button size="small" color="error" onClick={() => handleDeleteAssignment(assignment)}>
                    Remove
                  </Button>
                </Box>
              ))}
            </Box>
          )}

          <Grid container spacing={2} alignItems="center">
            <Grid item xs={12} md={4}>
              <FormControl fullWidth size="small">
                <InputLabel id="roster-teacher-label">Teacher</InputLabel>
                <Select
                  labelId="roster-teacher-label"
                  label="Teacher"
                  value={rosterForm.teacherId}
                  onChange={(event) => setRosterForm((prev) => ({ ...prev, teacherId: event.target.value }))}
                >
                  {teachers.map((employee) => (
                    <MenuItem key={employee.id} value={employee.id}>
                      {teacherName(employee)}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12} md={4}>
              <FormControl fullWidth size="small">
                <InputLabel id="roster-subject-label">Subject</InputLabel>
                <Select
                  labelId="roster-subject-label"
                  label="Subject"
                  value={rosterForm.subjectId}
                  onChange={(event) => setRosterForm((prev) => ({ ...prev, subjectId: event.target.value }))}
                >
                  {subjects.map((subject) => (
                    <MenuItem key={subject.id} value={subject.id}>
                      {subject.name}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12} md={4}>
              <TextField
                fullWidth
                size="small"
                label="Academic year"
                value={rosterForm.academicYear}
                onChange={(event) => setRosterForm((prev) => ({ ...prev, academicYear: event.target.value }))}
              />
            </Grid>
            <Grid item xs={12}>
              <Button variant="outlined" onClick={handleAddAssignment} disabled={savingRoster}>
                Add assignment
              </Button>
            </Grid>
          </Grid>

          <Divider sx={{ my: 3 }} />

          <Typography variant="subtitle2" gutterBottom>
            Weekly periods
          </Typography>
          <Typography variant="caption" color="text.secondary">
            A teacher can only save an attendance register for a scheduled period.
          </Typography>
          {slots.length === 0 ? (
            <Alert severity="info" sx={{ mt: 1, mb: 2 }}>
              No periods scheduled for this class.
            </Alert>
          ) : (
            <Box sx={{ mt: 1, mb: 2 }}>
              {slots
                .slice()
                .sort((a, b) => a.dayOfWeek.localeCompare(b.dayOfWeek) || a.period - b.period)
                .map((slot) => (
                  <Box key={slot.id} sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                    <Chip
                      size="small"
                      variant="outlined"
                      label={`${slot.dayOfWeek} · Period ${slot.period} · ${slot.assignment?.subject?.name || 'Subject'} · ${
                        slot.assignment?.teacher?.fullName || 'Teacher'
                      }`}
                    />
                    <Button size="small" color="error" onClick={() => handleRemovePeriod(slot)}>
                      Remove
                    </Button>
                  </Box>
                ))}
            </Box>
          )}

          <Grid container spacing={2} alignItems="center">
            <Grid item xs={12} md={5}>
              <FormControl fullWidth size="small" disabled={assignments.length === 0}>
                <InputLabel id="period-assignment-label">Assignment</InputLabel>
                <Select
                  labelId="period-assignment-label"
                  label="Assignment"
                  value={periodForm.assignmentId}
                  onChange={(event) => setPeriodForm((prev) => ({ ...prev, assignmentId: event.target.value }))}
                >
                  {assignments.map((assignment) => (
                    <MenuItem key={assignment.id} value={assignment.id}>
                      {assignmentLabel(assignment)}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={6} md={2}>
              <FormControl fullWidth size="small">
                <InputLabel id="period-day-label">Day</InputLabel>
                <Select
                  labelId="period-day-label"
                  label="Day"
                  value={periodForm.dayOfWeek}
                  onChange={(event) => setPeriodForm((prev) => ({ ...prev, dayOfWeek: event.target.value }))}
                >
                  {DAYS.map((day) => (
                    <MenuItem key={day} value={day}>
                      {day}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={6} md={2}>
              <TextField
                fullWidth
                size="small"
                type="number"
                label="Period"
                inputProps={{ min: 1, max: MAX_PERIODS }}
                value={periodForm.period}
                onChange={(event) => setPeriodForm((prev) => ({ ...prev, period: event.target.value }))}
              />
            </Grid>
            <Grid item xs={12} md={3}>
              <Button
                variant="contained"
                onClick={handleAddPeriod}
                disabled={savingRoster || assignments.length === 0}
              >
                Add period
              </Button>
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRoster(null)}>Done</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default ClassroomsPage;
