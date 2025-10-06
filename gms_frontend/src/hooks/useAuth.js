import { useSelector, useDispatch } from 'react-redux';
import { useNavigate } from 'react-router-dom';
import { 
  loginStart, 
  loginSuccess, 
  loginFailure, 
  logout as logoutAction 
} from '../store/slices/authSlice';
import { useLoginMutation } from '../store/api/apiSlice';
import AuthService from '../services/authService';
import { ROUTES, ROLES } from '../constants';
import { toast } from 'react-toastify';

export const useAuth = () => {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const auth = useSelector((state) => state.auth);
  const [loginMutation] = useLoginMutation();

  const login = async (credentials) => {
    try {
      dispatch(loginStart());
      
      const result = await AuthService.login(credentials);
      
      dispatch(loginSuccess(result));
      
      // Role-based navigation
      const userRole = result.user.roles[0];
      switch (userRole) {
        case ROLES.SUPERADMIN:
          navigate(ROUTES.SUPERADMIN.DASHBOARD);
          break;
        case ROLES.ADMIN:
          navigate(ROUTES.ADMIN.DASHBOARD);
          break;
        case ROLES.TEACHER:
          navigate(ROUTES.TEACHER.DASHBOARD);
          break;
        case ROLES.STUDENT:
          navigate(ROUTES.STUDENT.DASHBOARD);
          break;
        case ROLES.PARENT:
          navigate(ROUTES.PARENT.DASHBOARD);
          break;
        default:
          navigate(ROUTES.DASHBOARD);
      }
      
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
    if (!auth.user || !auth.user.roles) return false;
    
    const userRoles = Array.isArray(auth.user.roles) ? auth.user.roles : [auth.user.roles];
    const required = Array.isArray(requiredRoles) ? requiredRoles : [requiredRoles];
    
    return required.some(role => userRoles.includes(role));
  };

  const hasAnyRole = (roles) => {
    return hasRole(roles);
  };

  const hasAllRoles = (roles) => {
    if (!auth.user || !auth.user.roles) return false;
    
    const userRoles = Array.isArray(auth.user.roles) ? auth.user.roles : [auth.user.roles];
    const required = Array.isArray(roles) ? roles : [roles];
    
    return required.every(role => userRoles.includes(role));
  };

  const getUserRole = () => {
    return auth.user?.roles?.[0] || null;
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
    getUser,
  };
};
