import { useEffect, useMemo, useState } from 'react';
import { useDispatch } from 'react-redux';
import { useNavigate } from 'react-router-dom';
import { Grid, Paper, Typography, Box, Card, CardContent, Avatar } from '@mui/material';
import {
  Groups,
  EventNote,
  Assessment,
  AttachMoney,
} from '@mui/icons-material';
import {
  apiSlice,
  useGetParentProfileQuery,
  useGetParentChildrenQuery,
} from '../../store/api/apiSlice';
import { ROUTES } from '../../constants';

const OUTSTANDING_FEE_STATUSES = ['PENDING', 'PARTIAL', 'OVERDUE'];

const StatCard = ({ title, value, icon, color, subtitle }) => (
  <Card sx={{ height: '100%' }}>
    <CardContent>
      <Box sx={{ display: 'flex', alignItems: 'center' }}>
        <Avatar sx={{ bgcolor: color, mr: 2 }}>
          {icon}
        </Avatar>
        <Box sx={{ flexGrow: 1 }}>
          <Typography color="textSecondary" gutterBottom variant="overline">
            {title}
          </Typography>
          <Typography variant="h4" component="h2">
            {value}
          </Typography>
          {subtitle && (
            <Typography color="textSecondary" variant="body2">
              {subtitle}
            </Typography>
          )}
        </Box>
      </Box>
    </CardContent>
  </Card>
);

const emptyStats = () => ({
  records: 0,
  present: 0,
  absent: 0,
  late: 0,
  scoreCount: 0,
  scoreSum: 0,
  feeRecords: 0,
  paid: 0,
  outstanding: 0,
});

const buildStats = (attendance, results, fees) => {
  const stats = emptyStats();

  attendance.forEach((entry) => {
    stats.records += 1;
    if (entry.status === 'PRESENT') stats.present += 1;
    if (entry.status === 'ABSENT') stats.absent += 1;
    if (entry.status === 'LATE') stats.late += 1;
  });

  results.forEach((result) => {
    if (typeof result.percentage === 'number' && Number.isFinite(result.percentage)) {
      stats.scoreCount += 1;
      stats.scoreSum += result.percentage;
    }
  });

  fees.forEach((fee) => {
    stats.feeRecords += 1;
    stats.paid += Number(fee.amountPaid ?? 0);
    if (OUTSTANDING_FEE_STATUSES.includes(fee.status)) stats.outstanding += 1;
  });

  return stats;
};

const combineStats = (statsList) => statsList.reduce((total, stats) => {
  total.records += stats.records;
  total.present += stats.present;
  total.absent += stats.absent;
  total.late += stats.late;
  total.scoreCount += stats.scoreCount;
  total.scoreSum += stats.scoreSum;
  total.feeRecords += stats.feeRecords;
  total.paid += stats.paid;
  total.outstanding += stats.outstanding;
  return total;
}, emptyStats());

const attendancePercent = (stats) =>
  (stats.records > 0 ? Math.round((stats.present / stats.records) * 100) : null);

const averagePercent = (stats) =>
  (stats.scoreCount > 0 ? Math.round(stats.scoreSum / stats.scoreCount) : null);

const ParentDashboard = () => {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const { data: profile } = useGetParentProfileQuery();
  const { data: children = [] } = useGetParentChildrenQuery();
  const [childStats, setChildStats] = useState({});
  const [statsLoading, setStatsLoading] = useState(false);

  const parentName = [profile?.firstName, profile?.lastName]
    .filter(Boolean)
    .join(' ');

  const childIdsKey = useMemo(
    () => children.map((child) => child.id).filter((id) => id != null).join(','),
    [children]
  );

  useEffect(() => {
    const studentIds = childIdsKey ? childIdsKey.split(',').map(Number) : [];
    if (studentIds.length === 0) {
      setChildStats({});
      return undefined;
    }

    let active = true;
    setStatsLoading(true);

    const loadChildData = async () => {
      const settled = await Promise.allSettled(
        studentIds.map(async (studentId) => {
          const [attendance, results, fees] = await Promise.all([
            dispatch(apiSlice.endpoints.getChildAttendance.initiate(studentId, { subscribe: false })).unwrap(),
            dispatch(apiSlice.endpoints.getChildResults.initiate(studentId, { subscribe: false })).unwrap(),
            dispatch(apiSlice.endpoints.getChildFees.initiate(studentId, { subscribe: false })).unwrap(),
          ]);
          return { studentId, stats: buildStats(attendance ?? [], results ?? [], fees ?? []) };
        })
      );

      if (!active) return;
      const nextStats = {};
      settled
        .filter((entry) => entry.status === 'fulfilled')
        .forEach((entry) => {
          nextStats[entry.value.studentId] = entry.value.stats;
        });
      setChildStats(nextStats);
      setStatsLoading(false);
    };

    loadChildData();

    return () => {
      active = false;
    };
  }, [childIdsKey, dispatch]);

  const collectedStats = Object.values(childStats);
  const overall = combineStats(collectedStats);
  const activeChildren = children.filter((child) => child.status === '1').length;
  const overallAttendance = attendancePercent(overall);
  const overallScore = averagePercent(overall);

  const stats = [
    {
      title: 'My Children',
      value: children.length,
      icon: <Groups />,
      color: '#1976d2',
      subtitle: `${activeChildren} active of ${children.length} enrolled`,
    },
    {
      title: 'Avg Attendance',
      value: overallAttendance === null ? (statsLoading ? 'Loading' : 'No data') : `${overallAttendance}%`,
      icon: <EventNote />,
      color: '#388e3c',
      subtitle:
        overallAttendance === null
          ? 'No attendance marked in last month'
          : `${overall.present} present of ${overall.records} records, last month`,
    },
    {
      title: 'Avg Score',
      value: overallScore === null ? (statsLoading ? 'Loading' : 'No data') : `${overallScore}%`,
      icon: <Assessment />,
      color: '#f57c00',
      subtitle:
        overallScore === null
          ? 'No results with scores'
          : `Across ${overall.scoreCount} results with scores`,
    },
    {
      title: 'Fees Paid',
      value: `₹${overall.paid.toLocaleString()}`,
      icon: <AttachMoney />,
      color: '#7b1fa2',
      subtitle: `${overall.outstanding} of ${overall.feeRecords} fee records outstanding`,
    },
  ];

  const goToChildTab = (tabIndex) => {
    navigate(ROUTES.PARENT.CHILDREN, { state: { tabIndex } });
  };

  const quickActions = [
    { label: 'View Attendance', description: "Check children's attendance", tabIndex: 1 },
    { label: 'View Results', description: 'Check academic performance', tabIndex: 2 },
    { label: 'View Fees', description: 'Check fee records and payments', tabIndex: 3 },
  ];

  return (
    <Box>
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          Parent Dashboard
        </Typography>
        <Typography variant="body1" color="text.secondary">
          {parentName ? `Welcome back, ${parentName}` : 'Welcome back'}
        </Typography>
      </Box>

      <Grid container spacing={3} sx={{ mb: 4 }}>
        {stats.map((stat) => (
          <Grid item xs={12} sm={6} md={3} key={stat.title}>
            <StatCard {...stat} />
          </Grid>
        ))}
      </Grid>

      <Grid container spacing={3}>
        <Grid item xs={12} md={8}>
          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>
              My Children
            </Typography>
            <Box>
              {children.length === 0 && (
                <Typography variant="body2" color="text.secondary">
                  No children are linked to your profile yet.
                </Typography>
              )}
              {children.map((child) => {
                const statsForChild = childStats[child.id];
                const childAttendance = statsForChild ? attendancePercent(statsForChild) : null;
                const childScore = statsForChild ? averagePercent(statsForChild) : null;

                return (
                  <Box
                    key={child.id}
                    sx={{
                      display: 'flex',
                      alignItems: 'center',
                      py: 2,
                      borderBottom: '1px solid #e0e0e0',
                      '&:last-child': { borderBottom: 'none' },
                    }}
                  >
                    <Avatar sx={{ bgcolor: '#1976d2', mr: 2 }}>
                      {child.firstName?.charAt(0)}
                    </Avatar>
                    <Box sx={{ flexGrow: 1 }}>
                      <Typography variant="subtitle1">
                        {[child.firstName, child.lastName].filter(Boolean).join(' ')}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Class ID: {child.classId ?? 'N/A'} • Roll No: {child.studentId ?? 'N/A'} • Status: {child.status === '1' ? 'Active' : 'Inactive'}
                      </Typography>
                    </Box>
                    <Box sx={{ textAlign: 'right' }}>
                      <Typography variant="body2" color="success.main">
                        {childAttendance === null ? 'Attendance: N/A' : `${childAttendance}% Attendance`}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {childScore === null ? 'Score: N/A' : `Avg Score: ${childScore}%`}
                      </Typography>
                    </Box>
                  </Box>
                );
              })}
            </Box>
          </Paper>
        </Grid>

        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>
              Quick Actions
            </Typography>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              {quickActions.map((action) => (
                <Card
                  key={action.label}
                  sx={{ cursor: 'pointer', '&:hover': { bgcolor: '#f5f5f5' } }}
                  onClick={() => goToChildTab(action.tabIndex)}
                >
                  <CardContent sx={{ py: 2 }}>
                    <Typography variant="subtitle2">{action.label}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {action.description}
                    </Typography>
                  </CardContent>
                </Card>
              ))}
            </Box>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
};

export default ParentDashboard;
