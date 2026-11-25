import {
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  signOut as firebaseSignOut,
  updateProfile,
  GoogleAuthProvider,
  signInWithPopup,
  sendPasswordResetEmail,
  confirmPasswordReset,
  sendEmailVerification,
  applyActionCode,
  setPersistence,
  browserLocalPersistence,
  browserSessionPersistence,
  onAuthStateChanged,
  reload
} from 'firebase/auth';
import { auth, isFirebaseConfigured } from './firebaseConfig.js';
import { apiClient } from './apiClient.js';
import { authService } from './authService.js';

/**
 * CivicFix Platform — Frontend Firebase Authentication Service
 * 
 * Orchestrates:
 * 1. Firebase Google Sign-In & Email/Password Authentication in the browser
 * 2. Firebase ID token retrieval (JWT)
 * 3. Token dispatch to Spring Boot backend (POST /api/auth/firebase/session)
 * 4. Password reset, email verification, and session persistence management
 * 5. Coordinated logout across both Firebase and Spring Boot session
 */

export const firebaseAuthService = {
  isConfigured: () => isFirebaseConfigured(),

  /**
   * Retrieve current Firebase user ID token if signed in
   */
  getIdToken: async (forceRefresh = false) => {
    if (!auth || !auth.currentUser) return null;
    return auth.currentUser.getIdToken(forceRefresh);
  },

  /**
   * Sign in with Google via Firebase Authentication popup
   */
  signInWithGoogle: async (rememberMe = false) => {
    if (!isFirebaseConfigured() || !auth) {
      throw new Error('Firebase Authentication is not configured in this environment.');
    }

    try {
      if (rememberMe) {
        await setPersistence(auth, browserLocalPersistence);
      } else {
        await setPersistence(auth, browserSessionPersistence);
      }
    } catch (e) {
      // Non-fatal if persistence cannot be adjusted
    }

    const provider = new GoogleAuthProvider();
    provider.addScope('email');
    provider.addScope('profile');

    let userCredential;
    try {
      userCredential = await signInWithPopup(auth, provider);
    } catch (fbErr) {
      const code = fbErr.code || '';
      if (code === 'auth/popup-closed-by-user') {
        throw new Error('Google sign-in popup was closed before completing authentication.');
      } else if (code === 'auth/popup-blocked') {
        throw new Error('Google sign-in popup was blocked by your browser. Please allow popups for this site.');
      } else {
        throw new Error(fbErr.message || 'Google sign-in failed. Please try again.');
      }
    }
    const firebaseUser = userCredential.user;
    const idToken = await firebaseUser.getIdToken();

    const response = await apiClient.auth.createFirebaseSession(idToken);
    const backendUser = response?.data || response;

    const liveUser = {
      id: String(backendUser.id),
      backendId: String(backendUser.id),
      name: backendUser.name || firebaseUser.displayName || 'Citizen User',
      email: backendUser.email || firebaseUser.email || '',
      phone: backendUser.phone || '',
      role: backendUser.role || 'CITIZEN',
      roles: backendUser.roles || [backendUser.role || 'CITIZEN'],
      department: backendUser.department || '',
      ward: backendUser.ward || 'Ward 08, Anna Nagar, Chennai',
      trustScore: backendUser.trustScore ?? 90,
      status: backendUser.accountStatus || 'ACTIVE',
      isLiveBackend: true,
      authSource: 'FIREBASE_GOOGLE_SESSION'
    };

    authService.setCurrentUser(liveUser);
    return liveUser;
  },

  /**
   * Authenticate with Firebase Email and Password, then establish Spring Boot session
   */
  signInWithEmail: async (email, password, rememberMe = false) => {
    if (!isFirebaseConfigured() || !auth) {
      throw new Error('Firebase Authentication is not configured in this environment.');
    }

    if (!email || !email.trim()) {
      throw new Error('Please enter your registered email address.');
    }

    if (!password) {
      throw new Error('Please enter your account password.');
    }

    try {
      if (rememberMe) {
        await setPersistence(auth, browserLocalPersistence);
      } else {
        await setPersistence(auth, browserSessionPersistence);
      }
    } catch (e) {
      // Non-fatal
    }

    // 1. Authenticate with Firebase
    let userCredential;
    try {
      userCredential = await signInWithEmailAndPassword(auth, email.trim(), password);
    } catch (fbErr) {
      const code = fbErr.code || '';
      if (code === 'auth/invalid-credential' || code === 'auth/wrong-password' || code === 'auth/user-not-found') {
        throw new Error('Invalid email address or password. Please verify your credentials.');
      } else if (code === 'auth/invalid-email') {
        throw new Error('Please enter a valid email address.');
      } else if (code === 'auth/user-disabled') {
        throw new Error('Your account has been deactivated. Please contact your municipal administrator.');
      } else if (code === 'auth/too-many-requests') {
        throw new Error('Too many failed login attempts. Access temporarily disabled for security. Please try again later.');
      } else {
        throw new Error(fbErr.message || 'Firebase authentication failed. Please try again.');
      }
    }
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
    let userCredential;
    try {
      userCredential = await createUserWithEmailAndPassword(auth, email.trim(), password);
    } catch (fbErr) {
      const code = fbErr.code || '';
      if (code === 'auth/email-already-in-use') {
        throw new Error('An account with this email address is already registered. Please sign in instead.');
      } else if (code === 'auth/weak-password') {
        throw new Error('Password must be at least 8 characters long.');
      } else if (code === 'auth/invalid-email') {
        throw new Error('Please enter a valid email address.');
      } else {
        throw new Error(fbErr.message || 'Registration failed. Please try again.');
      }
    }
    const firebaseUser = userCredential.user;

    // Update Firebase display name if provided
    if (name && name.trim()) {
      try {
        await updateProfile(firebaseUser, { displayName: name.trim() });
      } catch (err) {
        // Non-fatal
      }
    }

    // Send Firebase email verification to citizen
    try {
      await sendEmailVerification(firebaseUser);
    } catch (err) {
      console.warn('[Firebase Auth] Verification email dispatch note:', err.message);
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
   * Register or sign in a staff member during account activation to retrieve Firebase ID token
   */
  registerOrLinkStaff: async (email, password) => {
    if (!isFirebaseConfigured() || !auth) {
      return null;
    }
    let userCredential;
    try {
      userCredential = await createUserWithEmailAndPassword(auth, email.trim(), password);
    } catch (fbErr) {
      if (fbErr.code === 'auth/email-already-in-use') {
        userCredential = await signInWithEmailAndPassword(auth, email.trim(), password);
      } else {
        throw fbErr;
      }
    }
    return userCredential.user.getIdToken();
  },

  /**
   * Trigger Firebase Password Reset Email
   */
  sendPasswordReset: async (email) => {
    if (!isFirebaseConfigured() || !auth) {
      throw new Error('Firebase Authentication is not configured in this environment.');
    }
    if (!email || !email.trim()) {
      throw new Error('Please enter your email address.');
    }
    await sendPasswordResetEmail(auth, email.trim());
  },

  /**
   * Confirm Password Reset via Firebase action code (oobCode)
   */
  confirmPasswordResetCode: async (oobCode, newPassword) => {
    if (!isFirebaseConfigured() || !auth) {
      throw new Error('Firebase Authentication is not configured in this environment.');
    }
    await confirmPasswordReset(auth, oobCode, newPassword);
  },

  /**
   * Verify Email using Firebase action code (oobCode)
   */
  verifyEmailWithActionCode: async (oobCode) => {
    if (!isFirebaseConfigured() || !auth) {
      throw new Error('Firebase Authentication is not configured in this environment.');
    }
    await applyActionCode(auth, oobCode);
    if (auth.currentUser) {
      await reload(auth.currentUser);
    }
  },

  /**
   * Resend email verification to current Firebase user
   */
  resendVerificationEmail: async () => {
    if (!isFirebaseConfigured() || !auth || !auth.currentUser) {
      throw new Error('No active Firebase user session found.');
    }
    await sendEmailVerification(auth.currentUser);
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
  },

  /**
   * Wait for Firebase Auth to initialize and resolve its initial user state
   * Ensures Remember Me sessions from IndexedDB are loaded before checking backend session.
   */
  waitForInitialAuth: () => {
    if (!isFirebaseConfigured() || !auth) {
      return Promise.resolve(null);
    }
    return new Promise((resolve) => {
      const unsubscribe = onAuthStateChanged(
        auth,
        (user) => {
          unsubscribe();
          resolve(user);
        },
        () => {
          resolve(null);
        }
      );
    });
  },

  /**
   * Register a listener for Firebase Auth state changes
   */
  onAuthStateChange: (callback) => {
    if (!isFirebaseConfigured() || !auth) {
      return () => {};
    }
    return onAuthStateChanged(auth, callback);
  }
};
