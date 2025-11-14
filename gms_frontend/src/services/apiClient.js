import axios from 'axios';
import { toast } from 'react-toastify';

// Get API base URL from environment variable or use default
const getApiBaseUrl = () => {
  // Use environment variable if provided (recommended for production)
  const customUrl = import.meta.env.VITE_API_BASE_URL;
  
  if (customUrl) {
    return customUrl;
  }
  
  // Default backend URL - using HTTP for now
  // IMPORTANT: For production on Vercel, you MUST set VITE_API_BASE_URL with HTTPS
  // because Vercel serves over HTTPS and blocks HTTP requests (Mixed Content policy)
  // Options:
  // 1. Set up SSL certificate on your EC2 backend
  // 2. Use AWS Application Load Balancer with SSL
  // 3. Use AWS API Gateway with custom domain and SSL
  // 4. Use a reverse proxy (nginx) with Let's Encrypt SSL
  const backendHost = 'ec2-65-0-109-47.ap-south-1.compute.amazonaws.com:8080';
  return `http://${backendHost}/api/v1`;
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
