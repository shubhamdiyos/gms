import { useLocation, useNavigate } from 'react-router-dom';
import {
  Box,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Toolbar,
  Typography,
  Divider,
  Chip,
} from '@mui/material';
import {
  Dashboard,
  School,
  People,
  PersonAdd,
  Class,
  Subject,
  CalendarToday,
  Assignment,
  Payment,
  Assessment,
  Notifications,
  Campaign,
  BarChart,
  Settings,
  AccountBalance,
  Groups,
  Person,
  Schedule,
  Grade,
  AttachMoney,
  EventNote,
} from '@mui/icons-material';
import { useAuth, ROLE_PRECEDENCE } from '../../hooks/useAuth';
import { ROLES, ROUTES, ROLE_LABELS } from '../../constants';

// One section per portal role; every path here is a route App.jsx declares and the backend admits.
const NAV_SECTIONS = {
  [ROLES.SUPERADMIN]: [
    {
      text: 'Dashboard',
      icon: <Dashboard />,
      path: ROUTES.SUPERADMIN.DASHBOARD,
    },
    {
      text: 'Schools',
      icon: <School />,
      path: ROUTES.SUPERADMIN.SCHOOLS,
    },
    {
      text: 'System Reports',
      icon: <BarChart />,
      path: ROUTES.SUPERADMIN.REPORTS,
    },
    {
      text: 'Settings',
      icon: <Settings />,
      path: ROUTES.SUPERADMIN.SETTINGS,
    },
  ],

  [ROLES.ADMIN]: [
    {
      text: 'Dashboard',
      icon: <Dashboard />,
      path: ROUTES.ADMIN.DASHBOARD,
    },
    {
      text: 'Students',
      icon: <People />,
      path: ROUTES.ADMIN.STUDENTS,
    },
    {
      text: 'Employees',
      icon: <PersonAdd />,
      path: ROUTES.ADMIN.EMPLOYEES,
    },
    {
      text: 'Classrooms',
      icon: <Class />,
      path: ROUTES.ADMIN.CLASSROOMS,
    },
    {
      text: 'Subjects',
      icon: <Subject />,
      path: ROUTES.ADMIN.SUBJECTS,
    },
    {
      text: 'Academic Years',
      icon: <CalendarToday />,
      path: ROUTES.ADMIN.ACADEMIC_YEARS,
    },
    {
      text: 'Fees',
      icon: <Payment />,
      path: ROUTES.ADMIN.FEES,
    },
    {
      text: 'Exams',
      icon: <Assignment />,
      path: ROUTES.ADMIN.EXAMS,
    },
    {
      text: 'Announcements',
      icon: <Campaign />,
      path: ROUTES.ADMIN.ANNOUNCEMENTS,
    },
  ],

  [ROLES.ACCOUNTANT]: [
    {
      text: 'Fees',
      icon: <Payment />,
      path: ROUTES.ADMIN.FEES,
    },
  ],

  [ROLES.TEACHER]: [
    {
      text: 'Dashboard',
      icon: <Dashboard />,
      path: ROUTES.TEACHER.DASHBOARD,
    },
    {
      text: 'My Classes',
      icon: <Groups />,
      path: ROUTES.TEACHER.CLASSES,
    },
    {
      text: 'Attendance',
      icon: <EventNote />,
      path: ROUTES.TEACHER.ATTENDANCE,
    },
    {
      text: 'Grades',
      icon: <Grade />,
      path: ROUTES.TEACHER.GRADES,
    },
    {
      text: 'Students',
      icon: <People />,
      path: ROUTES.TEACHER.STUDENTS,
    },
    {
      text: 'Schedule',
      icon: <Schedule />,
      path: ROUTES.TEACHER.SCHEDULE,
    },
  ],

  [ROLES.STUDENT]: [
    {
      text: 'Dashboard',
      icon: <Dashboard />,
      path: ROUTES.STUDENT.DASHBOARD,
    },
    {
      text: 'Profile',
      icon: <Person />,
      path: ROUTES.STUDENT.PROFILE,
    },
    {
      text: 'Attendance',
      icon: <EventNote />,
      path: ROUTES.STUDENT.ATTENDANCE,
    },
    {
      text: 'Results',
      icon: <Assessment />,
      path: ROUTES.STUDENT.RESULTS,
    },
    {
      text: 'Fees',
      icon: <AttachMoney />,
      path: ROUTES.STUDENT.FEES,
    },
    {
      text: 'Timetable',
      icon: <Schedule />,
      path: ROUTES.STUDENT.TIMETABLE,
    },
  ],

  // Attendance, results and fees for a child are tabs of ParentChildrenPage, not separate routes.
  [ROLES.PARENT]: [
    {
      text: 'Dashboard',
      icon: <Dashboard />,
      path: ROUTES.PARENT.DASHBOARD,
    },
    {
      text: 'Children',
      icon: <Groups />,
      path: ROUTES.PARENT.CHILDREN,
    },
  ],
};

const Sidebar = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, getUserRole, hasRole } = useAuth();
  const userRole = getUserRole();

  // Navigation items based on user role
  const getNavigationItems = () => {
    const items = [...(NAV_SECTIONS[userRole] || [])];

    // ACCOUNTANT outranks the academic portals, so a dual-role account keeps the sections below it.
    if (userRole === ROLES.ACCOUNTANT) {
      ROLE_PRECEDENCE.slice(ROLE_PRECEDENCE.indexOf(ROLES.ACCOUNTANT) + 1)
        .filter((role) => hasRole(role))
        .forEach((role) => items.push(...(NAV_SECTIONS[role] || [])));
    }

    return items.filter(
      (item, index) => items.findIndex((other) => other.path === item.path) === index
    );
  };

  const navigationItems = getNavigationItems();

  const handleNavigation = (path) => {
    navigate(path);
  };

  const getRoleColor = (role) => {
    switch (role) {
      case ROLES.SUPERADMIN:
        return '#d32f2f';
      case ROLES.ADMIN:
        return '#1976d2';
      case ROLES.TEACHER:
        return '#388e3c';
      case ROLES.ACCOUNTANT:
        return '#00796b';
      case ROLES.STUDENT:
        return '#f57c00';
      case ROLES.PARENT:
        return '#7b1fa2';
      default:
        return '#757575';
    }
  };

  return (
    <Box sx={{ height: '100%', backgroundColor: '#fff' }}>
      {/* Header */}
      <Toolbar
        sx={{
          background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
          color: 'white',
        }}
      >
        <Box sx={{ width: '100%', textAlign: 'center' }}>
          <Typography variant="h6" noWrap component="div" sx={{ fontWeight: 'bold' }}>
            GMS Portal
          </Typography>
          <Chip
            label={ROLE_LABELS[userRole] || userRole}
            size="small"
            sx={{
              mt: 1,
              backgroundColor: getRoleColor(userRole),
              color: 'white',
              fontWeight: 'bold',
              fontSize: '0.75rem',
            }}
          />
        </Box>
      </Toolbar>

      <Divider />

      {/* User Info */}
      <Box sx={{ p: 2, backgroundColor: '#f8f9fa' }}>
        <Typography variant="body2" color="text.secondary">
          Welcome back,
        </Typography>
        <Typography variant="subtitle1" sx={{ fontWeight: 'bold' }}>
          {user?.username}
        </Typography>
      </Box>

      <Divider />

      {/* Navigation */}
      <List sx={{ pt: 1 }}>
        {navigationItems.map((item) => {
          const isActive = location.pathname === item.path;
          
          return (
            <ListItem key={item.path} disablePadding>
              <ListItemButton
                onClick={() => handleNavigation(item.path)}
                sx={{
                  mx: 1,
                  mb: 0.5,
                  borderRadius: 2,
                  backgroundColor: isActive ? 'rgba(25, 118, 210, 0.08)' : 'transparent',
                  '&:hover': {
                    backgroundColor: isActive 
                      ? 'rgba(25, 118, 210, 0.12)' 
                      : 'rgba(0, 0, 0, 0.04)',
                  },
                }}
              >
                <ListItemIcon
                  sx={{
                    color: isActive ? 'primary.main' : 'text.secondary',
                    minWidth: 40,
                  }}
                >
                  {item.icon}
                </ListItemIcon>
                <ListItemText
                  primary={item.text}
                  sx={{
                    '& .MuiListItemText-primary': {
                      color: isActive ? 'primary.main' : 'text.primary',
                      fontWeight: isActive ? 600 : 400,
                    },
                  }}
                />
              </ListItemButton>
            </ListItem>
          );
        })}
      </List>

      {/* Footer */}
      <Box
        sx={{
          position: 'absolute',
          bottom: 0,
          width: '100%',
          p: 2,
          backgroundColor: '#f8f9fa',
          borderTop: '1px solid #e0e0e0',
        }}
      >
        <Typography variant="caption" color="text.secondary" align="center" display="block">
          © 2024 GMS Portal
        </Typography>
        <Typography variant="caption" color="text.secondary" align="center" display="block">
          Version 1.0.0
        </Typography>
      </Box>
    </Box>
  );
};

export default Sidebar;
