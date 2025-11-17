// API Configuration
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

export const API_BASE_URL = getApiBaseUrl();

// Storage Keys
export const STORAGE_KEYS = {
  TOKEN: 'gms_token',
  USER: 'gms_user',
  THEME: 'gms_theme',
};

// User Roles
export const ROLES = {
  SUPERADMIN: 'SUPERADMIN',
  ADMIN: 'ADMIN',
  TEACHER: 'TEACHER',
  STUDENT: 'STUDENT',
  PARENT: 'PARENT',
};

// Route Paths
export const ROUTES = {
  LOGIN: '/login',
  DASHBOARD: '/dashboard',
  SUPERADMIN: {
    DASHBOARD: '/superadmin/dashboard',
    SCHOOLS: '/superadmin/schools',
    REPORTS: '/superadmin/reports',
    SETTINGS: '/superadmin/settings',
  },
  ADMIN: {
    DASHBOARD: '/admin/dashboard',
    STUDENTS: '/admin/students',
    EMPLOYEES: '/admin/employees',
    CLASSROOMS: '/admin/classrooms',
    SUBJECTS: '/admin/subjects',
    ACADEMIC_YEARS: '/admin/academic-years',
    FEES: '/admin/fees',
    EXAMS: '/admin/exams',
    REPORTS: '/admin/reports',
    ANNOUNCEMENTS: '/admin/announcements',
  },
  TEACHER: {
    DASHBOARD: '/teacher/dashboard',
    CLASSES: '/teacher/classes',
    ATTENDANCE: '/teacher/attendance',
    GRADES: '/teacher/grades',
    STUDENTS: '/teacher/students',
  },
  STUDENT: {
    DASHBOARD: '/student/dashboard',
    PROFILE: '/student/profile',
    ATTENDANCE: '/student/attendance',
    RESULTS: '/student/results',
    FEES: '/student/fees',
    TIMETABLE: '/student/timetable',
  },
  PARENT: {
    DASHBOARD: '/parent/dashboard',
    CHILDREN: '/parent/children',
    ATTENDANCE: '/parent/attendance',
    RESULTS: '/parent/results',
    FEES: '/parent/fees',
  },
};

// Theme Configuration
export const THEME = {
  COLORS: {
    PRIMARY: '#1976d2',
    SECONDARY: '#388e3c',
    ACCENT: '#f57c00',
    ERROR: '#d32f2f',
    WARNING: '#ed6c02',
    INFO: '#0288d1',
    SUCCESS: '#2e7d32',
  },
  BREAKPOINTS: {
    XS: 0,
    SM: 600,
    MD: 900,
    LG: 1200,
    XL: 1536,
  },
};

// Status Types
export const STATUS = {
  ACTIVE: '1',
  INACTIVE: '0',
  PENDING: 'PENDING',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
};

// Notification Types
export const NOTIFICATION_TYPES = {
  SUCCESS: 'success',
  ERROR: 'error',
  WARNING: 'warning',
  INFO: 'info',
};

// Date Formats
export const DATE_FORMATS = {
  DISPLAY: 'DD/MM/YYYY',
  API: 'YYYY-MM-DD',
  DATETIME: 'DD/MM/YYYY HH:mm',
  TIME: 'HH:mm',
};

// Pagination
export const PAGINATION = {
  DEFAULT_PAGE_SIZE: 10,
  PAGE_SIZE_OPTIONS: [10, 25, 50, 100],
};

// File Upload
export const FILE_UPLOAD = {
  MAX_SIZE: 5 * 1024 * 1024, // 5MB
  ALLOWED_TYPES: ['image/jpeg', 'image/png', 'image/gif', 'application/pdf'],
};

// Validation Messages
export const VALIDATION_MESSAGES = {
  REQUIRED: 'This field is required',
  EMAIL: 'Please enter a valid email address',
  MIN_LENGTH: (min) => `Minimum ${min} characters required`,
  MAX_LENGTH: (max) => `Maximum ${max} characters allowed`,
  PHONE: 'Please enter a valid phone number',
  PASSWORD: 'Password must be at least 8 characters with uppercase, lowercase, number and special character',
};
