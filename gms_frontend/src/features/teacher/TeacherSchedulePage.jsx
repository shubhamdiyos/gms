import { useMemo } from 'react';
import { Box, Typography, Paper, Grid, Card, CardContent, Chip } from '@mui/material';
import { Schedule } from '@mui/icons-material';
import { useGetTeacherTimetableQuery } from '../../store/api/apiSlice';

const daysOrder = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

const formatDay = (day) => {
  if (!day) return '';
  return day.charAt(0) + day.slice(1).toLowerCase();
};

const TeacherSchedulePage = () => {
  const { data: timetable, isLoading } = useGetTeacherTimetableQuery();

  const slots = useMemo(() => timetable?.slots ?? [], [timetable]);

  const slotsByDay = useMemo(
    () =>
      slots.reduce((acc, slot) => {
        const day = slot?.dayOfWeek;
        if (!day) return acc;
        if (!acc[day]) acc[day] = [];
        acc[day].push(slot);
        return acc;
      }, {}),
    [slots],
  );

  return (
    <Box>
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          My Schedule
        </Typography>
        <Typography variant="body1" color="text.secondary">
          View your weekly teaching schedule.
        </Typography>
      </Box>

      <Paper sx={{ p: 3 }}>
        {isLoading ? (
          <Typography>Loading schedule...</Typography>
        ) : slots.length === 0 ? (
          <Typography color="text.secondary">
            No schedule assigned yet. GET /api/v1/timetables/teacher/me returns an empty timetable.
          </Typography>
        ) : (
          <Grid container spacing={3}>
            {daysOrder.map((day) => {
              const daySlots = [...(slotsByDay[day] ?? [])].sort(
                (a, b) => (a.period ?? 0) - (b.period ?? 0),
              );
              return (
                <Grid item xs={12} md={4} key={day}>
                  <Card>
                    <CardContent>
                      <Typography variant="h6" gutterBottom sx={{ display: 'flex', alignItems: 'center' }}>
                        <Schedule sx={{ mr: 1 }} />
                        {formatDay(day)}
                      </Typography>
                      {daySlots.length === 0 ? (
                        <Typography variant="body2" color="text.secondary">
                          No classes.
                        </Typography>
                      ) : (
                        daySlots.map((slot, index) => (
                          <Box key={`${slot.dayOfWeek}-${slot.period}-${index}`} sx={{ mb: 1 }}>
                            <Typography variant="body2" fontWeight="bold">
                              Period {slot.period}
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                              {slot.assignment?.subject?.name || 'Subject'} •{' '}
                              {slot.assignment?.classroom
                                ? `${slot.assignment.classroom.name}${
                                    slot.assignment.classroom.grade ? ` (${slot.assignment.classroom.grade})` : ''
                                  }`
                                : 'Class'}
                            </Typography>
                            <Chip
                              label={slot.assignment?.teacher?.fullName || 'You'}
                              size="small"
                              sx={{ mt: 0.5 }}
                            />
                          </Box>
                        ))
                      )}
                    </CardContent>
                  </Card>
                </Grid>
              );
            })}
          </Grid>
        )}
      </Paper>
    </Box>
  );
};

export default TeacherSchedulePage;
