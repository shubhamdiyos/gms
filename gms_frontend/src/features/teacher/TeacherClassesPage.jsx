import { useState } from 'react';
import {
  Box,
  Typography,
  Paper,
  Card,
  CardContent,
  Grid,
  Chip,
  Button,
  Avatar,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  Divider,
} from '@mui/material';
import {
  Groups,
  Person,
  Schedule,
  Assignment,
  EventNote,
} from '@mui/icons-material';
import {
  useGetTeacherClassesQuery,
  useGetTeacherStudentsQuery,
} from '../../store/api/apiSlice';

const TeacherClassesPage = () => {
  const { data: classes = [], isLoading } = useGetTeacherClassesQuery();
  const { data: students = [] } = useGetTeacherStudentsQuery();
  const [selectedClass, setSelectedClass] = useState(null);

  const getClassStudents = (classId) => {
    return students.filter(student => student.classroomId === classId);
  };

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          My Classes
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Manage your assigned classes and students
        </Typography>
      </Box>

      <Grid container spacing={3}>
        {/* Classes List */}
        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom sx={{ display: 'flex', alignItems: 'center' }}>
              <Groups sx={{ mr: 1 }} />
              Assigned Classes
            </Typography>
            
            {isLoading ? (
              <Typography>Loading classes...</Typography>
            ) : classes.length === 0 ? (
              <Typography color="text.secondary">No classes assigned yet.</Typography>
            ) : (
              <Grid container spacing={2}>
                {classes.map((classItem) => (
                  <Grid item xs={12} key={classItem.id}>
                    <Card 
                      sx={{ 
                        cursor: 'pointer',
                        border: selectedClass?.id === classItem.id ? '2px solid #1976d2' : '1px solid #e0e0e0',
                        '&:hover': { boxShadow: 3 }
                      }}
                      onClick={() => setSelectedClass(classItem)}
                    >
                      <CardContent>
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <Box>
                            <Typography variant="h6">
                              {classItem.className || `Class ${classItem.id}`}
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                              Section: {classItem.section || 'A'}
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                              Room: {classItem.roomNumber || 'N/A'}
                            </Typography>
                          </Box>
                          <Box sx={{ textAlign: 'center' }}>
                            <Typography variant="h6" color="primary">
                              {getClassStudents(classItem.id).length}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              Students
                            </Typography>
                          </Box>
                        </Box>
                        
                        <Box sx={{ mt: 2, display: 'flex', gap: 1 }}>
                          <Chip 
                            label="Active" 
                            color="success" 
                            size="small" 
                          />
                          <Chip 
                            label={`Capacity: ${classItem.capacity || 30}`}
                            variant="outlined" 
                            size="small" 
                          />
                        </Box>
                      </CardContent>
                    </Card>
                  </Grid>
                ))}
              </Grid>
            )}
          </Paper>
        </Grid>

        {/* Class Details */}
        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom sx={{ display: 'flex', alignItems: 'center' }}>
              <Person sx={{ mr: 1 }} />
              {selectedClass ? `${selectedClass.className} Students` : 'Select a Class'}
            </Typography>
            
            {selectedClass ? (
              <Box>
                {/* Class Info */}
                <Box sx={{ mb: 3, p: 2, bgcolor: '#f5f5f5', borderRadius: 1 }}>
                  <Grid container spacing={2}>
                    <Grid item xs={6}>
                      <Typography variant="body2" color="text.secondary">Class</Typography>
                      <Typography variant="body1">{selectedClass.className}</Typography>
                    </Grid>
                    <Grid item xs={6}>
                      <Typography variant="body2" color="text.secondary">Section</Typography>
                      <Typography variant="body1">{selectedClass.section}</Typography>
                    </Grid>
                    <Grid item xs={6}>
                      <Typography variant="body2" color="text.secondary">Room</Typography>
                      <Typography variant="body1">{selectedClass.roomNumber}</Typography>
                    </Grid>
                    <Grid item xs={6}>
                      <Typography variant="body2" color="text.secondary">Capacity</Typography>
                      <Typography variant="body1">{selectedClass.capacity}</Typography>
                    </Grid>
                  </Grid>
                </Box>

                {/* Quick Actions */}
                <Box sx={{ mb: 3, display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                  <Button 
                    variant="contained" 
                    startIcon={<EventNote />}
                    size="small"
                  >
                    Mark Attendance
                  </Button>
                  <Button 
                    variant="outlined" 
                    startIcon={<Assignment />}
                    size="small"
                  >
                    Enter Grades
                  </Button>
                  <Button 
                    variant="outlined" 
                    startIcon={<Schedule />}
                    size="small"
                  >
                    View Schedule
                  </Button>
                </Box>

                {/* Students List */}
                <Divider sx={{ mb: 2 }} />
                <Typography variant="subtitle1" gutterBottom>
                  Students ({getClassStudents(selectedClass.id).length})
                </Typography>
                
                <List sx={{ maxHeight: 400, overflow: 'auto' }}>
                  {getClassStudents(selectedClass.id).map((student, index) => (
                    <ListItem key={student.id || index}>
                      <ListItemAvatar>
                        <Avatar sx={{ bgcolor: '#1976d2' }}>
                          {student.firstName?.charAt(0) || 'S'}
                        </Avatar>
                      </ListItemAvatar>
                      <ListItemText
                        primary={student.fullName || `${student.firstName} ${student.lastName}`}
                        secondary={`ID: ${student.studentId || student.id} • ${student.email || 'No email'}`}
                      />
                      <Chip 
                        label={student.status === '1' ? 'Active' : 'Inactive'}
                        color={student.status === '1' ? 'success' : 'error'}
                        size="small"
                      />
                    </ListItem>
                  ))}
                  {getClassStudents(selectedClass.id).length === 0 && (
                    <ListItem>
                      <ListItemText 
                        primary="No students enrolled"
                        secondary="Students will appear here once they are assigned to this class"
                      />
                    </ListItem>
                  )}
                </List>
              </Box>
            ) : (
              <Box sx={{ textAlign: 'center', py: 4 }}>
                <Groups sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
                <Typography variant="body1" color="text.secondary">
                  Select a class from the left to view students and class details
                </Typography>
              </Box>
            )}
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
};

export default TeacherClassesPage;
