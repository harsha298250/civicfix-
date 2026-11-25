import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useApp } from '../../context/AppContext';
import { useTranslation } from '../../context/LanguageContext';
import LanguageToggle from '../../components/common/LanguageToggle';
import InstallPwaButton from '../../components/common/InstallPwaButton';
import { authService, isDemoModeEnabled } from '../../services/authService';
import { firebaseAuthService } from '../../services/firebaseAuth';
import { apiClient } from '../../services/apiClient';
import { detectContactType, formatIndianPhone, isValidIndianPhone } from '../../utils/phoneUtils';

export const LoginPage = () => {
  const navigate = useNavigate();
  const { setCurrentUser, showToast } = useApp();
  const { t } = useTranslation();

  const [selectedRole, setSelectedRole] = useState('CITIZEN');
  const [identifier, setIdentifier] = useState(isDemoModeEnabled() ? 'citizen@civicfix.gov' : '');
  const [password, setPassword] = useState(isDemoModeEnabled() ? 'CivicSecure#2026!' : '');
  const [rememberMe, setRememberMe] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [googleLoading, setGoogleLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [resendStatus, setResendStatus] = useState(null);
  const [resending, setResending] = useState(false);
  const [backendStatus, setBackendStatus] = useState({ checked: false, online: false });

  useEffect(() => {
    let mounted = true;
    apiClient.checkHealth()
      .then((res) => {
        if (mounted) setBackendStatus({ checked: true, online: true, version: res?.version || '1.0.0' });
      })
      .catch(() => {
        if (mounted) setBackendStatus({ checked: true, online: false });
      });
    return () => { mounted = false; };
  }, []);

  // Initialize Google Identity Services if client ID is configured
  useEffect(() => {
    const googleClientId = (typeof import.meta !== 'undefined' && import.meta.env?.VITE_GOOGLE_CLIENT_ID) || '';
    if (!googleClientId) return;

    const setupGoogle = () => {
      if (window.google?.accounts?.id) {
        try {
          window.google.accounts.id.initialize({
            client_id: googleClientId,
            callback: handleGoogleCredentialResponse,
            auto_select: false
          });
        } catch (e) {
          console.warn('Google Identity Services initialization warning:', e);
        }
      }
    };

    if (!window.google?.accounts?.id) {
      const script = document.createElement('script');
      script.src = 'https://accounts.google.com/gsi/client';
      script.async = true;
      script.defer = true;
      script.onload = setupGoogle;
      document.body.appendChild(script);
    } else {
      setupGoogle();
    }
  }, [rememberMe]);

  const handleGoogleCredentialResponse = async (response) => {
    if (!response?.credential) return;
    setGoogleLoading(true);
    setErrorMsg('');
    setResendStatus(null);

    try {
      const user = await authService.loginWithGoogle(response.credential, rememberMe);
      setCurrentUser(user);
      showToast(`Welcome back, ${user.name}! (Signed in with Google)`, 'success');

      if (user.role === 'OFFICER') navigate('/officer/dashboard');
      else if (user.role === 'SUPERVISOR') navigate('/supervisor/escalations');
      else if (user.role === 'WORKER') navigate('/worker/dashboard');
      else if (user.role === 'ADMIN') navigate('/admin/dashboard');
      else navigate('/citizen/dashboard');
    } catch (err) {
      setErrorMsg(err.message || 'Google sign-in failed. Please try again.');
    } finally {
      setGoogleLoading(false);
    }
  };

  const handleGoogleSignInClick = async () => {
    if (firebaseAuthService.isConfigured()) {
      setGoogleLoading(true);
      setErrorMsg('');
      setResendStatus(null);
      try {
        const user = await firebaseAuthService.signInWithGoogle(rememberMe);
        setCurrentUser(user);
        showToast(`Welcome back, ${user.name}! (Signed in with Firebase Google)`, 'success');

        if (user.role === 'OFFICER') navigate('/officer/dashboard');
        else if (user.role === 'SUPERVISOR') navigate('/supervisor/escalations');
        else if (user.role === 'WORKER') navigate('/worker/dashboard');
        else if (user.role === 'ADMIN') navigate('/admin/dashboard');
        else navigate('/citizen/dashboard');
      } catch (err) {
        setErrorMsg(err.message || 'Google sign-in failed. Please try again.');
      } finally {
        setGoogleLoading(false);
      }
      return;
    }

    const googleClientId = (typeof import.meta !== 'undefined' && import.meta.env?.VITE_GOOGLE_CLIENT_ID) || '';
    if (!googleClientId) {
      setErrorMsg('Google OAuth client ID is not configured (missing VITE_GOOGLE_CLIENT_ID). Please sign in using your email and password.');
      return;
    }

    if (window.google?.accounts?.id) {
      window.google.accounts.id.prompt();
    } else {
      setErrorMsg('Google authentication service is loading. Please try again in a moment.');
    }
  };

  // Quick Staff & Demo Account Presets
  const DEMO_PRESETS = [
    { label: 'Citizen', name: 'Alex Morgan', identifier: 'citizen@civicfix.gov', phone: null, role: 'CITIZEN' },
    { label: 'Municipal Officer', name: 'Elena Vance', identifier: 'officer@civicfix.gov', phone: null, role: 'OFFICER' },
    { label: 'Supervisor', name: 'Sarah Jenkins', identifier: 'supervisor@civicfix.gov', phone: null, role: 'SUPERVISOR' },
    { label: 'Field Worker', name: 'Marcus Chen', identifier: 'worker@civicfix.gov', phone: null, role: 'WORKER' },
    { label: 'System Admin', name: 'Priya Sharma', identifier: 'admin@civicfix.gov', phone: null, role: 'ADMIN' }
  ];

  const handleApplyPreset = (preset, usePhone = false) => {
    setSelectedRole(preset.role === 'OFFICER' ? 'OFFICER' : 'CITIZEN');
    setIdentifier((usePhone && preset.phone) ? preset.phone : preset.identifier);
    setPassword('CivicSecure#2026!');
    setErrorMsg('');
    setResendStatus(null);
  };

  const contactType = detectContactType(identifier);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!identifier.trim()) {
      setErrorMsg(
        firebaseAuthService.isConfigured()
          ? 'Please enter your registered email address.'
          : 'Please enter your registered email address or Indian mobile number.'
      );
      return;
    }

    if (firebaseAuthService.isConfigured() && contactType === 'PHONE') {
      setErrorMsg('Firebase authentication requires your registered email address.');
      return;
    }

    if (contactType === 'PHONE' && !isValidIndianPhone(identifier)) {
      setErrorMsg('Please enter a valid 10-digit Indian mobile number (e.g. 98401 23456).');
      return;
    }

    if (!password) {
      setErrorMsg('Please enter your account password.');
      return;
    }

    setLoading(true);
    setErrorMsg('');
    setResendStatus(null);

    try {
      const user = await authService.login(identifier.trim(), password, rememberMe);
      setCurrentUser(user);
      const modeLabel = (user.authSource && user.authSource.includes('FIREBASE'))
        ? ' (Firebase Authenticated)'
        : (user.isLiveBackend ? ' (PostgreSQL Live)' : ' (Demo Mode)');
      showToast(`Welcome back, ${user.name}!${modeLabel}`, 'success');

      if (user.role === 'CITIZEN') navigate('/citizen/dashboard');
      else if (user.role === 'OFFICER') navigate('/officer/dashboard');
      else if (user.role === 'SUPERVISOR') navigate('/supervisor/escalations');
      else if (user.role === 'WORKER') navigate('/worker/dashboard');
      else if (user.role === 'ADMIN') navigate('/admin/dashboard');
      else navigate('/citizen/dashboard');
    } catch (err) {
      setErrorMsg(err.message || 'Login failed. Please verify your credentials.');
    } finally {
      setLoading(false);
    }
  };

  const handleResendVerification = async () => {
    const emailToUse = identifier.trim();
    if (!emailToUse || !emailToUse.includes('@')) {
      setErrorMsg('Please enter your registered email address above to resend the verification link.');
      return;
    }

    setResending(true);
    try {
      const res = await authService.resendVerification(emailToUse);
      setResendStatus({
        type: 'success',
        message: res?.message || 'Verification link sent! Please check your email inbox.'
      });
    } catch (err) {
      setResendStatus({
        type: 'error',
        message: err?.message || 'Failed to resend verification email.'
      });
    } finally {
      setResending(false);
    }
  };

  const isPendingVerificationError =
    errorMsg &&
    (errorMsg.toLowerCase().includes('verification') ||
      errorMsg.toLowerCase().includes('verify') ||
      errorMsg.toLowerCase().includes('disabled') ||
      errorMsg.toLowerCase().includes('not active'));

  return (
    <div className="w-full min-h-[calc(100vh-64px)] flex flex-col items-center justify-center p-4 sm:p-6 bg-[#F5F7FA]">
      <div className="w-full max-w-md mx-auto">
        <div className="bg-white rounded-2xl p-6 sm:p-8 shadow-sm border border-[#E2E7ED] space-y-6">
          {/* Header & Language Toggle */}
          <div className="flex items-center justify-between pb-2 border-b border-[#E2E7ED]">
            <InstallPwaButton />
            <div className="flex items-center gap-2 ml-auto">
              <span className="text-[11px] font-bold text-tertiary uppercase tracking-wider">{t('common.language')}</span>
              <LanguageToggle />
            </div>
          </div>

          <div className="text-center space-y-1">
            <div className="w-12 h-12 rounded-2xl bg-[#E8F4F1] text-[#137A70] flex items-center justify-center mx-auto mb-3 shadow-xs">
              <span className="material-symbols-outlined text-2xl">account_circle</span>
            </div>
            <h1 className="text-2xl font-bold text-[#18232F]">{t('auth.loginTitle')}</h1>
            <p className="text-xs text-[#667384]">
              {t('auth.loginSubtitle')}
            </p>

            {/* Backend Connectivity Status Pill */}
            {backendStatus.checked && (
              <div className="pt-2 flex items-center justify-center">
                {backendStatus.online ? (
                  <span className="inline-flex items-center gap-1.5 text-[11px] font-semibold text-emerald-800 bg-emerald-50 px-2.5 py-0.5 rounded-full border border-emerald-200">
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-600 animate-pulse"></span>
                    Spring Boot & PostgreSQL Connected
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-1.5 text-[11px] font-semibold text-amber-800 bg-amber-50 px-2.5 py-0.5 rounded-full border border-amber-200">
                    <span className="w-1.5 h-1.5 rounded-full bg-amber-500"></span>
                    {isDemoModeEnabled() ? 'Backend Offline (Demo Fallback Active)' : 'Municipal Backend Unreachable'}
                  </span>
                )}
              </div>
            )}
          </div>

          {/* Error Message & Verification Resend Banner */}
          {errorMsg && (
            <div
              id="login-error-alert"
              role="alert"
              aria-live="assertive"
              className="p-3.5 bg-red-50 border border-red-200 rounded-xl text-xs text-red-700 space-y-2 animate-fade-in"
            >
              <div className="flex items-start gap-2">
                <span className="material-symbols-outlined text-[18px] shrink-0 mt-0.5">error</span>
                <p className="leading-relaxed flex-1">{errorMsg}</p>
              </div>

              {isPendingVerificationError && (
                <div className="pt-2 border-t border-red-200/80 flex items-center justify-between">
                  <span className="text-[11px] text-red-800 font-medium">Need another activation link?</span>
                  <button
                    type="button"
                    onClick={handleResendVerification}
                    disabled={resending}
                    className="px-2.5 py-1 bg-red-700 hover:bg-red-800 text-white rounded-lg text-[10px] font-semibold transition-colors disabled:opacity-60 flex items-center gap-1"
                  >
                    {resending && <span className="material-symbols-outlined text-[12px] animate-spin">progress_activity</span>}
                    <span>Resend Email</span>
                  </button>
                </div>
              )}
            </div>
          )}

          {/* Resend Status Feedback */}
          {resendStatus && (
            <div
              className={`p-3 rounded-xl text-xs flex items-start gap-2 border animate-fade-in ${
                resendStatus.type === 'success'
                  ? 'bg-emerald-50 border-emerald-200 text-emerald-800'
                  : 'bg-red-50 border-red-200 text-red-800'
              }`}
            >
              <span className="material-symbols-outlined text-[16px] shrink-0 mt-0.5">
                {resendStatus.type === 'success' ? 'check_circle' : 'error'}
              </span>
              <p className="leading-relaxed">{resendStatus.message}</p>
            </div>
          )}

          {/* Role Selection Tabs */}
          <div className="flex p-1 bg-slate-100 rounded-xl gap-1">
            <button
              type="button"
              onClick={() => {
                setSelectedRole('CITIZEN');
                if (isDemoModeEnabled()) {
                  handleApplyPreset(DEMO_PRESETS[0], false);
                } else {
                  setIdentifier('');
                  setPassword('');
                }
              }}
              className={`flex-1 py-2 text-xs font-semibold rounded-lg transition-all flex items-center justify-center gap-1.5 ${
                selectedRole === 'CITIZEN'
                  ? 'bg-white text-[#18232F] shadow-xs'
                  : 'text-[#667384] hover:text-[#18232F]'
              }`}
            >
              <span className="material-symbols-outlined text-[16px]">person</span>
              <span>{t('auth.roleCitizenTab')}</span>
            </button>
            <button
              type="button"
              onClick={() => {
                setSelectedRole('OFFICER');
                if (isDemoModeEnabled()) {
                  handleApplyPreset(DEMO_PRESETS[1], false);
                } else {
                  setIdentifier('');
                  setPassword('');
                }
              }}
              className={`flex-1 py-2 text-xs font-semibold rounded-lg transition-all flex items-center justify-center gap-1.5 ${
                selectedRole === 'OFFICER'
                  ? 'bg-[#137A70] text-white shadow-xs'
                  : 'text-[#667384] hover:text-[#18232F]'
              }`}
            >
              <span className="material-symbols-outlined text-[16px]">account_balance</span>
              <span>{t('auth.roleOfficerTab')}</span>
            </button>
          </div>

          {/* Officer Role Instructions Card */}
          {selectedRole === 'OFFICER' && (
            <div className="p-4 bg-teal-50 border border-teal-200 rounded-xl text-xs space-y-2 animate-fade-in">
              <div className="flex items-center gap-2 text-teal-900 font-bold">
                <span className="material-symbols-outlined text-[18px] text-teal-700">admin_panel_settings</span>
                <span>{t('auth.officerInstructionsTitle')}</span>
              </div>
              <p className="text-teal-800 leading-relaxed text-[11px]">
                {t('auth.officerInstructionsDesc')}
              </p>
              {isDemoModeEnabled() ? (
                <div className="pt-2 border-t border-teal-200/60 flex items-center justify-between">
                  <span className="text-[10px] text-teal-700 font-medium">{t('auth.officerNoticeDemo')}</span>
                  <button
                    type="button"
                    onClick={() => handleApplyPreset(DEMO_PRESETS[1], false)}
                    className="px-2.5 py-1 bg-teal-700 hover:bg-teal-800 text-white rounded-lg text-[10px] font-semibold transition-colors"
                  >
                    Load Elena Vance (Demo Officer)
                  </button>
                </div>
              ) : (
                <div className="pt-1.5 border-t border-teal-200/60">
                  <span className="text-[10px] text-teal-700">
                    {t('auth.officerNoticeProd')}
                  </span>
                </div>
              )}
            </div>
          )}

          {/* Login Form */}
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <div className="flex items-center justify-between mb-1">
                <label
                  htmlFor="login-identifier"
                  className="block text-xs font-semibold text-[#18232F] uppercase tracking-wider"
                >
                  {t('auth.emailLabel')} *
                </label>
                {contactType === 'PHONE' && (
                  <span className="text-[10px] text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full font-mono font-semibold flex items-center gap-1 border border-emerald-200">
                    <span>🇮🇳</span> {t('auth.mobileBadge')}
                  </span>
                )}
                {contactType === 'EMAIL' && (
                  <span className="text-[10px] text-blue-700 bg-blue-50 px-2 py-0.5 rounded-full font-mono font-semibold">
                    {t('auth.emailBadge')}
                  </span>
                )}
              </div>
              <div className="relative">
                <span className="material-symbols-outlined absolute left-3 top-2.5 text-[#667384] text-[18px]">
                  {contactType === 'PHONE' ? 'smartphone' : 'alternate_email'}
                </span>
                <input
                  id="login-identifier"
                  type={contactType === 'PHONE' ? 'tel' : 'text'}
                  required
                  aria-describedby={errorMsg ? 'login-error-alert login-identifier-hint' : 'login-identifier-hint'}
                  aria-invalid={!!errorMsg}
                  placeholder={t('auth.emailPlaceholder')}
                  value={identifier}
                  onChange={(e) => {
                    setIdentifier(e.target.value);
                    if (errorMsg) setErrorMsg('');
                  }}
                  className="w-full pl-9 pr-3.5 py-2.5 text-sm bg-slate-50 border border-[#E2E7ED] rounded-xl text-[#18232F] focus:outline-none focus:ring-2 focus:ring-[#137A70] focus:bg-white transition-all font-sans"
                />
              </div>
              <p id="login-identifier-hint" className="text-[11px] text-[#667384] mt-1">
                {firebaseAuthService.isConfigured()
                  ? 'Enter your registered email address (Firebase Authentication).'
                  : 'Accepts registered email or standard 10-digit Indian mobile (+91).'}
              </p>
            </div>

            <div>
              <div className="flex items-center justify-between mb-1">
                <label
                  htmlFor="login-password"
                  className="block text-xs font-semibold text-[#18232F] uppercase tracking-wider"
                >
                  {t('auth.passwordLabel')} *
                </label>
              </div>
              <div className="relative">
                <span className="material-symbols-outlined absolute left-3 top-2.5 text-[#667384] text-[18px]">
                  lock
                </span>
                <input
                  id="login-password"
                  type={showPassword ? 'text' : 'password'}
                  required
                  aria-describedby={errorMsg ? 'login-error-alert' : undefined}
                  aria-invalid={!!errorMsg}
                  placeholder={t('auth.passwordPlaceholder')}
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    if (errorMsg) setErrorMsg('');
                  }}
                  className="w-full pl-9 pr-10 py-2.5 text-sm bg-slate-50 border border-[#E2E7ED] rounded-xl text-[#18232F] focus:outline-none focus:ring-2 focus:ring-[#137A70] focus:bg-white transition-all"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-2.5 text-[#667384] hover:text-[#18232F] p-0.5 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary rounded"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  <span className="material-symbols-outlined text-[18px]">
                    {showPassword ? 'visibility_off' : 'visibility'}
                  </span>
                </button>
              </div>
            </div>

            {/* Remember Me and Forgot Password */}
            <div className="flex items-center justify-between pt-1">
              <label className="flex items-center gap-2 cursor-pointer select-none">
                <input
                  type="checkbox"
                  id="login-remember-me"
                  name="rememberMe"
                  checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)}
                  className="w-4 h-4 rounded border-slate-300 text-[#137A70] focus:ring-[#137A70] transition-colors"
                />
                <span className="text-xs text-[#18232F] font-medium">Remember me (30 days)</span>
              </label>

              <Link
                to="/forgot-password"
                className="text-xs font-medium text-[#137A70] hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-primary rounded"
              >
                {t('auth.forgotPassword')}
              </Link>
            </div>

            <button
              type="submit"
              disabled={loading || googleLoading}
              className="w-full min-h-[44px] py-2.5 bg-[#137A70] hover:bg-[#0F625B] text-white text-sm font-semibold rounded-xl shadow-sm transition-all flex items-center justify-center gap-2 disabled:opacity-60 focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[#137A70]"
            >
              {loading ? (
                <>
                  <span className="material-symbols-outlined animate-spin text-[18px]">progress_activity</span>
                  <span>{t('auth.signingIn')}</span>
                </>
              ) : (
                <span>{t('auth.signInButton')}</span>
              )}
            </button>

            {/* Or Divider */}
            <div className="relative flex py-1 items-center">
              <div className="flex-grow border-t border-[#E2E7ED]"></div>
              <span className="flex-shrink mx-3 text-[11px] text-[#667384] uppercase font-semibold">Or continue with</span>
              <div className="flex-grow border-t border-[#E2E7ED]"></div>
            </div>

            {/* Google Sign In Button */}
            <button
              type="button"
              onClick={handleGoogleSignInClick}
              disabled={loading || googleLoading}
              className="w-full min-h-[44px] py-2.5 bg-white hover:bg-slate-50 text-[#18232F] text-sm font-semibold rounded-xl border border-[#CBD5E1] shadow-xs transition-all flex items-center justify-center gap-2.5 disabled:opacity-60 focus:outline-none focus-visible:ring-2 focus-visible:ring-[#137A70]"
            >
              <svg className="w-4 h-4 shrink-0" viewBox="0 0 24 24">
                <path
                  fill="#4285F4"
                  d="M23.745 12.27c0-.7-.06-1.4-.19-2.07H12v4.51h6.6c-.29 1.52-1.14 2.8-2.4 3.66v3.05h3.88c2.27-2.09 3.665-5.17 3.665-9.15z"
                />
                <path
                  fill="#34A853"
                  d="M12 24c3.24 0 5.95-1.08 7.93-2.91l-3.88-3.05c-1.08.72-2.45 1.16-4.05 1.16-3.12 0-5.77-2.1-6.72-4.93H1.26v3.14C3.25 21.36 7.33 24 12 24z"
                />
                <path
                  fill="#FBBC05"
                  d="M5.28 14.27c-.25-.72-.38-1.49-.38-2.27s.13-1.55.38-2.27V6.59H1.26C.46 8.18 0 9.99 0 12s.46 3.82 1.26 5.41l4.02-3.14z"
                />
                <path
                  fill="#EA4335"
                  d="M12 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42C17.95 1.19 15.24 0 12 0 7.33 0 3.25 2.64 1.26 6.59l4.02 3.14c.95-2.83 3.6-4.98 6.72-4.98z"
                />
              </svg>
              <span>{googleLoading ? 'Connecting with Google...' : 'Continue with Google'}</span>
            </button>
          </form>

          {/* Quick Demo Account Presets (Active exclusively when VITE_DEMO_MODE=true) */}
          {isDemoModeEnabled() && (
            <div className="pt-2 border-t border-[#E2E7ED] space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-[11px] font-bold text-[#667384] uppercase tracking-wider">
                  {t('auth.demoAccountsTitle')}
                </span>
                <span className="text-[10px] text-tertiary font-mono">CivicSecure#2026!</span>
              </div>
              <div className="grid grid-cols-2 gap-2">
                {DEMO_PRESETS.map((p, idx) => (
                  <div
                    key={idx}
                    className="p-2 rounded-xl border border-[#E2E7ED] bg-slate-50 hover:bg-[#E8F4F1] hover:border-[#137A70] transition-colors"
                  >
                    <div className="flex items-center justify-between">
                      <p className="text-xs font-bold text-[#18232F]">{p.label}</p>
                      <span className="text-[9px] font-mono text-tertiary">{p.role}</span>
                    </div>
                    <p className="text-[11px] text-[#667384] truncate">{p.name}</p>
                    <div className="flex items-center gap-1.5 mt-1.5 pt-1.5 border-t border-slate-200 text-[10px]">
                      <button
                        type="button"
                        onClick={() => handleApplyPreset(p, false)}
                        className="px-1.5 py-0.5 rounded bg-white hover:bg-slate-100 text-primary-container font-semibold border border-slate-200"
                      >
                        Email
                      </button>
                      {p.phone && (
                        <button
                          type="button"
                          onClick={() => handleApplyPreset(p, true)}
                          className="px-1.5 py-0.5 rounded bg-white hover:bg-slate-100 text-emerald-800 font-semibold border border-slate-200"
                        >
                          🇮🇳 Mobile
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Registration link */}
          <div className="text-center text-xs text-[#667384] pt-2 border-t border-[#E2E7ED]">
            {t('auth.dontHaveAccount')}{' '}
            <Link to="/register" className="font-semibold text-[#137A70] hover:underline">
              {t('auth.registerLink')}
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
