import { useState } from 'react';
import { useLocation } from 'react-router-dom';
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
  Phone,
  Email,
} from '@mui/icons-material';
import {
  useGetParentChildrenQuery,
  useGetChildAttendanceQuery,
  useGetChildResultsQuery,
  useGetChildFeesQuery,
} from '../../store/api/apiSlice';
import dayjs from 'dayjs';

const OUTSTANDING_FEE_STATUSES = ['PENDING', 'PARTIAL', 'OVERDUE'];

const ParentChildrenPage = () => {
  const location = useLocation();
  const initialTab = Number.isInteger(location.state?.tabIndex) ? location.state.tabIndex : 0;
  const [selectedChild, setSelectedChild] = useState(0);
  const [tabValue, setTabValue] = useState(initialTab);

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
    setTabValue(0);
  };

  const handleTabChange = (event, newValue) => {
    setTabValue(newValue);
  };

  const getAttendanceStats = () => {
    const total = childAttendance.length;
    const present = childAttendance.filter((entry) => entry.status === 'PRESENT').length;
    const absent = childAttendance.filter((entry) => entry.status === 'ABSENT').length;
    const late = childAttendance.filter((entry) => entry.status === 'LATE').length;
    const percentage = total > 0 ? Math.round((present / total) * 100) : null;

    return { percentage, present, absent, late, total };
  };

  const getFeeStats = () => {
    const paid = childFees.reduce((sum, fee) => sum + Number(fee.amountPaid ?? 0), 0);
    const outstanding = childFees.filter((fee) => OUTSTANDING_FEE_STATUSES.includes(fee.status)).length;

    return { paid, outstanding, records: childFees.length };
  };

  const scoredResults = childResults.filter(
    (result) => typeof result.percentage === 'number' && Number.isFinite(result.percentage)
  );
  const averageScore = scoredResults.length
    ? Math.round(scoredResults.reduce((sum, result) => sum + result.percentage, 0) / scoredResults.length)
    : null;

  const childFullName = (child) =>
    [child?.firstName, child?.lastName].filter(Boolean).join(' ');

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 400 }}>
        <Typography>Loading children information...</Typography>
      </Box>
    );
  }

  const attendance = getAttendanceStats();
  const grades = childResults;
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

            {children.length === 0 && (
              <Typography variant="body2" color="text.secondary">
                No children are linked to your profile yet.
              </Typography>
            )}

            <Grid container spacing={2}>
              {children.map((child, index) => (
                <Grid item xs={12} key={child.id}>
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
                          {child.firstName?.charAt(0)}
                        </Avatar>
                        <Box sx={{ flexGrow: 1 }}>
                          <Typography variant="h6">
                            {childFullName(child) || 'Unnamed student'}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Roll No: {child.studentId ?? 'N/A'}
                          </Typography>
                        </Box>
                      </Box>

                      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <Box>
                          <Typography variant="body2" color="text.secondary">Class ID</Typography>
                          <Typography variant="body1">{child.classId ?? 'N/A'}</Typography>
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
            {!currentChild ? (
              <Typography variant="body1" color="text.secondary">
                Select a child to view their details.
              </Typography>
            ) : (
              <>
                {/* Child Header */}
                <Box sx={{ display: 'flex', alignItems: 'center', mb: 3 }}>
                  <Avatar sx={{ width: 60, height: 60, bgcolor: '#1976d2', mr: 2, fontSize: '1.5rem' }}>
                    {currentChild.firstName?.charAt(0)}
                  </Avatar>
                  <Box sx={{ flexGrow: 1 }}>
                    <Typography variant="h5">
                      {childFullName(currentChild) || 'Unnamed student'}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Roll No: {currentChild.studentId ?? 'N/A'} • Class ID: {currentChild.classId ?? 'N/A'} • Section ID: {currentChild.sectionId ?? 'N/A'}
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
                                secondary={currentChild.email ?? 'Not recorded'}
                              />
                            </ListItem>
                            <ListItem>
                              <ListItemText
                                primary="Phone"
                                secondary={currentChild.phoneNumber ?? 'Not recorded'}
                              />
                            </ListItem>
                            <ListItem>
                              <ListItemText
                                primary="Date of Birth"
                                secondary={currentChild.dateOfBirth ? dayjs(currentChild.dateOfBirth).format('MMMM DD, YYYY') : 'Not recorded'}
                              />
                            </ListItem>
                            <ListItem>
                              <ListItemText
                                primary="Gender"
                                secondary={currentChild.gender ?? 'Not recorded'}
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
                            <Typography variant="body2" color="text.secondary">
                              Attendance {attendance.total > 0 ? `(${attendance.present} of ${attendance.total} records)` : '(last month)'}
                            </Typography>
                            {attendance.percentage === null ? (
                              <Typography variant="body2">
                                {attendanceLoading ? 'Loading...' : 'No attendance records'}
                              </Typography>
                            ) : (
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
                            )}
                          </Box>

                          <Box sx={{ mb: 2 }}>
                            <Typography variant="body2" color="text.secondary">Average Score</Typography>
                            {averageScore === null ? (
                              <Typography variant="body2" color="text.secondary">
                                {resultsLoading ? 'Loading...' : 'No results with scores'}
                              </Typography>
                            ) : (
                              <Typography variant="h6" color="primary">
                                {averageScore}% ({scoredResults.length} results)
                              </Typography>
                            )}
                          </Box>

                          <Box>
                            <Typography variant="body2" color="text.secondary">Fee Status</Typography>
                            <Chip
                              label={
                                feesLoading
                                  ? 'Loading...'
                                  : `${fees.outstanding} of ${fees.records} records outstanding`
                              }
                              color={!feesLoading && fees.outstanding > 0 ? 'warning' : 'success'}
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
                    {attendanceLoading && (
                      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                        Loading attendance records...
                      </Typography>
                    )}
                    <Grid container spacing={3} sx={{ mb: 3 }}>
                      <Grid item xs={6} md={3}>
                        <Card>
                          <CardContent sx={{ textAlign: 'center' }}>
                            <Typography variant="h4" color="primary">{attendance.total}</Typography>
                            <Typography variant="body2" color="text.secondary">Marked Records</Typography>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={6} md={3}>
                        <Card>
                          <CardContent sx={{ textAlign: 'center' }}>
                            <Typography variant="h4" color="success.main">{attendance.present}</Typography>
                            <Typography variant="body2" color="text.secondary">Present</Typography>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={6} md={3}>
                        <Card>
                          <CardContent sx={{ textAlign: 'center' }}>
                            <Typography variant="h4" color="error.main">{attendance.absent}</Typography>
                            <Typography variant="body2" color="text.secondary">Absent</Typography>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={6} md={3}>
                        <Card>
                          <CardContent sx={{ textAlign: 'center' }}>
                            <Typography variant="h4" color="warning.main">{attendance.late}</Typography>
                            <Typography variant="body2" color="text.secondary">Late</Typography>
                          </CardContent>
                        </Card>
                      </Grid>
                    </Grid>

                    <Box sx={{ mb: 2 }}>
                      <Typography variant="h6" gutterBottom>Attendance Percentage</Typography>
                      {attendance.percentage === null ? (
                        <Typography variant="body2" color="text.secondary">
                          No attendance marked for this student in the last month.
                        </Typography>
                      ) : (
                        <>
                          <LinearProgress
                            variant="determinate"
                            value={attendance.percentage}
                            sx={{ height: 10, borderRadius: 5 }}
                            color={attendance.percentage >= 75 ? 'success' : 'error'}
                          />
                          <Typography variant="body2" sx={{ mt: 1 }}>
                            {attendance.percentage}% - {attendance.percentage >= 75 ? 'Good' : 'Needs Improvement'}
                          </Typography>
                        </>
                      )}
                    </Box>

                    {attendance.total > 0 && (
                      <Box sx={{ mt: 3 }}>
                        <Typography variant="h6" gutterBottom>Recent Attendance</Typography>
                        <List dense>
                          {childAttendance.slice(0, 10).map((entry) => (
                            <ListItem key={entry.id}>
                              <ListItemAvatar>
                                <Avatar sx={{ bgcolor: '#388e3c' }}>
                                  <EventNote />
                                </Avatar>
                              </ListItemAvatar>
                              <ListItemText
                                primary={dayjs(entry.date).format('MMMM DD, YYYY')}
                                secondary={entry.remarks ?? 'No remarks'}
                              />
                              <Chip
                                label={entry.status}
                                size="small"
                                color={entry.status === 'PRESENT' ? 'success' : entry.status === 'LATE' ? 'warning' : 'error'}
                              />
                            </ListItem>
                          ))}
                        </List>
                      </Box>
                    )}
                  </Box>
                )}

                {tabValue === 2 && (
                  <Box>
                    {resultsLoading && (
                      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                        Loading results...
                      </Typography>
                    )}
                    {grades.length === 0 && !resultsLoading && (
                      <Typography variant="body2" color="text.secondary">
                        No results recorded for this student.
                      </Typography>
                    )}
                    <Typography variant="h6" gutterBottom>Recent Grades</Typography>
                    <List>
                      {grades.map((result) => (
                        <Box key={result.id}>
                          <ListItem>
                            <ListItemAvatar>
                              <Avatar sx={{ bgcolor: '#1976d2' }}>
                                <Assessment />
                              </Avatar>
                            </ListItemAvatar>
                            <ListItemText
                              primary={result.subjectName ?? 'Subject not recorded'}
                              secondary={`${result.examName ?? 'Exam not recorded'} • Marks: ${result.obtainedMarks ?? 'N/A'}${typeof result.percentage === 'number' ? ` • ${result.percentage}%` : ''}`}
                            />
                            {result.grade ? (
                              <Chip
                                label={result.grade}
                                color={
                                  result.grade.startsWith('A')
                                    ? 'success'
                                    : result.grade.startsWith('B')
                                      ? 'primary'
                                      : 'warning'
                                }
                              />
                            ) : (
                              <Chip label="No grade" size="small" />
                            )}
                          </ListItem>
                        </Box>
                      ))}
                    </List>
                  </Box>
                )}

                {tabValue === 3 && (
                  <Box>
                    {feesLoading && (
                      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                        Loading fee records...
                      </Typography>
                    )}
                    <Grid container spacing={3} sx={{ mb: 3 }}>
                      <Grid item xs={4}>
                        <Card>
                          <CardContent sx={{ textAlign: 'center' }}>
                            <Typography variant="h6" color="success.main">
                              ₹{fees.paid.toLocaleString()}
                            </Typography>
                            <Typography variant="body2" color="text.secondary">Paid to Date</Typography>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={4}>
                        <Card>
                          <CardContent sx={{ textAlign: 'center' }}>
                            <Typography variant="h6" color="primary">{fees.records}</Typography>
                            <Typography variant="body2" color="text.secondary">Fee Records</Typography>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={4}>
                        <Card>
                          <CardContent sx={{ textAlign: 'center' }}>
                            <Typography variant="h6" color="error.main">{fees.outstanding}</Typography>
                            <Typography variant="body2" color="text.secondary">Outstanding</Typography>
                          </CardContent>
                        </Card>
                      </Grid>
                    </Grid>

                    {fees.records > 0 && (
                      <Box sx={{ mb: 2 }}>
                        <Typography variant="body2" color="text.secondary">
                          Fee amounts and due dates are not returned by this endpoint, so outstanding balances are shown as record counts.
                        </Typography>
                      </Box>
                    )}

                    <List>
                      {childFees.map((fee, index) => (
                        <Box key={fee.id}>
                          <ListItem>
                            <ListItemText
                              primary={`Fee #${fee.feeId ?? 'N/A'}`}
                              secondary={`Paid: ₹${Number(fee.amountPaid ?? 0).toLocaleString()}`}
                            />
                            <Chip
                              label={fee.status ?? 'Unknown'}
                              size="small"
                              color={
                                OUTSTANDING_FEE_STATUSES.includes(fee.status)
                                  ? 'warning'
                                  : fee.status === 'PAID'
                                    ? 'success'
                                    : 'default'
                              }
                            />
                          </ListItem>
                          {index < childFees.length - 1 && <Divider />}
                        </Box>
                      ))}
                    </List>
                  </Box>
                )}
              </>
            )}
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
};

export default ParentChildrenPage;
