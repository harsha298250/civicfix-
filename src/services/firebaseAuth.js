import {
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  signOut as firebaseSignOut,
  updateProfile
} from 'firebase/auth';
import { auth, isFirebaseConfigured } from './firebaseConfig.js';
import { apiClient } from './apiClient.js';
import { authService } from './authService.js';

/**
 * CivicFix Platform — Frontend Firebase Authentication Service
 * 
 * Orchestrates:
 * 1. Firebase Email/Password Authentication in the browser
 * 2. Firebase ID token retrieval
 * 3. Token dispatch to Spring Boot backend (POST /api/auth/firebase/session)
 * 4. Establishment of secure server-side session (CIVICFIX_SESSION cookie + CSRF)
 * 5. Coordinated logout across both Firebase and Spring Boot session
 */

export const firebaseAuthService = {
  isConfigured: () => isFirebaseConfigured(),

  /**
   * Authenticate with Firebase Email and Password, then establish Spring Boot session
   */
  signInWithEmail: async (email, password) => {
    if (!isFirebaseConfigured() || !auth) {
      throw new Error('Firebase Authentication is not configured in this environment.');
    }

    if (!email || !email.trim()) {
      throw new Error('Please enter your registered email address.');
    }

    if (!password) {
      throw new Error('Please enter your account password.');
    }

    // 1. Authenticate with Firebase
    const userCredential = await signInWithEmailAndPassword(auth, email.trim(), password);
    const firebaseUser = userCredential.user;

    // 2. Retrieve Firebase ID token (JWT)
    const idToken = await firebaseUser.getIdToken();

    // 3. Send ID token to Spring Boot backend to verify and establish server-side HTTP session
    const response = await apiClient.auth.createFirebaseSession(idToken);
    const backendUser = response?.data || response;

    // 4. Update in-memory frontend session state
    const liveUser = {
      id: String(backendUser.id),
      backendId: String(backendUser.id),
      name: backendUser.name || firebaseUser.displayName || 'Citizen User',
      email: backendUser.email || firebaseUser.email || email.trim(),
      phone: backendUser.phone || '',
      role: backendUser.role || 'CITIZEN',
      roles: backendUser.roles || [backendUser.role || 'CITIZEN'],
      department: backendUser.department || '',
      ward: backendUser.ward || 'Ward 08, Anna Nagar, Chennai',
      trustScore: backendUser.trustScore ?? 90,
      status: backendUser.accountStatus || 'ACTIVE',
      isLiveBackend: true,
      authSource: 'FIREBASE_SESSION'
    };

    authService.setCurrentUser(liveUser);
    return liveUser;
  },

  /**
   * Register a new Citizen account with Firebase Email/Password, then establish Spring Boot session
   */
  registerCitizen: async ({ email, password, name, ward, phone }) => {
    if (!isFirebaseConfigured() || !auth) {
      throw new Error('Firebase Authentication is not configured in this environment.');
    }

    if (!email || !email.trim()) {
      throw new Error('Please enter a valid email address.');
    }

    if (!password || password.length < 8) {
      throw new Error('Password must be at least 8 characters long.');
    }

    // 1. Create account with Firebase
    const userCredential = await createUserWithEmailAndPassword(auth, email.trim(), password);
    const firebaseUser = userCredential.user;

    // Update Firebase display name if provided
    if (name && name.trim()) {
      try {
        await updateProfile(firebaseUser, { displayName: name.trim() });
      } catch (err) {
        // Non-fatal
      }
    }

    // 2. Retrieve Firebase ID token
    const idToken = await firebaseUser.getIdToken();

    // 3. Send ID token + registration metadata to Spring Boot backend
    const response = await apiClient.auth.createFirebaseSession(idToken, {
      name: name?.trim(),
      ward: ward?.trim(),
      phone: phone?.trim()
    });
    const backendUser = response?.data || response;

    // 4. Update in-memory session state
    const liveUser = {
      id: String(backendUser.id),
      backendId: String(backendUser.id),
      name: backendUser.name || name || 'Citizen User',
      email: backendUser.email || firebaseUser.email || email.trim(),
      phone: backendUser.phone || phone || '',
      role: backendUser.role || 'CITIZEN',
      roles: backendUser.roles || ['CITIZEN'],
      department: backendUser.department || 'Resident Community Member',
      ward: backendUser.ward || ward || 'Ward 08, Anna Nagar, Chennai',
      trustScore: backendUser.trustScore ?? 90,
      status: backendUser.accountStatus || 'ACTIVE',
      isLiveBackend: true,
      authSource: 'FIREBASE_SESSION'
    };

    authService.setCurrentUser(liveUser);
    return liveUser;
  },

  /**
   * Terminate Firebase authentication session
   */
  signOut: async () => {
    if (isFirebaseConfigured() && auth) {
      try {
        await firebaseSignOut(auth);
      } catch (err) {
        // Silently ignore sign-out errors on teardown
      }
    }
  }
};
