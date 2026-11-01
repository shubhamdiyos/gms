import { useMemo } from 'react';
import { Box, Typography, Paper, Grid, Card, CardContent, Avatar, Chip, Divider } from '@mui/material';
import { People, Event, Person } from '@mui/icons-material';
import { useGetTeacherStudentsQuery, useGetTeacherClassesQuery } from '../../store/api/apiSlice';

const TeacherStudentsPage = () => {
  const { data: students = [], isLoading } = useGetTeacherStudentsQuery();
  const { data: classes = [] } = useGetTeacherClassesQuery();

  const classById = useMemo(
    () =>
      classes.reduce((acc, classroom) => {
        if (classroom?.id) acc[classroom.id] = classroom;
        return acc;
      }, {}),
    [classes],
  );

  return (
    <Box>
      <Box sx={{ mb: 3 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          My Students
        </Typography>
        <Typography variant="body1" color="text.secondary">
          View students assigned to your classes.
        </Typography>
      </Box>

      <Paper sx={{ p: 2 }}>
        {isLoading && (
          <Typography variant="body2" color="text.secondary">
            Loading students...
          </Typography>
        )}

        {!isLoading && students.length === 0 && (
          <Typography variant="body2" color="text.secondary">
            No students found for your classes.
          </Typography>
        )}

        {!isLoading && students.length > 0 && (
          <Grid container spacing={2}>
            {students.map((student) => {
              const classroom = classById[student.classId];
              return (
                <Grid item xs={12} sm={6} md={4} key={student.id}>
                  <Card>
                    <CardContent>
                      <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
                        <Avatar sx={{ bgcolor: '#1976d2', mr: 2 }}>
                          <People />
                        </Avatar>
                        <Box>
                          <Typography variant="subtitle1">
                            {`${student.firstName ?? ''} ${student.lastName ?? ''}`.trim()}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            {student.studentId ? `Student ID: ${student.studentId}` : student.email}
                          </Typography>
                        </Box>
                      </Box>

                      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                        {classroom?.name && (
                          <Chip label={classroom.name} size="small" color="primary" variant="outlined" />
                        )}
                        {classroom?.grade && (
                          <Chip
                            label={`Grade ${classroom.grade}`}
                            size="small"
                            color="secondary"
                            variant="outlined"
                          />
                        )}
                        {classroom?.section && (
                          <Chip
                            label={`Section ${classroom.section}`}
                            size="small"
                            color="secondary"
                            variant="outlined"
                          />
                        )}
                        <Chip
                          label={student.status === '1' ? 'Active' : 'Inactive'}
                          size="small"
                          color={student.status === '1' ? 'success' : 'default'}
                        />
                      </Box>

                      <Divider sx={{ my: 1.5 }} />

                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <Person fontSize="small" color="action" />
                        <Typography variant="body2" color="text.secondary">
                          {student.gender || 'Gender not set'}
                        </Typography>
                      </Box>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <Event fontSize="small" color="action" />
                        <Typography variant="body2" color="text.secondary">
                          {student.dateOfBirth ? `DOB: ${student.dateOfBirth}` : 'Date of birth not set'}
                        </Typography>
                      </Box>
                      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                        {student.email || 'No email'}
                        {student.phoneNumber ? ` • ${student.phoneNumber}` : ''}
                      </Typography>
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

export default TeacherStudentsPage;
