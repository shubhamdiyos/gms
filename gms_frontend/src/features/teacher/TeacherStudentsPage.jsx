import { Box, Typography, Paper, Grid, Card, CardContent, Avatar, Chip } from '@mui/material';
import { People } from '@mui/icons-material';
import { useGetTeacherStudentsQuery } from '../../store/api/apiSlice';

const TeacherStudentsPage = () => {
  const { data: students = [], isLoading } = useGetTeacherStudentsQuery();

  return (
    <Box>
      {/* Header */}
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

        {!isLoading && (!students || students.length === 0) && (
          <Typography variant="body2" color="text.secondary">
            No students found for your classes.
          </Typography>
        )}

        {!isLoading && students && students.length > 0 && (
          <Grid container spacing={2}>
            {students.map((student) => (
              <Grid item xs={12} sm={6} md={4} key={student.id}>
                <Card>
                  <CardContent>
                    <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
                      <Avatar sx={{ bgcolor: '#1976d2', mr: 2 }}>
                        <People />
                      </Avatar>
                      <Box>
                        <Typography variant="subtitle1">
                          {student.fullName || `${student.firstName} ${student.lastName}`}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          {student.rollNumber ? `Roll: ${student.rollNumber}` : student.email}
                        </Typography>
                      </Box>
                    </Box>

                    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                      {student.className && (
                        <Chip
                          label={student.className}
                          size="small"
                          color="primary"
                          variant="outlined"
                        />
                      )}
                      {student.section && (
                        <Chip
                          label={`Section ${student.section}`}
                          size="small"
                          color="secondary"
                          variant="outlined"
                        />
                      )}
                    </Box>
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

export default TeacherStudentsPage;
