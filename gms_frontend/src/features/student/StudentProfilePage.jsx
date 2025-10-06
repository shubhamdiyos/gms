import { useState } from 'react';
import {
  Box,
  Typography,
  Paper,
  Grid,
  Avatar,
  Card,
  CardContent,
  Chip,
  Divider,
  List,
  ListItem,
  ListItemText,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
} from '@mui/material';
import {
  Person,
  Email,
  Phone,
  Cake,
  School,
  Class,
  Edit,
  Save,
} from '@mui/icons-material';
import { useForm } from 'react-hook-form';
import {
  useGetStudentProfileQuery,
  useGetMyClassesQuery,
  useGetMySubjectsQuery,
  useUpdateStudentMutation,
} from '../../store/api/apiSlice';
import { toast } from 'react-toastify';
import dayjs from 'dayjs';

const StudentProfilePage = () => {
  const [editOpen, setEditOpen] = useState(false);
  
  const { data: profile, isLoading } = useGetStudentProfileQuery();
  const { data: classes = [] } = useGetMyClassesQuery();
  const { data: subjects = [] } = useGetMySubjectsQuery();
  const [updateStudent] = useUpdateStudentMutation();

  const { register, handleSubmit, reset, formState: { errors } } = useForm();

  const handleEditOpen = () => {
    if (profile) {
      reset({
        firstName: profile.firstName,
        lastName: profile.lastName,
        email: profile.email,
        phoneNumber: profile.phoneNumber,
      });
    }
    setEditOpen(true);
  };

  const handleEditClose = () => {
    setEditOpen(false);
    reset();
  };

  const onSubmit = async (data) => {
    try {
      await updateStudent({ id: profile.id, ...data }).unwrap();
      toast.success('Profile updated successfully');
      handleEditClose();
    } catch (error) {
      toast.error('Failed to update profile');
    }
  };

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 400 }}>
        <Typography>Loading profile...</Typography>
      </Box>
    );
  }

  if (!profile) {
    return (
      <Box sx={{ textAlign: 'center', py: 4 }}>
        <Typography variant="h6" color="text.secondary">
          Profile not found
        </Typography>
      </Box>
    );
  }

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          My Profile
        </Typography>
        <Typography variant="body1" color="text.secondary">
          View and manage your personal information
        </Typography>
      </Box>

      <Grid container spacing={3}>
        {/* Profile Card */}
        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 3, textAlign: 'center' }}>
            <Avatar
              sx={{
                width: 120,
                height: 120,
                mx: 'auto',
                mb: 2,
                bgcolor: '#1976d2',
                fontSize: '3rem',
              }}
            >
              {profile.firstName?.charAt(0) || 'S'}
            </Avatar>
            
            <Typography variant="h5" gutterBottom>
              {profile.fullName || `${profile.firstName} ${profile.lastName}`}
            </Typography>
            
            <Chip
              label={`Student ID: ${profile.studentId || profile.id}`}
              color="primary"
              sx={{ mb: 2 }}
            />
            
            <Box sx={{ mb: 2 }}>
              <Chip
                label={profile.status === '1' ? 'Active' : 'Inactive'}
                color={profile.status === '1' ? 'success' : 'error'}
                size="small"
              />
            </Box>

            <Button
              variant="outlined"
              startIcon={<Edit />}
              onClick={handleEditOpen}
              fullWidth
            >
              Edit Profile
            </Button>
          </Paper>
        </Grid>

        {/* Personal Information */}
        <Grid item xs={12} md={8}>
          <Paper sx={{ p: 3, mb: 3 }}>
            <Typography variant="h6" gutterBottom sx={{ display: 'flex', alignItems: 'center' }}>
              <Person sx={{ mr: 1 }} />
              Personal Information
            </Typography>
            
            <Grid container spacing={3}>
              <Grid item xs={12} md={6}>
                <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                  <Person sx={{ mr: 2, color: 'text.secondary' }} />
                  <Box>
                    <Typography variant="body2" color="text.secondary">First Name</Typography>
                    <Typography variant="body1">{profile.firstName}</Typography>
                  </Box>
                </Box>
              </Grid>
              
              <Grid item xs={12} md={6}>
                <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                  <Person sx={{ mr: 2, color: 'text.secondary' }} />
                  <Box>
                    <Typography variant="body2" color="text.secondary">Last Name</Typography>
                    <Typography variant="body1">{profile.lastName}</Typography>
                  </Box>
                </Box>
              </Grid>
              
              <Grid item xs={12} md={6}>
                <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                  <Email sx={{ mr: 2, color: 'text.secondary' }} />
                  <Box>
                    <Typography variant="body2" color="text.secondary">Email</Typography>
                    <Typography variant="body1">{profile.email}</Typography>
                  </Box>
                </Box>
              </Grid>
              
              <Grid item xs={12} md={6}>
                <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                  <Phone sx={{ mr: 2, color: 'text.secondary' }} />
                  <Box>
                    <Typography variant="body2" color="text.secondary">Phone</Typography>
                    <Typography variant="body1">{profile.phoneNumber || 'Not provided'}</Typography>
                  </Box>
                </Box>
              </Grid>
              
              <Grid item xs={12} md={6}>
                <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                  <Cake sx={{ mr: 2, color: 'text.secondary' }} />
                  <Box>
                    <Typography variant="body2" color="text.secondary">Date of Birth</Typography>
                    <Typography variant="body1">
                      {profile.dateOfBirth ? dayjs(profile.dateOfBirth).format('MMMM DD, YYYY') : 'Not provided'}
                    </Typography>
                  </Box>
                </Box>
              </Grid>
              
              <Grid item xs={12} md={6}>
                <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                  <Person sx={{ mr: 2, color: 'text.secondary' }} />
                  <Box>
                    <Typography variant="body2" color="text.secondary">Gender</Typography>
                    <Typography variant="body1">{profile.gender || 'Not specified'}</Typography>
                  </Box>
                </Box>
              </Grid>
            </Grid>
          </Paper>

          {/* Academic Information */}
          <Grid container spacing={3}>
            <Grid item xs={12} md={6}>
              <Card>
                <CardContent>
                  <Typography variant="h6" gutterBottom sx={{ display: 'flex', alignItems: 'center' }}>
                    <Class sx={{ mr: 1 }} />
                    My Classes
                  </Typography>
                  <Divider sx={{ mb: 2 }} />
                  
                  {classes.length === 0 ? (
                    <Typography color="text.secondary">No classes assigned</Typography>
                  ) : (
                    <List dense>
                      {classes.map((classItem, index) => (
                        <ListItem key={index} disablePadding>
                          <ListItemText
                            primary={classItem.className || `Class ${index + 1}`}
                            secondary={`Section: ${classItem.section || 'A'}`}
                          />
                        </ListItem>
                      ))}
                    </List>
                  )}
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} md={6}>
              <Card>
                <CardContent>
                  <Typography variant="h6" gutterBottom sx={{ display: 'flex', alignItems: 'center' }}>
                    <School sx={{ mr: 1 }} />
                    My Subjects
                  </Typography>
                  <Divider sx={{ mb: 2 }} />
                  
                  {subjects.length === 0 ? (
                    <Typography color="text.secondary">No subjects assigned</Typography>
                  ) : (
                    <List dense>
                      {subjects.map((subject, index) => (
                        <ListItem key={index} disablePadding>
                          <ListItemText
                            primary={subject.subjectName || `Subject ${index + 1}`}
                            secondary={`Code: ${subject.subjectCode || 'N/A'}`}
                          />
                        </ListItem>
                      ))}
                    </List>
                  )}
                </CardContent>
              </Card>
            </Grid>
          </Grid>
        </Grid>
      </Grid>

      {/* Edit Profile Dialog */}
      <Dialog open={editOpen} onClose={handleEditClose} maxWidth="sm" fullWidth>
        <form onSubmit={handleSubmit(onSubmit)}>
          <DialogTitle>Edit Profile</DialogTitle>
          <DialogContent>
            <Grid container spacing={2} sx={{ mt: 1 }}>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('firstName', { required: 'First name is required' })}
                  fullWidth
                  label="First Name"
                  error={!!errors.firstName}
                  helperText={errors.firstName?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('lastName', { required: 'Last name is required' })}
                  fullWidth
                  label="Last Name"
                  error={!!errors.lastName}
                  helperText={errors.lastName?.message}
                />
              </Grid>
              <Grid item xs={12}>
                <TextField
                  {...register('email', { 
                    required: 'Email is required',
                    pattern: {
                      value: /^\S+@\S+$/i,
                      message: 'Invalid email address'
                    }
                  })}
                  fullWidth
                  label="Email"
                  type="email"
                  error={!!errors.email}
                  helperText={errors.email?.message}
                />
              </Grid>
              <Grid item xs={12}>
                <TextField
                  {...register('phoneNumber')}
                  fullWidth
                  label="Phone Number"
                  error={!!errors.phoneNumber}
                  helperText={errors.phoneNumber?.message}
                />
              </Grid>
            </Grid>
          </DialogContent>
          <DialogActions>
            <Button onClick={handleEditClose}>Cancel</Button>
            <Button type="submit" variant="contained" startIcon={<Save />}>
              Save Changes
            </Button>
          </DialogActions>
        </form>
      </Dialog>
    </Box>
  );
};

export default StudentProfilePage;
