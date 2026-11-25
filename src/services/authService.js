import { INITIAL_USERS } from './mockData.js';
import { apiClient } from './apiClient.js';
import { firebaseAuthService } from './firebaseAuth.js';
import {
  formatIndianPhone,
  normalizeIndianPhone,
  normalizeEmail
} from '../utils/phoneUtils.js';

// In-memory active session user state (no persistent credentials or sessions stored in localStorage)
let activeSessionUser = null;

/**
 * Helper to check whether demo mode is actively enabled via environment variable
 */
export const isDemoModeEnabled = () => {
  if (typeof import.meta !== 'undefined' && import.meta.env && import.meta.env.VITE_DEMO_MODE) {
    return import.meta.env.VITE_DEMO_MODE === 'true';
  }
  if (typeof process !== 'undefined' && process.env && process.env.VITE_DEMO_MODE) {
    return process.env.VITE_DEMO_MODE === 'true';
  }
  return false;
};

export const authService = {
  /**
   * Get active user session in memory or return null if unauthenticated.
   */
  getCurrentUser: () => {
    return activeSessionUser;
  },

  /**
   * Set active user session (or clear if null)
   */
  setCurrentUser: (user) => {
    activeSessionUser = user || null;
    return activeSessionUser;
  },

  /**
   * Terminate active user session across both Firebase and Spring Boot
   */
  logout: async () => {
    activeSessionUser = null;
    try {
      localStorage.removeItem('civicfix_current_user_v1');
    } catch (e) {}
    try {
      if (firebaseAuthService && typeof firebaseAuthService.signOut === 'function') {
        await firebaseAuthService.signOut();
      }
    } catch (e) {
      // Ignore client-side Firebase teardown error
    }
    try {
      await apiClient.auth.logout();
    } catch (e) {
      // Backend may be offline or unreachable, ignore network failure on logout
    }
  },

  /**
   * Check active server session via GET /api/auth/me
   */
  checkSession: async () => {
    try {
      if (firebaseAuthService && typeof firebaseAuthService.waitForInitialAuth === 'function') {
        try {
          await firebaseAuthService.waitForInitialAuth();
        } catch (e) {
          // Non-fatal if Firebase auth initialization times out
        }
      }
      const res = await apiClient.auth.getCurrentUser();
      const backendUser = res?.data || res;
      if (backendUser && backendUser.id) {
        const liveUser = {
          id: String(backendUser.id),
          name: backendUser.name || 'Citizen User',
          email: backendUser.email || '',
          phone: backendUser.phone || '',
          role: backendUser.role || 'CITIZEN',
          roles: backendUser.roles || [backendUser.role || 'CITIZEN'],
          department: backendUser.department || '',
          ward: backendUser.ward || 'Ward 08, Anna Nagar, Chennai',
          trustScore: backendUser.trustScore ?? 90,
          status: backendUser.accountStatus || 'ACTIVE',
          isLiveBackend: true,
          authSource: 'POSTGRESQL_BACKEND'
        };
        authService.setCurrentUser(liveUser);
        return liveUser;
      }
      authService.setCurrentUser(null);
      return null;
    } catch (err) {
      authService.setCurrentUser(null);
      if (err.status === 401 || err.status === 403) {
        return null;
      }
      const unreachableError = new Error('Cannot reach the server. Nothing was saved.');
      unreachableError.isServerUnreachable = true;
      unreachableError.status = err.status || 0;
      throw unreachableError;
    }
  },

  /**
   * Switch role for demo & rapid testing (explicitly non-authoritative client persona switch).
   * Gated strictly behind VITE_DEMO_MODE === 'true'.
   */
  switchRole: (role) => {
    if (!isDemoModeEnabled()) {
      throw new Error('Demo mode is disabled. Role switching is not permitted.');
    }
    const targetUser = INITIAL_USERS.find((u) => u.role === role) || INITIAL_USERS[0];
    const demoPersona = {
      ...targetUser,
      isLiveBackend: false,
      isDemoMode: true,
      authSource: 'DEMO_PERSONA_SWITCH'
    };
    return authService.setCurrentUser(demoPersona);
  },

  /**
   * Primary User Authentication
   * Routes through Firebase Authentication (Email/Password) when Firebase is configured.
   * Authoritative user profile and roles are resolved from Neon PostgreSQL via POST /api/auth/firebase/session.
   * Falls back to server-authoritative POST /api/auth/login if Firebase is unconfigured (e.g. offline testing).
   */
  login: async (identifier, password, rememberMe = false) => {
    if (!identifier || !identifier.trim()) {
      throw new Error('Please enter your registered email address or Indian mobile number.');
    }
    if (!password) {
      throw new Error('Please enter your account password.');
    }

    const trimmed = identifier.trim();

    // 1. Primary Authentication Path: Firebase Email/Password
    if (firebaseAuthService && typeof firebaseAuthService.isConfigured === 'function' && firebaseAuthService.isConfigured()) {
      return firebaseAuthService.signInWithEmail(trimmed, password, rememberMe);
    }

    // 2. Fallback Path: Server-Authoritative Spring Boot / PostgreSQL login (for offline tests & unconfigured dev)
    try {
      const response = await apiClient.auth.login(trimmed, password, rememberMe);
      const backendUser = response?.data || response;

      const legacyIdMap = {
        '00000000-0000-0000-0000-000000000001': 'usr-001',
        '00000000-0000-0000-0000-000000000002': 'usr-002',
        '00000000-0000-0000-0000-000000000003': 'usr-003',
        '00000000-0000-0000-0000-000000000004': 'usr-004'
      };

      const user = {
        id: legacyIdMap[String(backendUser.id)] || String(backendUser.id),
        backendId: String(backendUser.id),
        name: backendUser.name || 'Citizen User',
        email: backendUser.email || trimmed,
        phone: backendUser.phone || '',
        role: backendUser.role || 'CITIZEN',
        roles: backendUser.roles || [backendUser.role || 'CITIZEN'],
        department: backendUser.department || '',
        ward: backendUser.ward || 'Ward 08, Anna Nagar, Chennai',
        trustScore: backendUser.trustScore ?? 90,
        status: backendUser.accountStatus || 'ACTIVE',
        isLiveBackend: true,
        authSource: 'POSTGRESQL_BACKEND'
      };

      if (backendUser.accountStatus !== 'PENDING_VERIFICATION') {
        authService.setCurrentUser(user);
      }
      return user;
    } catch (apiError) {
      if (apiError.status === 401 || apiError.status === 403 || apiError.status === 400 || apiError.status === 429) {
        const errorMsg = apiError.data?.message || apiError.message || 'Invalid credentials. Please verify your email or mobile number and password.';
        throw new Error(errorMsg);
      }

      throw new Error('Cannot reach the server. Nothing was saved.');
    }
  },

  /**
   * Log in or register with verified Google ID token
   */
  loginWithGoogle: async (idToken, rememberMe = false) => {
    if (!idToken) {
      throw new Error('Google authentication token is required.');
    }

    try {
      const response = await apiClient.auth.loginWithGoogle({ idToken, rememberMe });
      const backendUser = response?.data || response;

      const user = {
        id: String(backendUser.id),
        backendId: String(backendUser.id),
        name: backendUser.name || 'Citizen User',
        email: backendUser.email || '',
        phone: backendUser.phone || '',
        role: backendUser.role || 'CITIZEN',
        roles: backendUser.roles || [backendUser.role || 'CITIZEN'],
        department: backendUser.department || '',
        ward: backendUser.ward || 'Ward 08, Anna Nagar, Chennai',
        trustScore: backendUser.trustScore ?? 90,
        status: backendUser.accountStatus || 'ACTIVE',
        isLiveBackend: true,
        authSource: 'GOOGLE_OAUTH'
      };

      authService.setCurrentUser(user);
      return user;
    } catch (apiError) {
      if (apiError.status === 401 || apiError.status === 403 || apiError.status === 400 || apiError.status === 429) {
        const errorMsg = apiError.data?.message || apiError.message || 'Google authentication failed. Please try again.';
        throw new Error(errorMsg);
      }

      throw new Error('Cannot reach the server. Nothing was saved.');
    }
  },

  /**
   * Verify Citizen Email with single-use token
   */
  verifyEmail: async (token) => {
    if (!token) {
      throw new Error('Verification token is missing.');
    }
    return await apiClient.auth.verifyEmail(token);
  },

  /**
   * Resend Citizen Email Verification link
   */
  resendVerification: async (email) => {
    if (!email) {
      throw new Error('Email address is required to resend verification.');
    }
    return await apiClient.auth.resendVerification(email);
  },

  /**
   * Log in via Firebase Email/Password Authentication and establish Spring Boot session
   */
  loginWithFirebase: async (email, password, rememberMe = false) => {
    return firebaseAuthService.signInWithEmail(email, password, rememberMe);
  },

  /**
   * Register Citizen via Firebase Email/Password Authentication and establish Spring Boot session
   */
  registerWithFirebase: async (formData) => {
    return firebaseAuthService.registerCitizen(formData);
  },

  /**
   * Complete Citizen Registration against Spring Boot PostgreSQL Backend
   */
  registerCitizen: async (formData) => {
    const { name, email, phone, ward, password } = formData;

    if (!name || !name.trim()) {
      throw new Error('Full legal name is required.');
    }

    const formattedPhone = phone ? formatIndianPhone(phone) : '';
    const canonicalPhone = phone ? normalizeIndianPhone(phone) : '';
    const canonicalEmail = email ? normalizeEmail(email) : '';

    try {
      const res = await apiClient.auth.register({
        name: name.trim(),
        email: canonicalEmail || null,
        phone: formattedPhone || null,
        ward: ward || 'Ward 08, Anna Nagar, Chennai',
        password: password || 'CivicSecure#2026!'
      });

      const backendUser = res?.data || res;
      const user = {
        id: backendUser.id ? String(backendUser.id) : 'usr-backend',
        name: backendUser.name || name.trim(),
        email: backendUser.email || canonicalEmail,
        phone: backendUser.phone || formattedPhone,
        role: 'CITIZEN',
        roles: ['CITIZEN'],
        department: backendUser.department || 'Resident Community Member',
        ward: backendUser.ward || ward || 'Ward 08, Anna Nagar, Chennai',
        trustScore: backendUser.trustScore ?? 90,
        status: backendUser.accountStatus || 'ACTIVE',
        avatar: name
          .trim()
          .split(' ')
          .map((n) => n[0])
          .join('')
          .substring(0, 2)
          .toUpperCase(),
        isLiveBackend: true,
        authSource: 'POSTGRESQL_BACKEND'
      };

      if (backendUser.accountStatus !== 'PENDING_VERIFICATION') {
        authService.setCurrentUser(user);
      }
      return user;
    } catch (apiError) {
      if (apiError.status === 400 || apiError.status === 409) {
        throw new Error(apiError.data?.message || apiError.message || 'Registration rejected by server.');
      }
      throw new Error('Cannot reach the server. Nothing was saved.');
    }
  },

  forgotPassword: async (email) => {
    if (firebaseAuthService.isConfigured()) {
      try {
        await firebaseAuthService.sendPasswordReset(email);
        return { message: 'Password reset link sent successfully via Firebase.' };
      } catch (err) {
        // Fallback to backend API if Firebase reset fails
      }
    }
    return await apiClient.auth.forgotPassword(email);
  },

  resetPassword: async (token, newPassword) => {
    if (firebaseAuthService.isConfigured() && token && token.length > 50) {
      try {
        await firebaseAuthService.confirmPasswordResetCode(token, newPassword);
        return { message: 'Password has been reset successfully via Firebase.' };
      } catch (err) {
        // Fallback to backend API
      }
    }
    return await apiClient.auth.resetPassword({ token, newPassword });
  }
};

export default authService;
