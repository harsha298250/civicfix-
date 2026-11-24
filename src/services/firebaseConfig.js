import { initializeApp, getApps, getApp } from 'firebase/app';
import { getAuth } from 'firebase/auth';

/**
 * CivicFix Platform — Firebase Configuration Module
 * 
 * Securely extracts Firebase client settings from Vite environment variables (VITE_FIREBASE_*).
 * Firebase credentials (API keys, project IDs) are public client configuration values,
 * while service-account private keys strictly remain on the backend server.
 */

const getFirebaseConfig = () => {
  const env = (typeof import.meta !== 'undefined' && import.meta.env)
    ? import.meta.env
    : (typeof process !== 'undefined' && process.env ? process.env : {});

  return {
    apiKey: env.VITE_FIREBASE_API_KEY || '',
    authDomain: env.VITE_FIREBASE_AUTH_DOMAIN || '',
    projectId: env.VITE_FIREBASE_PROJECT_ID || '',
    storageBucket: env.VITE_FIREBASE_STORAGE_BUCKET || '',
    messagingSenderId: env.VITE_FIREBASE_MESSAGING_SENDER_ID || '',
    appId: env.VITE_FIREBASE_APP_ID || ''
  };
};

export const firebaseConfig = getFirebaseConfig();

/**
 * Returns true if Firebase client has been configured with required properties.
 */
export const isFirebaseConfigured = () => {
  return Boolean(firebaseConfig.apiKey && firebaseConfig.projectId);
};

let app = null;
let auth = null;

try {
  if (isFirebaseConfigured()) {
    app = getApps().length > 0 ? getApp() : initializeApp(firebaseConfig);
    auth = getAuth(app);
  }
} catch (err) {
  // Graceful initialization failure in test or unconfigured environments
  console.warn('[CivicFix Firebase] Initialization deferred:', err.message);
}

export { app, auth };
