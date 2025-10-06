import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { Provider } from 'react-redux';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { CssBaseline, Box, CircularProgress } from '@mui/material';
import { ToastContainer } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';

import store from './store/store';
import { useAuth } from './hooks/useAuth';
import { ROUTES, ROLES } from './constants';

// Components
import ProtectedRoute from './components/common/ProtectedRoute';
import Login from './features/auth/Login';
import DashboardLayout from './components/layout/DashboardLayout';

// Dashboard Pages
import SuperAdminDashboard from './features/dashboard/SuperAdminDashboard';
import AdminDashboard from './features/dashboard/AdminDashboard';
import TeacherDashboard from './features/dashboard/TeacherDashboard';
import StudentDashboard from './features/dashboard/StudentDashboard';
import ParentDashboard from './features/dashboard/ParentDashboard';

// Feature Pages
import SchoolsPage from './features/schools/SchoolsPage';
import StudentsPage from './features/students/StudentsPage';
import EmployeesPage from './features/employees/EmployeesPage';
import ClassroomsPage from './features/classrooms/ClassroomsPage';
import SubjectsPage from './features/subjects/SubjectsPage';
import AcademicYearsPage from './features/academic-years/AcademicYearsPage';

// Teacher Pages
import TeacherClassesPage from './features/teacher/TeacherClassesPage';
import AttendancePage from './features/teacher/AttendancePage';

// Student Pages
import StudentProfilePage from './features/student/StudentProfilePage';
import StudentAttendancePage from './features/student/StudentAttendancePage';

// Parent Pages
import ParentChildrenPage from './features/parent/ParentChildrenPage';

// Fee Management
import FeesPage from './features/fees/FeesPage';

// Theme configuration
const theme = createTheme({
  palette: {
    primary: {
      main: '#1976d2',
    },
    secondary: {
      main: '#388e3c',
    },
    background: {
      default: '#f5f5f5',
    },
  },
  typography: {
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
  },
  components: {
    MuiButton: {
      styleOverrides: {
        root: {
          textTransform: 'none',
        },
      },
    },
  },
});

// Main App Router Component
const AppRouter = () => {
  const { isAuthenticated, getUserRole, loading } = useAuth();
  const userRole = getUserRole();

  // Show loading while checking authentication
  if (loading) {
    return (
      <Box
        display="flex"
        justifyContent="center"
        alignItems="center"
        minHeight="100vh"
      >
        <CircularProgress />
      </Box>
    );
  }

  // Force login if URL contains ?forceLogin=true
  const urlParams = new URLSearchParams(window.location.search);
  const forceLogin = urlParams.get('forceLogin') === 'true';
  
  if (forceLogin) {
    localStorage.clear();
    sessionStorage.clear();
    window.history.replaceState({}, '', '/login');
  }

  // Redirect authenticated users to their dashboard
  const getDefaultRoute = () => {
    if (!isAuthenticated || forceLogin) return ROUTES.LOGIN;
    
    switch (userRole) {
      case ROLES.SUPERADMIN:
        return ROUTES.SUPERADMIN.DASHBOARD;
      case ROLES.ADMIN:
        return ROUTES.ADMIN.DASHBOARD;
      case ROLES.TEACHER:
        return ROUTES.TEACHER.DASHBOARD;
      case ROLES.STUDENT:
        return ROUTES.STUDENT.DASHBOARD;
      case ROLES.PARENT:
        return ROUTES.PARENT.DASHBOARD;
      default:
        return ROUTES.LOGIN;
    }
  };

  return (
    <Routes>
      {/* Public Routes */}
      <Route 
        path={ROUTES.LOGIN} 
        element={
          isAuthenticated ? (
            <Navigate to={getDefaultRoute()} replace />
          ) : (
            <Login />
          )
        } 
      />

      {/* Protected Routes with Layout */}
      <Route
        path="/*"
        element={
          <ProtectedRoute>
            <DashboardLayout />
          </ProtectedRoute>
        }
      >
        {/* SuperAdmin Routes */}
        <Route
          path="superadmin/dashboard"
          element={
            <ProtectedRoute requiredRoles={[ROLES.SUPERADMIN]}>
              <SuperAdminDashboard />
            </ProtectedRoute>
          }
        />
        <Route
          path="superadmin/schools"
          element={
            <ProtectedRoute requiredRoles={[ROLES.SUPERADMIN]}>
              <SchoolsPage />
            </ProtectedRoute>
          }
        />

        {/* Admin Routes */}
        <Route
          path="admin/dashboard"
          element={
            <ProtectedRoute requiredRoles={[ROLES.ADMIN]}>
              <AdminDashboard />
            </ProtectedRoute>
          }
        />
        <Route
          path="admin/students"
          element={
            <ProtectedRoute requiredRoles={[ROLES.ADMIN]}>
              <StudentsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="admin/employees"
          element={
            <ProtectedRoute requiredRoles={[ROLES.ADMIN]}>
              <EmployeesPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="admin/classrooms"
          element={
            <ProtectedRoute requiredRoles={[ROLES.ADMIN]}>
              <ClassroomsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="admin/subjects"
          element={
            <ProtectedRoute requiredRoles={[ROLES.ADMIN]}>
              <SubjectsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="admin/academic-years"
          element={
            <ProtectedRoute requiredRoles={[ROLES.ADMIN]}>
              <AcademicYearsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="admin/fees"
          element={
            <ProtectedRoute requiredRoles={[ROLES.ADMIN]}>
              <FeesPage />
            </ProtectedRoute>
          }
        />

        {/* Teacher Routes */}
        <Route
          path="teacher/dashboard"
          element={
            <ProtectedRoute requiredRoles={[ROLES.TEACHER]}>
              <TeacherDashboard />
            </ProtectedRoute>
          }
        />
        <Route
          path="teacher/classes"
          element={
            <ProtectedRoute requiredRoles={[ROLES.TEACHER]}>
              <TeacherClassesPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="teacher/attendance"
          element={
            <ProtectedRoute requiredRoles={[ROLES.TEACHER]}>
              <AttendancePage />
            </ProtectedRoute>
          }
        />

        {/* Student Routes */}
        <Route
          path="student/dashboard"
          element={
            <ProtectedRoute requiredRoles={[ROLES.STUDENT]}>
              <StudentDashboard />
            </ProtectedRoute>
          }
        />
        <Route
          path="student/profile"
          element={
            <ProtectedRoute requiredRoles={[ROLES.STUDENT]}>
              <StudentProfilePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="student/attendance"
          element={
            <ProtectedRoute requiredRoles={[ROLES.STUDENT]}>
              <StudentAttendancePage />
            </ProtectedRoute>
          }
        />

        {/* Parent Routes */}
        <Route
          path="parent/dashboard"
          element={
            <ProtectedRoute requiredRoles={[ROLES.PARENT]}>
              <ParentDashboard />
            </ProtectedRoute>
          }
        />
        <Route
          path="parent/children"
          element={
            <ProtectedRoute requiredRoles={[ROLES.PARENT]}>
              <ParentChildrenPage />
            </ProtectedRoute>
          }
        />
      </Route>

      {/* Default redirect */}
      <Route path="/" element={<Navigate to={getDefaultRoute()} replace />} />
      
      {/* 404 and other routes */}
      <Route path="*" element={<Navigate to={getDefaultRoute()} replace />} />
    </Routes>
  );
};

// Main App Component
function App() {
  return (
    <Provider store={store}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <Router future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
          <AppRouter />
        </Router>
        <ToastContainer
          position="top-right"
          autoClose={5000}
          hideProgressBar={false}
          newestOnTop={false}
          closeOnClick
          rtl={false}
          pauseOnFocusLoss
          draggable
          pauseOnHover
        />
      </ThemeProvider>
    </Provider>
  );
}

export default App;
