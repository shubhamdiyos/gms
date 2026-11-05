import apiClient from './apiClient';
import { jwtDecode } from 'jwt-decode';
import MockAuthService from './mockAuthService';

const MOCK_AUTH = import.meta.env.VITE_MOCK_AUTH === 'true';
const CSRF_COOKIE_NAME = 'XSRF-TOKEN';

class AuthService {
  static USER_KEY = 'gms_user';

  // Login user
  static async login(credentials) {
    // --- MOCK MODE: bypass backend when VITE_MOCK_AUTH=true ---
    if (MOCK_AUTH) {
      console.info('🟡 [MockAuth] Backend bypassed — using mock login');
      const result = await MockAuthService.login(credentials);
      // Mock mode still uses localStorage for simplicity (not production)
      localStorage.setItem('gms_token', result.token);
      this.setUser(result.user);
      return result;
    }
    // --- END MOCK MODE ---

    try {
      console.log('🔐 Attempting login with:', { username: credentials.username });
      // Login is a POST, so the CSRF cookie must exist before it is sent.
      await this.fetchCsrfToken();
      // Credentials are sent with the request so the backend can set the HttpOnly cookie
      const response = await apiClient.post('/auth/login', credentials, {
        withCredentials: true,
      });
      console.log('✅ Login response:', response.data);
      const { requirePasswordChange } = response.data;
      
      // After successful login, fetch the CSRF token
      await this.fetchCsrfToken();
      
      // Decode user info from the CSRF token response (we'll get it from the cookie)
      // For now, we need to get user info another way - let's call a /me endpoint
      // or decode from the cookie. Since the JWT is HttpOnly, we can't read it.
      // We'll store minimal user info from the login response.
      const user = {
        username: credentials.username,
        requirePasswordChange: requirePasswordChange || false,
      };
      
      // Fetch full user info from /users/me endpoint
      try {
        const userResponse = await apiClient.get('/users/me', {
          withCredentials: true,
        });
        Object.assign(user, userResponse.data);
      } catch (error) {
        console.warn('Could not fetch user details:', error.message);
      }
      
      this.setUser(user);
      return { user };
    } catch (error) {
      console.error('❌ Login error:', error.response?.data || error.message);
      throw error;
    }
  }

  // Fetch CSRF token from backend and ensure it's in the cookie
  static async fetchCsrfToken() {
    try {
      const response = await apiClient.get('/auth/csrf-token', {
        withCredentials: true,
      });
      // The CSRF token is automatically set in the XSRF-TOKEN cookie by Spring Security
      // We don't need to store it explicitly; the apiClient will read it from the cookie
      console.log('✅ CSRF token fetched');
      return response.data;
    } catch (error) {
      console.error('❌ Failed to fetch CSRF token:', error.message);
      throw error;
    }
  }

  // Read CSRF token from cookie
  static getCsrfTokenFromCookie() {
    const cookies = document.cookie.split(';');
    for (let cookie of cookies) {
      const [name, value] = cookie.trim().split('=');
      if (name === CSRF_COOKIE_NAME) {
        return decodeURIComponent(value);
      }
    }
    return null;
  }

  // Change password
  static async changePassword(passwordData) {
    try {
      const response = await apiClient.post('/auth/change-password', passwordData, {
        withCredentials: true,
      });
      return response.data;
    } catch (error) {
      throw error;
    }
  }

  // Logout user
  static async logout() {
    try {
      // Call backend logout endpoint to clear the HttpOnly cookie
      await apiClient.post('/auth/logout', {}, {
        withCredentials: true,
      });
    } catch (error) {
      console.warn('Backend logout failed, clearing local state anyway:', error.message);
    } finally {
      // Always clear local state, even if backend call fails
      this.clearUser();
      if (MOCK_AUTH) {
        localStorage.removeItem('gms_token');
      }
    }
  }

  // Token management (deprecated - cookies handle this now)
  static setToken(token) {
    // Only used in mock mode
    if (MOCK_AUTH) {
      localStorage.setItem('gms_token', token);
    }
  }

  static getToken() {
    // Only used in mock mode
    if (MOCK_AUTH) {
      return localStorage.getItem('gms_token');
    }
    return null; // Real mode uses HttpOnly cookies
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

  static clearUser() {
    localStorage.removeItem(this.USER_KEY);
  }

  // Check if user is authenticated
  // In cookie mode, we check if the user info exists in localStorage
  // The actual authentication is validated by the backend on each request
  static isAuthenticated() {
    if (MOCK_AUTH) {
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
    
    // Real mode: check if we have user info stored
    const user = this.getUser();
    return user !== null;
  }

  // Role questions are answered by useAuth.hasRole and useAuth.getPrimaryRole, which test
  // membership. Nothing here reads `roles[0]`: the JWT claim is sorted alphabetically
  // (JwtTokenProvider.rolesClaim), so index 0 is the first role name, not the senior one.
}

export default AuthService;
