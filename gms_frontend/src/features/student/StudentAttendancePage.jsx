import { useState } from 'react';
import {
  Box,
  Typography,
  Paper,
  Grid,
  Card,
  CardContent,
  LinearProgress,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
} from '@mui/material';
import {
  EventNote,
  CheckCircle,
  Cancel,
  Schedule,
  CalendarToday,
} from '@mui/icons-material';
import { useGetMyAttendanceQuery } from '../../store/api/apiSlice';
import dayjs from 'dayjs';

const StudentAttendancePage = () => {
  const [selectedMonth, setSelectedMonth] = useState(dayjs().format('YYYY-MM'));
  
  const { data: attendance = [], isLoading } = useGetMyAttendanceQuery({
    month: selectedMonth,
  });

  // Calculate attendance statistics
  const calculateStats = () => {
    const totalDays = attendance.length;
    const presentDays = attendance.filter(record => record.status === 'PRESENT').length;
    const absentDays = attendance.filter(record => record.status === 'ABSENT').length;
    const lateDays = attendance.filter(record => record.status === 'LATE').length;
    
    const attendancePercentage = totalDays > 0 ? Math.round((presentDays / totalDays) * 100) : 0;
    
    return {
      totalDays,
      presentDays,
      absentDays,
      lateDays,
      attendancePercentage,
    };
  };

  const stats = calculateStats();

  // Generate month options for the last 12 months
  const getMonthOptions = () => {
    const months = [];
    for (let i = 0; i < 12; i++) {
      const month = dayjs().subtract(i, 'month');
      months.push({
        value: month.format('YYYY-MM'),
        label: month.format('MMMM YYYY'),
      });
    }
    return months;
  };

  const getStatusColor = (status) => {
    switch (status) {
      case 'PRESENT':
        return 'success';
      case 'ABSENT':
        return 'error';
      case 'LATE':
        return 'warning';
      default:
        return 'default';
    }
  };

  const getAttendanceColor = (percentage) => {
    if (percentage >= 90) return 'success';
    if (percentage >= 75) return 'warning';
    return 'error';
  };

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          My Attendance
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Track your attendance record and statistics
        </Typography>
      </Box>

      {/* Month Selection */}
      <Paper sx={{ p: 3, mb: 3 }}>
        <Grid container spacing={3} alignItems="center">
          <Grid item xs={12} md={4}>
            <FormControl fullWidth>
              <InputLabel>Select Month</InputLabel>
              <Select
                value={selectedMonth}
                onChange={(e) => setSelectedMonth(e.target.value)}
                label="Select Month"
              >
                {getMonthOptions().map((month) => (
                  <MenuItem key={month.value} value={month.value}>
                    {month.label}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>
          
          <Grid item xs={12} md={8}>
            <Box sx={{ display: 'flex', alignItems: 'center' }}>
              <CalendarToday sx={{ mr: 1, color: 'text.secondary' }} />
              <Typography variant="body1">
                Showing attendance for {dayjs(selectedMonth).format('MMMM YYYY')}
              </Typography>
            </Box>
          </Grid>
        </Grid>
      </Paper>

      {/* Statistics Cards */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        <Grid item xs={12} md={3}>
          <Card>
            <CardContent sx={{ textAlign: 'center' }}>
              <EventNote sx={{ fontSize: 40, color: '#1976d2', mb: 1 }} />
              <Typography variant="h4" color="primary">
                {stats.totalDays}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Total Days
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={3}>
          <Card>
            <CardContent sx={{ textAlign: 'center' }}>
              <CheckCircle sx={{ fontSize: 40, color: '#4caf50', mb: 1 }} />
              <Typography variant="h4" color="success.main">
                {stats.presentDays}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Present Days
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={3}>
          <Card>
            <CardContent sx={{ textAlign: 'center' }}>
              <Cancel sx={{ fontSize: 40, color: '#f44336', mb: 1 }} />
              <Typography variant="h4" color="error.main">
                {stats.absentDays}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Absent Days
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={3}>
          <Card>
            <CardContent sx={{ textAlign: 'center' }}>
              <Schedule sx={{ fontSize: 40, color: '#ff9800', mb: 1 }} />
              <Typography variant="h4" color="warning.main">
                {stats.lateDays}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Late Days
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Attendance Percentage */}
      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6" gutterBottom>
          Attendance Percentage
        </Typography>
        <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
          <Box sx={{ width: '100%', mr: 1 }}>
            <LinearProgress
              variant="determinate"
              value={stats.attendancePercentage}
              color={getAttendanceColor(stats.attendancePercentage)}
              sx={{ height: 10, borderRadius: 5 }}
            />
          </Box>
          <Box sx={{ minWidth: 35 }}>
            <Typography
              variant="body2"
              color={`${getAttendanceColor(stats.attendancePercentage)}.main`}
              sx={{ fontWeight: 'bold' }}
            >
              {stats.attendancePercentage}%
            </Typography>
          </Box>
        </Box>
        
        <Typography variant="body2" color="text.secondary">
          {stats.attendancePercentage >= 90 && "Excellent attendance! Keep it up!"}
          {stats.attendancePercentage >= 75 && stats.attendancePercentage < 90 && "Good attendance. Try to improve further."}
          {stats.attendancePercentage < 75 && "Attendance needs improvement. Please attend classes regularly."}
        </Typography>
      </Paper>

      {/* Attendance Records Table */}
      <Paper>
        <Box sx={{ p: 2, borderBottom: '1px solid #e0e0e0' }}>
          <Typography variant="h6">
            Attendance Records - {dayjs(selectedMonth).format('MMMM YYYY')}
          </Typography>
        </Box>
        
        {isLoading ? (
          <Box sx={{ p: 4, textAlign: 'center' }}>
            <Typography>Loading attendance records...</Typography>
          </Box>
        ) : attendance.length === 0 ? (
          <Box sx={{ p: 4, textAlign: 'center' }}>
            <EventNote sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
            <Typography variant="h6" color="text.secondary" gutterBottom>
              No Attendance Records
            </Typography>
            <Typography variant="body2" color="text.secondary">
              No attendance records found for {dayjs(selectedMonth).format('MMMM YYYY')}
            </Typography>
          </Box>
        ) : (
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Date</TableCell>
                  <TableCell>Day</TableCell>
                  <TableCell>Subject</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Time</TableCell>
                  <TableCell>Remarks</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {attendance.map((record, index) => (
                  <TableRow key={index}>
                    <TableCell>
                      {dayjs(record.date).format('MMM DD, YYYY')}
                    </TableCell>
                    <TableCell>
                      {dayjs(record.date).format('dddd')}
                    </TableCell>
                    <TableCell>
                      {record.subject || 'General'}
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={record.status}
                        color={getStatusColor(record.status)}
                        size="small"
                      />
                    </TableCell>
                    <TableCell>
                      {record.time || dayjs(record.createdAt).format('HH:mm')}
                    </TableCell>
                    <TableCell>
                      {record.remarks || '-'}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>
    </Box>
  );
};

export default StudentAttendancePage;
