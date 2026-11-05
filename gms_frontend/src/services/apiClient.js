import axios from 'axios';
import { toast } from 'react-toastify';

const CSRF_COOKIE_NAME = 'XSRF-TOKEN';
const CSRF_HEADER_NAME = 'X-XSRF-TOKEN';

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

// Read CSRF token from cookie
const getCsrfTokenFromCookie = () => {
  const cookies = document.cookie.split(';');
  for (let cookie of cookies) {
    const [name, value] = cookie.trim().split('=');
    if (name === CSRF_COOKIE_NAME) {
      return decodeURIComponent(value);
    }
  }
  return null;
};

// Create axios instance
const apiClient = axios.create({
  baseURL: getApiBaseUrl(),
  headers: {
    'Content-Type': 'application/json',
  },
  // Enable credentials for cookie-based authentication
  withCredentials: true,
});

// Request interceptor to add CSRF token header
apiClient.interceptors.request.use(
  (config) => {
    // Read CSRF token from cookie and add to header for state-changing requests
    const method = config.method?.toUpperCase();
    if (method === 'POST' || method === 'PUT' || method === 'PATCH' || method === 'DELETE') {
      const csrfToken = getCsrfTokenFromCookie();
      if (csrfToken) {
        config.headers[CSRF_HEADER_NAME] = csrfToken;
      }
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
      // Cookie-based auth: clear local user state and redirect
      localStorage.removeItem('gms_user');
      window.location.href = '/login';
      toast.error('Session expired. Please login again.');
    } else if (error.response?.status === 403) {
      // Check if it's a CSRF error (403 with CSRF message)
      if (error.response?.data?.error?.toLowerCase().includes('csrf')) {
        toast.error('Security token mismatch. Please refresh the page and try again.');
      } else {
        toast.error('Access denied. You do not have permission to perform this action.');
      }
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
