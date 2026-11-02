import { Box, Typography, Paper, Grid, Card, CardContent, Chip } from '@mui/material';
import { Schedule } from '@mui/icons-material';
import { useGetMyTimetableQuery } from '../../store/api/apiSlice';

const StudentTimetablePage = () => {
  const { data: timetables, isLoading } = useGetMyTimetableQuery();

  const daysOrder = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

  const allSlots = Array.isArray(timetables)
    ? timetables.flatMap((timetable) =>
        (timetable.slots || []).map((slot) => ({
          ...slot,
          classroomName: timetable.classroomName,
        }))
      )
    : [];

  const slotsByDay = allSlots.reduce((acc, slot) => {
    const day = slot.dayOfWeek;
    if (!acc[day]) acc[day] = [];
    acc[day].push(slot);
    return acc;
  }, {});

  return (
    <Box>
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          My Timetable
        </Typography>
        <Typography variant="body1" color="text.secondary">
          View your weekly class schedule.
        </Typography>
      </Box>

      <Paper sx={{ p: 3 }}>
        {isLoading ? (
          <Typography>Loading timetable...</Typography>
        ) : allSlots.length === 0 ? (
          <Typography color="text.secondary">No timetable assigned yet.</Typography>
        ) : (
          <Grid container spacing={3}>
            {daysOrder.map((day) => (
              <Grid item xs={12} md={4} key={day}>
                <Card>
                  <CardContent>
                    <Typography variant="h6" gutterBottom sx={{ display: 'flex', alignItems: 'center' }}>
                      <Schedule sx={{ mr: 1 }} />
                      {day.charAt(0) + day.slice(1).toLowerCase()}
                    </Typography>
                    {!slotsByDay[day] || slotsByDay[day].length === 0 ? (
                      <Typography variant="body2" color="text.secondary">
                        No classes.
                      </Typography>
                    ) : (
                      slotsByDay[day].map((slot, index) => (
                        <Box key={index} sx={{ mb: 1 }}>
                          <Typography variant="body2" fontWeight="bold">
                            Period {slot.period}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            {slot.assignment?.subject?.name || 'Subject'} • {slot.assignment?.teacher?.fullName || 'Teacher'}
                          </Typography>
                          <Chip
                            label={slot.assignment?.classroom?.name || slot.classroomName || 'Class'}
                            size="small"
                            sx={{ mt: 0.5 }}
                          />
                        </Box>
                      ))
                    )}
                  </CardContent>
                </Card>
              </Grid>
            ))}
          </Grid>
        )}
      </Paper>
    </Box>
  );
};

export default StudentTimetablePage;
