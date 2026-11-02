import axios from 'axios';
import { toast } from 'react-toastify';

// Get API base URL from environment variable or use default
const getApiBaseUrl = () => {
  const customUrl = import.meta.env.VITE_API_BASE_URL;
  if (customUrl) {
    return customUrl;
  }
  if (import.meta.env.PROD) {
    return '/api/v1';
  }
  return 'http://localhost:8080/api/v1';
};

// Create axios instance
const apiClient = axios.create({
  baseURL: getApiBaseUrl(),
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor to add auth token
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('gms_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor for error handling
apiClient.interceptors.response.use(
  (response) => {
    return response;
  },
  (error) => {
    // A 401 that answers a login attempt is not an expired session. AuthController replies to a
    // bad credential with a bare 401 and no body (`AuthController.java:76` and `:108`), so the
    // branch below would clear storage, hard-reload the very page the user is typing on — losing
    // the username — and say "Session expired" to someone who was never signed in, while
    // useAuth.login's own catch (`useAuth.js:69-73`) toasts the real reason. One call, one voice.
    const config = error.config ?? {};
    const isLoginAttempt = config.method === 'post' && /\/auth\/login\/?$/.test(config.url ?? '');
    if (isLoginAttempt) {
      return Promise.reject(error);
    }

    if (error.response?.status === 401) {
      // Token expired or invalid
      localStorage.removeItem('gms_token');
      localStorage.removeItem('gms_user');
      window.location.href = '/login';
      toast.error('Session expired. Please login again.');
    } else if (error.response?.status === 403) {
      toast.error('Access denied. You do not have permission to perform this action.');
    } else if (error.response?.status >= 500) {
      toast.error('Server error. Please try again later.');
    } else if (error.response?.data?.message) {
      toast.error(error.response.data.message);
    } else {
      toast.error('An unexpected error occurred.');
    }
    
    return Promise.reject(error);
  }
);

export default apiClient;
