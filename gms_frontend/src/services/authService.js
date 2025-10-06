import apiClient from './apiClient';
import { jwtDecode } from 'jwt-decode';

class AuthService {
  static TOKEN_KEY = 'gms_token';
  static USER_KEY = 'gms_user';

  // Login user
  static async login(credentials) {
    try {
      console.log('🔐 Attempting login with:', { username: credentials.username });
      const response = await apiClient.post('/auth/login', credentials);
      console.log('✅ Login response:', response.data);
      const { accessToken, requirePasswordChange } = response.data;
      
      if (accessToken) {
        this.setToken(accessToken);
        const decodedToken = this.decodeToken(accessToken);
        const user = {
          username: decodedToken.sub,
          roles: decodedToken.roles || [],
          schoolId: decodedToken.schoolId,
          empId: decodedToken.empId,
          requirePasswordChange: requirePasswordChange || false,
        };
        this.setUser(user);
        return { token: accessToken, user };
      }
      
      throw new Error('No token received');
    } catch (error) {
      console.error('❌ Login error:', error.response?.data || error.message);
      throw error;
    }
  }

  // Change password
  static async changePassword(passwordData) {
    try {
      const response = await apiClient.post('/auth/change-password', passwordData);
      return response.data;
    } catch (error) {
      throw error;
    }
  }

  // Logout user
  static logout() {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
  }

  // Token management
  static setToken(token) {
    localStorage.setItem(this.TOKEN_KEY, token);
  }

  static getToken() {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  static decodeToken(token) {
    try {
      return jwtDecode(token);
    } catch (error) {
      console.error('Error decoding token:', error);
      return null;
    }
  }

  // User management
  static setUser(user) {
    localStorage.setItem(this.USER_KEY, JSON.stringify(user));
  }

  static getUser() {
    const user = localStorage.getItem(this.USER_KEY);
    return user ? JSON.parse(user) : null;
  }

  // Check if user is authenticated
  static isAuthenticated() {
    const token = this.getToken();
    if (!token) return false;

    try {
      const decodedToken = this.decodeToken(token);
      const currentTime = Date.now() / 1000;
      return decodedToken.exp > currentTime;
    } catch (error) {
      return false;
    }
  }

  // Check if user has required role
  static hasRole(requiredRoles) {
    const user = this.getUser();
    if (!user || !user.roles) return false;
    
    return requiredRoles.some(role => user.roles.includes(role));
  }

  // Get user's primary role
  static getUserRole() {
    const user = this.getUser();
    return user?.roles?.[0] || null;
  }
}

export default AuthService;
