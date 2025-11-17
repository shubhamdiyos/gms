import { useState } from 'react';
import {
  Box,
  Typography,
  Paper,
  Grid,
  Card,
  CardContent,
  Avatar,
  Chip,
  Button,
  LinearProgress,
  Tabs,
  Tab,
  List,
  ListItem,
  ListItemText,
  ListItemAvatar,
  Divider,
} from '@mui/material';
import {
  Person,
  School,
  EventNote,
  Assessment,
  AttachMoney,
  Phone,
  Email,
} from '@mui/icons-material';
import { useGetParentChildrenQuery, useGetChildAttendanceQuery, useGetChildResultsQuery, useGetChildFeesQuery } from '../../store/api/apiSlice';
import dayjs from 'dayjs';

const ParentChildrenPage = () => {
  const [selectedChild, setSelectedChild] = useState(0);
  const [tabValue, setTabValue] = useState(0);
  
  const { data: children = [], isLoading } = useGetParentChildrenQuery();
  const currentChild = children[selectedChild];

  const { data: childAttendance = [], isLoading: attendanceLoading } = useGetChildAttendanceQuery(
    currentChild?.id || 0,
    { skip: !currentChild?.id }
  );
  const { data: childResults = [], isLoading: resultsLoading } = useGetChildResultsQuery(
    currentChild?.id || 0,
    { skip: !currentChild?.id }
  );
  const { data: childFees = [], isLoading: feesLoading } = useGetChildFeesQuery(
    currentChild?.id || 0,
    { skip: !currentChild?.id }
  );

  const handleChildChange = (childIndex) => {
    setSelectedChild(childIndex);
    setTabValue(0); // Reset to first tab when changing child
  };

  const handleTabChange = (event, newValue) => {
    setTabValue(newValue);
  };

  // Calculate attendance stats from real data
  const getAttendanceStats = () => {
    if (!childAttendance.length) return { percentage: 0, present: 0, absent: 0, total: 0 };
    
    const total = childAttendance.length;
    const present = childAttendance.filter(a => a.status === 'PRESENT').length;
    const absent = total - present;
    const percentage = total > 0 ? Math.round((present / total) * 100) : 0;
    
    return { percentage, present, absent, total };
  };

  // Calculate fee stats from real data
  const getFeeStats = () => {
    if (!childFees.length) return { totalFees: 0, paidFees: 0, pendingFees: 0 };
    
    const totalFees = childFees.reduce((sum, fee) => sum + (fee.totalAmount || 0), 0);
    const paidFees = childFees.reduce((sum, fee) => sum + (fee.amountPaid || 0), 0);
    const pendingFees = totalFees - paidFees;
    
    return { totalFees, paidFees, pendingFees };
  };

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 400 }}>
        <Typography>Loading children information...</Typography>
      </Box>
    );
  }

  const attendance = getAttendanceStats();
  const grades = childResults; // Use real results data
  const fees = getFeeStats();

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          My Children
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Monitor your children's academic progress and information
        </Typography>
      </Box>

      <Grid container spacing={3}>
        {/* Children List */}
        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>
              Children ({children.length})
            </Typography>
            
            <Grid container spacing={2}>
              {children.map((child, index) => (
                <Grid item xs={12} key={child.id || index}>
                  <Card 
                    sx={{ 
                      cursor: 'pointer',
                      border: selectedChild === index ? '2px solid #1976d2' : '1px solid #e0e0e0',
                      '&:hover': { boxShadow: 3 }
                    }}
                    onClick={() => handleChildChange(index)}
                  >
                    <CardContent>
                      <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                        <Avatar sx={{ bgcolor: '#1976d2', mr: 2 }}>
                          {child.firstName?.charAt(0) || 'S'}
                        </Avatar>
                        <Box sx={{ flexGrow: 1 }}>
                          <Typography variant="h6">
                            {child.fullName || `${child.firstName} ${child.lastName}`}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            ID: {child.studentId || child.id}
                          </Typography>
                        </Box>
                      </Box>
                      
                      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <Box>
                          <Typography variant="body2" color="text.secondary">Class</Typography>
                          <Typography variant="body1">{child.className || 'Class 10'}</Typography>
                        </Box>
                        <Chip 
                          label={child.status === '1' ? 'Active' : 'Inactive'}
                          color={child.status === '1' ? 'success' : 'error'}
                          size="small"
                        />
                      </Box>
                    </CardContent>
                  </Card>
                </Grid>
              ))}
            </Grid>
          </Paper>
        </Grid>

        {/* Child Details */}
        <Grid item xs={12} md={8}>
          <Paper sx={{ p: 3 }}>
            {/* Child Header */}
            <Box sx={{ display: 'flex', alignItems: 'center', mb: 3 }}>
              <Avatar sx={{ width: 60, height: 60, bgcolor: '#1976d2', mr: 2, fontSize: '1.5rem' }}>
                {currentChild?.firstName?.charAt(0) || 'S'}
              </Avatar>
              <Box sx={{ flexGrow: 1 }}>
                <Typography variant="h5">
                  {currentChild?.fullName || `${currentChild?.firstName} ${currentChild?.lastName}`}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Student ID: {currentChild?.studentId || currentChild?.id} • Class: {currentChild?.className || 'Class 10'}
                </Typography>
              </Box>
              <Button variant="outlined" startIcon={<Phone />} size="small">
                Contact School
              </Button>
            </Box>

            {/* Tabs */}
            <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
              <Tabs value={tabValue} onChange={handleTabChange}>
                <Tab label="Overview" />
                <Tab label="Attendance" />
                <Tab label="Grades" />
                <Tab label="Fees" />
              </Tabs>
            </Box>

            {/* Tab Content */}
            {tabValue === 0 && (
              <Grid container spacing={3}>
                {/* Personal Info */}
                <Grid item xs={12} md={6}>
                  <Card>
                    <CardContent>
                      <Typography variant="h6" gutterBottom sx={{ display: 'flex', alignItems: 'center' }}>
                        <Person sx={{ mr: 1 }} />
                        Personal Information
                      </Typography>
                      <List dense>
                        <ListItem>
                          <ListItemText 
                            primary="Email" 
                            secondary={currentChild?.email || 'Not provided'} 
                          />
                        </ListItem>
                        <ListItem>
                          <ListItemText 
                            primary="Phone" 
                            secondary={currentChild?.phoneNumber || 'Not provided'} 
                          />
                        </ListItem>
                        <ListItem>
                          <ListItemText 
                            primary="Date of Birth" 
                            secondary={currentChild?.dateOfBirth ? dayjs(currentChild.dateOfBirth).format('MMMM DD, YYYY') : 'Not provided'} 
                          />
                        </ListItem>
                        <ListItem>
                          <ListItemText 
                            primary="Gender" 
                            secondary={currentChild?.gender || 'Not specified'} 
                          />
                        </ListItem>
                      </List>
                    </CardContent>
                  </Card>
                </Grid>

                {/* Quick Stats */}
                <Grid item xs={12} md={6}>
                  <Card>
                    <CardContent>
                      <Typography variant="h6" gutterBottom sx={{ display: 'flex', alignItems: 'center' }}>
                        <School sx={{ mr: 1 }} />
                        Quick Stats
                      </Typography>
                      <Box sx={{ mb: 2 }}>
                        <Typography variant="body2" color="text.secondary">Attendance</Typography>
                        <Box sx={{ display: 'flex', alignItems: 'center' }}>
                          <LinearProgress 
                            variant="determinate" 
                            value={attendance.percentage} 
                            sx={{ flexGrow: 1, mr: 1, height: 8, borderRadius: 4 }}
                            color={attendance.percentage >= 75 ? 'success' : 'error'}
                          />
                          <Typography variant="body2" fontWeight="bold">
                            {attendance.percentage}%
                          </Typography>
                        </Box>
                      </Box>
                      
                      <Box sx={{ mb: 2 }}>
                        <Typography variant="body2" color="text.secondary">Overall Grade</Typography>
                        <Typography variant="h6" color="primary">A-</Typography>
                      </Box>
                      
                      <Box>
                        <Typography variant="body2" color="text.secondary">Fee Status</Typography>
                        <Chip 
                          label={fees.pendingFees > 0 ? `₹${fees.pendingFees} Pending` : 'Paid'}
                          color={fees.pendingFees > 0 ? 'warning' : 'success'}
                          size="small"
                        />
                      </Box>
                    </CardContent>
                  </Card>
                </Grid>
              </Grid>
            )}

            {tabValue === 1 && (
              <Box>
                <Grid container spacing={3} sx={{ mb: 3 }}>
                  <Grid item xs={4}>
                    <Card>
                      <CardContent sx={{ textAlign: 'center' }}>
                        <Typography variant="h4" color="primary">{attendance.total}</Typography>
                        <Typography variant="body2" color="text.secondary">Total Days</Typography>
                      </CardContent>
                    </Card>
                  </Grid>
                  <Grid item xs={4}>
                    <Card>
                      <CardContent sx={{ textAlign: 'center' }}>
                        <Typography variant="h4" color="success.main">{attendance.present}</Typography>
                        <Typography variant="body2" color="text.secondary">Present</Typography>
                      </CardContent>
                    </Card>
                  </Grid>
                  <Grid item xs={4}>
                    <Card>
                      <CardContent sx={{ textAlign: 'center' }}>
                        <Typography variant="h4" color="error.main">{attendance.absent}</Typography>
                        <Typography variant="body2" color="text.secondary">Absent</Typography>
                      </CardContent>
                    </Card>
                  </Grid>
                </Grid>
                
                <Box sx={{ mb: 2 }}>
                  <Typography variant="h6" gutterBottom>Attendance Percentage</Typography>
                  <LinearProgress 
                    variant="determinate" 
                    value={attendance.percentage} 
                    sx={{ height: 10, borderRadius: 5 }}
                    color={attendance.percentage >= 75 ? 'success' : 'error'}
                  />
                  <Typography variant="body2" sx={{ mt: 1 }}>
                    {attendance.percentage}% - {attendance.percentage >= 75 ? 'Good' : 'Needs Improvement'}
                  </Typography>
                </Box>
              </Box>
            )}

            {tabValue === 2 && (
              <Box>
                <Typography variant="h6" gutterBottom>Recent Grades</Typography>
                <List>
                  {grades.map((grade, index) => (
                    <Box key={index}>
                      <ListItem>
                        <ListItemAvatar>
                          <Avatar sx={{ bgcolor: '#1976d2' }}>
                            <Assessment />
                          </Avatar>
                        </ListItemAvatar>
                        <ListItemText
                          primary={grade.subject}
                          secondary={`Marks: ${grade.marks}`}
                        />
                        <Chip 
                          label={grade.grade}
                          color={grade.grade.startsWith('A') ? 'success' : grade.grade.startsWith('B') ? 'primary' : 'warning'}
                        />
                      </ListItem>
                      {index < grades.length - 1 && <Divider />}
                    </Box>
                  ))}
                </List>
              </Box>
            )}

            {tabValue === 3 && (
              <Box>
                <Grid container spacing={3} sx={{ mb: 3 }}>
                  <Grid item xs={4}>
                    <Card>
                      <CardContent sx={{ textAlign: 'center' }}>
                        <Typography variant="h6" color="primary">₹{fees.totalFees.toLocaleString()}</Typography>
                        <Typography variant="body2" color="text.secondary">Total Fees</Typography>
                      </CardContent>
                    </Card>
                  </Grid>
                  <Grid item xs={4}>
                    <Card>
                      <CardContent sx={{ textAlign: 'center' }}>
                        <Typography variant="h6" color="success.main">₹{fees.paidFees.toLocaleString()}</Typography>
                        <Typography variant="body2" color="text.secondary">Paid</Typography>
                      </CardContent>
                    </Card>
                  </Grid>
                  <Grid item xs={4}>
                    <Card>
                      <CardContent sx={{ textAlign: 'center' }}>
                        <Typography variant="h6" color="error.main">₹{fees.pendingFees.toLocaleString()}</Typography>
                        <Typography variant="body2" color="text.secondary">Pending</Typography>
                      </CardContent>
                    </Card>
                  </Grid>
                </Grid>
                
                {fees.pendingFees > 0 && (
                  <Box sx={{ p: 2, bgcolor: '#fff3e0', borderRadius: 1, mb: 2 }}>
                    <Typography variant="body1" gutterBottom>
                      <strong>Next Due Date:</strong> {dayjs(fees.nextDueDate).format('MMMM DD, YYYY')}
                    </Typography>
                    <Button variant="contained" color="primary" startIcon={<AttachMoney />}>
                      Pay Now
                    </Button>
                  </Box>
                )}
              </Box>
            )}
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
};

export default ParentChildrenPage;
