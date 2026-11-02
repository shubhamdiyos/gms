import { useSelector, useDispatch } from 'react-redux';
import { useNavigate } from 'react-router-dom';
import { 
  loginStart, 
  loginSuccess, 
  loginFailure, 
  logout as logoutAction 
} from '../store/slices/authSlice';
import AuthService from '../services/authService';
import { ROUTES, ROLES } from '../constants';
import { toast } from 'react-toastify';

// Breadth of the portal a role owns: platform > school > school-wide money > classes > children > self.
export const ROLE_PRECEDENCE = [
  ROLES.SUPERADMIN,
  ROLES.ADMIN,
  ROLES.ACCOUNTANT,
  ROLES.TEACHER,
  ROLES.PARENT,
  ROLES.STUDENT,
];

export const DASHBOARD_BY_ROLE = {
  [ROLES.SUPERADMIN]: ROUTES.SUPERADMIN.DASHBOARD,
  [ROLES.ADMIN]: ROUTES.ADMIN.DASHBOARD,
  // ACCOUNTANT owns no portal: /admin/fees is the only screen FeeController and SecurityConfig admit it to.
  [ROLES.ACCOUNTANT]: ROUTES.ADMIN.FEES,
  [ROLES.TEACHER]: ROUTES.TEACHER.DASHBOARD,
  [ROLES.PARENT]: ROUTES.PARENT.DASHBOARD,
  [ROLES.STUDENT]: ROUTES.STUDENT.DASHBOARD,
};

export const getDashboardRoute = (role) => DASHBOARD_BY_ROLE[role] || null;

const normalizeRole = (role) => String(role).replace(/^ROLE_/, '').toUpperCase();

const getPrimaryRole = (roles) => {
  if (!Array.isArray(roles) || roles.length === 0) return null;
  const normalized = roles.map(normalizeRole);
  return ROLE_PRECEDENCE.find((role) => normalized.includes(role)) || null;
};

export const useAuth = () => {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const auth = useSelector((state) => state.auth);

  const login = async (credentials) => {
    try {
      dispatch(loginStart());
      
      const result = await AuthService.login(credentials);

      const userRole = getPrimaryRole(result.user?.roles);
      if (!userRole) {
        dispatch(logoutAction());
        const errorMessage = 'No portal role assigned to this account';
        dispatch(loginFailure(errorMessage));
        toast.error(errorMessage);
        return result;
      }

      dispatch(loginSuccess(result));
      
      navigate(DASHBOARD_BY_ROLE[userRole]);
      
      toast.success('Login successful!');
      return result;
    } catch (error) {
      const errorMessage = error.response?.data?.message || 'Login failed';
      dispatch(loginFailure(errorMessage));
      toast.error(errorMessage);
      throw error;
    }
  };

  const logout = () => {
    dispatch(logoutAction());
    navigate(ROUTES.LOGIN);
    toast.info('Logged out successfully');
  };

  const hasRole = (requiredRoles) => {
    if (!auth.user || !Array.isArray(auth.user.roles) || auth.user.roles.length === 0) return false;
    
    const userRoles = auth.user.roles.map(normalizeRole);
    const required = (Array.isArray(requiredRoles) ? requiredRoles : [requiredRoles])
      .filter(Boolean)
      .map(normalizeRole);
    
    return required.some(role => userRoles.includes(role));
  };

  const hasAnyRole = (roles) => {
    return hasRole(roles);
  };

  const hasAllRoles = (roles) => {
    if (!auth.user || !Array.isArray(auth.user.roles) || auth.user.roles.length === 0) return false;
    
    const userRoles = auth.user.roles.map(normalizeRole);
    const required = (Array.isArray(roles) ? roles : [roles])
      .filter(Boolean)
      .map(normalizeRole);
    
    return required.every(role => userRoles.includes(role));
  };

  const getUserRole = () => {
    return getPrimaryRole(auth.user?.roles);
  };

  const getUser = () => {
    return auth.user;
  };

  const isAuthenticated = () => {
    // Check both Redux state and AuthService
    const hasValidToken = AuthService.isAuthenticated();
    const hasAuthState = auth.isAuthenticated;
    
    // If we have a valid token but Redux state is false, update Redux
    if (hasValidToken && !hasAuthState) {
      const user = AuthService.getUser();
      const token = AuthService.getToken();
      if (user && token) {
        dispatch(loginSuccess({ user, token }));
        return true;
      }
    }
    
    return hasValidToken && hasAuthState;
  };

  return {
    // State
    user: auth.user,
    token: auth.token,
    isAuthenticated: isAuthenticated(),
    loading: auth.loading,
    error: auth.error,
    
    // Actions
    login,
    logout,
    
    // Utilities
    hasRole,
    hasAnyRole,
    hasAllRoles,
    getUserRole,
    getPrimaryRole,
    getUser,
  };
};
