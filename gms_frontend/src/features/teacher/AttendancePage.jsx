import { useMemo, useState } from 'react';
import { useLocation } from 'react-router-dom';
import {
  Box,
  Typography,
  Paper,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Checkbox,
  Avatar,
  Chip,
  Grid,
  Card,
  CardContent,
  Alert,
  Tooltip,
} from '@mui/material';
import {
  EventNote,
  Save,
  Today,
  Groups,
  CheckCircle,
  Cancel,
} from '@mui/icons-material';
import {
  useGetTeacherClassesQuery,
  useGetTeacherStudentsQuery,
  useGetAttendanceQuery,
  useGetTeacherTimetableQuery,
  useMarkAttendanceMutation,
  useUpdateAttendanceMutation,
} from '../../store/api/apiSlice';
import dayjs from 'dayjs';
import { toast } from 'react-toastify';

const AttendancePage = () => {
  const location = useLocation();
  const [selectedClass, setSelectedClass] = useState(() =>
    location.state?.classId ? String(location.state.classId) : '',
  );
  const [selectedSlot, setSelectedSlot] = useState('');
  const [selectedDate, setSelectedDate] = useState(dayjs().format('YYYY-MM-DD'));
  const [attendanceData, setAttendanceData] = useState({});
  const [isSaving, setIsSaving] = useState(false);

  const { data: classes = [] } = useGetTeacherClassesQuery();
  const { data: students = [] } = useGetTeacherStudentsQuery();
  const { data: timetable } = useGetTeacherTimetableQuery();
  const [markAttendance] = useMarkAttendanceMutation();
  const [updateAttendance] = useUpdateAttendanceMutation();

  const { data: existingAttendance = [] } = useGetAttendanceQuery(
    { classId: selectedClass, date: selectedDate },
    { skip: !selectedClass || !selectedDate },
  );

  const selectedClassId = selectedClass === '' ? null : Number(selectedClass);
  const dayKey = dayjs(selectedDate).format('dddd').toUpperCase();

  // Attendance rows carry a timetable slot because the schema requires one, so a register only
  // exists for a scheduled period.
  const classSlots = useMemo(() => {
    const slots = timetable?.slots ?? [];
    return slots
      .filter(
        (slot) =>
          slot.assignment?.classroom?.id === selectedClassId && slot.dayOfWeek === dayKey,
      )
      .sort((a, b) => a.period - b.period);
  }, [timetable, selectedClassId, dayKey]);

  const activeSlot =
    classSlots.find((slot) => String(slot.id) === String(selectedSlot)) ?? null;

  const classStudents = useMemo(
    () => students.filter((student) => student.classId === selectedClassId),
    [students, selectedClassId],
  );

  // The classroom query returns every period marked that day, so the register must read the
  // selected slot only — otherwise the Period 1 mark shows up as saved on Period 2.
  const savedByStudent = useMemo(() => {
    if (!activeSlot) return {};
    return existingAttendance.reduce((acc, record) => {
      if (record?.student?.id && String(record?.timetableSlot?.id) === String(activeSlot.id)) {
        acc[record.student.id] = { id: record.id, status: record.status };
      }
      return acc;
    }, {});
  }, [existingAttendance, activeSlot]);

  const savedStatusByStudent = useMemo(
    () => Object.fromEntries(Object.entries(savedByStudent).map(([studentId, record]) => [studentId, record.status])),
    [savedByStudent],
  );

  const handleClassChange = (classId) => {
    setSelectedClass(classId);
    setSelectedSlot('');
    setAttendanceData({});
  };

  const handleDateChange = (date) => {
    setSelectedDate(date);
    setSelectedSlot('');
    setAttendanceData({});
  };

  const handleAttendanceChange = (studentId, status) => {
    setAttendanceData((prev) => ({
      ...prev,
      [studentId]: prev[studentId] === status ? undefined : status,
    }));
  };

  const getStatus = (studentId) =>
    attendanceData[studentId] ?? savedStatusByStudent[studentId];

  const getAttendanceStats = () => {
    const statuses = classStudents.map((student) => getStatus(student.id));
    return {
      total: classStudents.length,
      marked: statuses.filter(Boolean).length,
      present: statuses.filter((status) => status === 'PRESENT').length,
      absent: statuses.filter((status) => status === 'ABSENT').length,
    };
  };

  const stats = getAttendanceStats();
  const savedCount = classStudents.filter((student) =>
    Boolean(savedStatusByStudent[student.id]),
  ).length;

  const pendingChanges = classStudents.filter(
    (student) =>
      attendanceData[student.id] && attendanceData[student.id] !== savedStatusByStudent[student.id],
  );
  const canSave = Boolean(activeSlot) && pendingChanges.length > 0 && !isSaving;

  const handleSave = async () => {
    if (!canSave) return;
    setIsSaving(true);
    const results = await Promise.allSettled(
      pendingChanges.map((student) => {
        const payload = {
          studentId: student.id,
          date: selectedDate,
          status: attendanceData[student.id],
          timetableSlotId: activeSlot.id,
        };
        // POST refuses a register that already exists, so a correction has to go through PUT by id.
        const saved = savedByStudent[student.id];
        return saved
          ? updateAttendance({ id: saved.id, ...payload }).unwrap()
          : markAttendance(payload).unwrap();
      }),
    );
    setIsSaving(false);

    const saved = results.filter((result) => result.status === 'fulfilled').length;
    const duplicates = results.filter((result) => {
      if (result.status !== 'rejected') return false;
      const code = result.error?.status ?? result.error?.originalStatus;
      return code === 400;
    }).length;
    const failed = results.length - saved - duplicates;

    if (saved > 0) {
      toast.success(`${saved} attendance record${saved === 1 ? '' : 's'} saved.`);
    }
    if (duplicates > 0) {
      toast.info(
        `${duplicates} record${duplicates === 1 ? ' was' : 's were'} already marked for that period.`,
      );
    }
    if (failed > 0) {
      toast.error(`${failed} record${failed === 1 ? '' : 's'} could not be saved.`);
    }
    setAttendanceData({});
  };

  return (
    <Box>
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          Mark Attendance
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Record student attendance for your classes
        </Typography>
      </Box>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Grid container spacing={3} alignItems="center">
          <Grid item xs={12} md={3}>
            <FormControl fullWidth>
              {/* The label has to be tied to the control: without `labelId`/`id` the InputLabel is
                  a sibling with no association, so the combobox reaches the accessibility tree with
                  no name at all and `label="Select Class"` does not supply one either. */}
              <InputLabel id="teacher-class-label">Select Class</InputLabel>
              <Select
                labelId="teacher-class-label"
                id="teacher-class-select"
                value={selectedClass}
                onChange={(e) => handleClassChange(e.target.value)}
                label="Select Class"
              >
                {classes.map((classItem) => (
                  <MenuItem key={classItem.id} value={classItem.id}>
                    {classItem.name} - {classItem.grade} {classItem.section}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>

          <Grid item xs={12} md={3}>
            <Box
              component="input"
              type="date"
              value={selectedDate}
              onChange={(e) => handleDateChange(e.target.value)}
              sx={{
                width: '100%',
                padding: '16px',
                border: '1px solid #ccc',
                borderRadius: '4px',
                fontSize: '16px',
              }}
            />
          </Grid>

          <Grid item xs={12} md={3}>
            <FormControl fullWidth disabled={!selectedClass || classSlots.length === 0}>
              <InputLabel id="teacher-period-label">Period</InputLabel>
              <Select
                labelId="teacher-period-label"
                id="teacher-period-select"
                value={activeSlot ? String(activeSlot.id) : ''}
                onChange={(e) => setSelectedSlot(e.target.value)}
                label="Period"
              >
                {classSlots.map((slot) => (
                  <MenuItem key={slot.id} value={String(slot.id)}>
                    Period {slot.period}
                    {slot.assignment?.subject?.name ? ` - ${slot.assignment.subject.name}` : ''}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>

          <Grid item xs={12} md={3}>
            <Tooltip
              title={
                activeSlot
                  ? 'Save the statuses you changed'
                  : 'Select a class and period to record attendance'
              }
            >
              <Box>
                <Button
                  variant="contained"
                  startIcon={<Save />}
                  disabled={!canSave}
                  onClick={handleSave}
                  fullWidth
                  sx={{
                    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                  }}
                >
                  {isSaving ? 'Saving...' : 'Save Attendance'}
                </Button>
              </Box>
            </Tooltip>
          </Grid>
        </Grid>
      </Paper>

      {selectedClass && classSlots.length === 0 && (
        <Alert severity="info" sx={{ mb: 3 }}>
          No periods are scheduled for this class on {dayjs(selectedDate).format('dddd')}. Attendance
          is recorded against a timetable slot, so pick a day with lessons or ask an administrator to
          publish the timetable.
        </Alert>
      )}

      {selectedClass && (
        <Grid container spacing={2} sx={{ mb: 3 }}>
          <Grid item xs={6} md={3}>
            <Card>
              <CardContent sx={{ textAlign: 'center' }}>
                <Groups sx={{ fontSize: 40, color: '#1976d2', mb: 1 }} />
                <Typography variant="h6">{stats.total}</Typography>
                <Typography variant="body2" color="text.secondary">
                  Total Students
                </Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={6} md={3}>
            <Card>
              <CardContent sx={{ textAlign: 'center' }}>
                <EventNote sx={{ fontSize: 40, color: '#f57c00', mb: 1 }} />
                <Typography variant="h6">{stats.marked}</Typography>
                <Typography variant="body2" color="text.secondary">
                  Marked
                </Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={6} md={3}>
            <Card>
              <CardContent sx={{ textAlign: 'center' }}>
                <CheckCircle sx={{ fontSize: 40, color: '#4caf50', mb: 1 }} />
                <Typography variant="h6">{stats.present}</Typography>
                <Typography variant="body2" color="text.secondary">
                  Present
                </Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={6} md={3}>
            <Card>
              <CardContent sx={{ textAlign: 'center' }}>
                <Cancel sx={{ fontSize: 40, color: '#f44336', mb: 1 }} />
                <Typography variant="h6">{stats.absent}</Typography>
                <Typography variant="body2" color="text.secondary">
                  Absent
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      )}

      {selectedClass ? (
        <Paper>
          <Box sx={{ p: 2, borderBottom: '1px solid #e0e0e0' }}>
            <Typography variant="h6" sx={{ display: 'flex', alignItems: 'center' }}>
              <Today sx={{ mr: 1 }} />
              Attendance for {dayjs(selectedDate).format('MMMM DD, YYYY')}
            </Typography>
          </Box>

          {classStudents.length === 0 ? (
            <Box sx={{ p: 4, textAlign: 'center' }}>
              <Typography variant="body1" color="text.secondary">
                No students found in the selected class
              </Typography>
            </Box>
          ) : (
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Student</TableCell>
                    <TableCell>Student ID</TableCell>
                    <TableCell align="center">Present</TableCell>
                    <TableCell align="center">Absent</TableCell>
                    <TableCell align="center">Status</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {classStudents.map((student) => {
                    const attendanceStatus = getStatus(student.id);
                    const isSaved = Boolean(savedStatusByStudent[student.id]);
                    return (
                      <TableRow key={student.id}>
                        <TableCell>
                          <Box sx={{ display: 'flex', alignItems: 'center' }}>
                            <Avatar sx={{ mr: 2, bgcolor: '#1976d2' }}>
                              {student.firstName?.charAt(0) || 'S'}
                            </Avatar>
                            <Box>
                              <Typography variant="body1">
                                {`${student.firstName ?? ''} ${student.lastName ?? ''}`.trim()}
                              </Typography>
                              <Typography variant="body2" color="text.secondary">
                                {student.email}
                              </Typography>
                            </Box>
                          </Box>
                        </TableCell>
                        <TableCell>
                          <Chip
                            label={student.studentId || student.id}
                            variant="outlined"
                            size="small"
                          />
                        </TableCell>
                        <TableCell align="center">
                          <Checkbox
                            checked={attendanceStatus === 'PRESENT'}
                            onChange={() => handleAttendanceChange(student.id, 'PRESENT')}
                            color="success"
                          />
                        </TableCell>
                        <TableCell align="center">
                          <Checkbox
                            checked={attendanceStatus === 'ABSENT'}
                            onChange={() => handleAttendanceChange(student.id, 'ABSENT')}
                            color="error"
                          />
                        </TableCell>
                        <TableCell align="center">
                          {attendanceStatus ? (
                            <Chip
                              label={
                                isSaved && attendanceStatus === savedStatusByStudent[student.id]
                                  ? `${attendanceStatus} (saved)`
                                  : isSaved
                                    ? `${attendanceStatus} (unsaved)`
                                    : attendanceStatus
                              }
                              color={attendanceStatus === 'PRESENT' ? 'success' : 'error'}
                              size="small"
                            />
                          ) : (
                            <Chip label="Not Marked" variant="outlined" size="small" />
                          )}
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Paper>
      ) : (
        <Paper sx={{ p: 4, textAlign: 'center' }}>
          <EventNote sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
          <Typography variant="h6" color="text.secondary" gutterBottom>
            Select a Class to Mark Attendance
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Choose a class from the dropdown above to start marking attendance
          </Typography>
        </Paper>
      )}

      {savedCount > 0 && (
        <Alert severity="info" sx={{ mt: 2 }}>
          {savedCount} attendance record
          {savedCount === 1 ? ' is' : 's are'} already saved for this class on{' '}
          {dayjs(selectedDate).format('MMMM DD, YYYY')}.
        </Alert>
      )}
    </Box>
  );
};

export default AttendancePage;
