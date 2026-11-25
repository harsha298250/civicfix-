import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useApp } from '../../context/AppContext';
import { useTranslation } from '../../context/LanguageContext';
import LanguageToggle from '../../components/common/LanguageToggle';
import ThemeToggle from '../../components/common/ThemeToggle';
import { authService } from '../../services/authService';
import { firebaseAuthService } from '../../services/firebaseAuth';
import { isValidIndianPhone, formatIndianPhone } from '../../utils/phoneUtils';

export const RegisterPage = () => {
  const navigate = useNavigate();
  const { setCurrentUser, showToast } = useApp();
  const { t } = useTranslation();

  const [contactMethod, setContactMethod] = useState('MOBILE'); // 'MOBILE' | 'EMAIL'

  const [formData, setFormData] = useState({
    name: '',
    phone: '',
    email: '',
    ward: '',
    password: '',
    confirmPassword: '',
    agreeTerms: false,
    agreeConsent: true
  });

  const [showPass, setShowPass] = useState(false);
  const [showConfirmPass, setShowConfirmPass] = useState(false);
  const [loading, setLoading] = useState(false);
  const [googleLoading, setGoogleLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [duplicateAccountNotice, setDuplicateAccountNotice] = useState(false);
  const [registrationSuccessEmail, setRegistrationSuccessEmail] = useState('');

  // Password strength calculator
  const calculatePasswordStrength = (pass) => {
    if (!pass) return { score: 0, label: 'None', color: 'bg-slate-200' };
    let score = 0;
    if (pass.length >= 8) score += 1;
    if (pass.length >= 12) score += 1;
    if (/[A-Z]/.test(pass) && /[a-z]/.test(pass)) score += 1;
    if (/[0-9]/.test(pass)) score += 1;
    if (/[^A-Za-z0-9]/.test(pass)) score += 1;

    if (score <= 2) return { score: 1, label: 'Weak', color: 'bg-red-500', width: 'w-1/3' };
    if (score <= 4) return { score: 2, label: 'Moderate', color: 'bg-amber-500', width: 'w-2/3' };
    return { score: 3, label: 'Strong', color: 'bg-emerald-600', width: 'w-full' };
  };

  const strength = calculatePasswordStrength(formData.password);

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
  }, []);

  const handleGoogleCredentialResponse = async (response) => {
    if (!response?.credential) return;
    setGoogleLoading(true);
    setErrorMsg('');

    try {
      const user = await authService.loginWithGoogle(response.credential, false);
      setCurrentUser(user);
      showToast(`Welcome to CivicFix, ${user.name}!`, 'success');
      navigate('/citizen/dashboard');
    } catch (err) {
      setErrorMsg(err.message || 'Google registration failed. Please try again.');
    } finally {
      setGoogleLoading(false);
    }
  };

  const handleGoogleSignInClick = async () => {
    if (firebaseAuthService.isConfigured()) {
      setGoogleLoading(true);
      setErrorMsg('');
      try {
        const user = await firebaseAuthService.signInWithGoogle(false);
        setCurrentUser(user);
        showToast(`Welcome to CivicFix, ${user.name}!`, 'success');
        navigate('/citizen/dashboard');
      } catch (err) {
        setErrorMsg(err.message || 'Google registration failed. Please try again.');
      } finally {
        setGoogleLoading(false);
      }
      return;
    }

    const googleClientId = (typeof import.meta !== 'undefined' && import.meta.env?.VITE_GOOGLE_CLIENT_ID) || '';
    if (!googleClientId) {
      setErrorMsg('Google OAuth client ID is not configured (missing VITE_GOOGLE_CLIENT_ID). Please enroll using standard credentials.');
      return;
    }

    if (window.google?.accounts?.id) {
      window.google.accounts.id.prompt();
    } else {
      setErrorMsg('Google authentication service is loading. Please try again in a moment.');
    }
  };

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value
    }));
    if (errorMsg) setErrorMsg('');
    if (duplicateAccountNotice) setDuplicateAccountNotice(false);
  };

  // Handle phone input with live formatting
  const handlePhoneChange = (e) => {
    const raw = e.target.value;
    const digitsOnly = raw.replace(/\D/g, '');
    let clean10 = digitsOnly;
    if (clean10.startsWith('91') && clean10.length > 10) {
      clean10 = clean10.substring(2);
    }
    if (clean10.startsWith('0') && clean10.length > 10) {
      clean10 = clean10.substring(1);
    }
    clean10 = clean10.substring(0, 10);

    setFormData((prev) => ({
      ...prev,
      phone: clean10
    }));
    if (errorMsg) setErrorMsg('');
    if (duplicateAccountNotice) setDuplicateAccountNotice(false);
  };

  // Single-Step Citizen Registration directly against POST /api/auth/register
  const handleRegister = async (e) => {
    e.preventDefault();
    setErrorMsg('');
    setDuplicateAccountNotice(false);

    if (!formData.name.trim() || formData.name.trim().length < 2) {
      setErrorMsg('Please enter your full legal name (minimum 2 characters).');
      return;
    }

    if (contactMethod === 'MOBILE') {
      if (!isValidIndianPhone(formData.phone)) {
        setErrorMsg('Please enter a valid 10-digit Indian mobile number starting with 6, 7, 8, or 9.');
        return;
      }
    } else {
      if (!formData.email.trim() || !formData.email.includes('@')) {
        setErrorMsg('Please enter a valid email address.');
        return;
      }
    }

    if (!formData.ward || !formData.ward.trim()) {
      setErrorMsg('Please select your municipal zone / ward.');
      return;
    }

    if (formData.password.length < 8) {
      setErrorMsg('Password must be at least 8 characters long.');
      return;
    }

    if (formData.password !== formData.confirmPassword) {
      setErrorMsg(t('auth.passwordMismatch') || 'Passwords do not match. Please verify your password entry.');
      return;
    }

    if (!formData.agreeTerms) {
      setErrorMsg('You must agree to the CivicFix Municipal Terms of Service and Privacy Notice.');
      return;
    }

    setLoading(true);

    try {
      let newUser;
      if (contactMethod === 'EMAIL' && firebaseAuthService.isConfigured()) {
        newUser = await authService.registerWithFirebase({
          email: formData.email.trim(),
          password: formData.password,
          name: formData.name.trim(),
          ward: formData.ward.trim(),
          phone: formData.phone ? formData.phone.trim() : null
        });
      } else {
        newUser = await authService.registerCitizen({
          name: formData.name.trim(),
          email: contactMethod === 'EMAIL' ? formData.email.trim() : (formData.email.trim() || null),
          phone: contactMethod === 'MOBILE' ? formData.phone.trim() : (formData.phone.trim() || null),
          ward: formData.ward.trim(),
          password: formData.password
        });
      }

      if (newUser && newUser.status === 'PENDING_VERIFICATION') {
        setRegistrationSuccessEmail(formData.email.trim());
        return;
      }

      setCurrentUser(newUser);
      showToast('Citizen account registered successfully!', 'success');
      navigate('/citizen/dashboard');
    } catch (err) {
      if (err.message && err.message.includes('already registered')) {
        setDuplicateAccountNotice(true);
      }
      setErrorMsg(err.message || 'Unable to complete citizen registration.');
    } finally {
      setLoading(false);
    }
  };

  // If email verification is required, render confirmation view
  if (registrationSuccessEmail) {
    return (
      <div className="w-full min-h-[calc(100vh-64px)] flex flex-col items-center justify-center p-4 sm:p-6 bg-[#F5F7FA]">
        <div className="w-full max-w-md mx-auto bg-white rounded-2xl p-6 sm:p-8 shadow-sm border border-[#E2E7ED] space-y-6 text-center animate-fade-in">
          <div className="w-14 h-14 rounded-2xl bg-emerald-50 border border-emerald-200 text-emerald-700 flex items-center justify-center mx-auto">
            <span className="material-symbols-outlined text-[32px]">mark_email_read</span>
          </div>
          <div className="space-y-2">
            <h1 className="text-xl font-bold text-[#18232F]">Verify Your Email Address</h1>
            <p className="text-xs text-[#667384] leading-relaxed">
              We have sent a verification email to <strong className="text-[#18232F]">{registrationSuccessEmail}</strong>.
            </p>
            <p className="text-xs text-[#667384] leading-relaxed">
              Please click the verification link in your inbox to activate your CivicFix citizen account before logging in.
            </p>
          </div>
          <div className="pt-2 flex flex-col gap-2">
            <Link
              to="/login"
              className="w-full min-h-[44px] py-2.5 bg-[#137A70] hover:bg-[#0F625B] text-white text-sm font-semibold rounded-xl shadow-sm transition-all flex items-center justify-center gap-2"
            >
              <span className="material-symbols-outlined text-[18px]">login</span>
              <span>Proceed to Sign In</span>
            </Link>
            <Link
              to="/verify-email"
              className="w-full min-h-[40px] py-2 bg-slate-100 hover:bg-slate-200 text-[#18232F] text-xs font-semibold rounded-xl transition-all flex items-center justify-center gap-1.5"
            >
              <span>Resend Verification Email</span>
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="w-full min-h-[calc(100vh-64px)] flex flex-col items-center justify-center p-4 sm:p-6 bg-[#F5F7FA]">
      <div className="w-full max-w-lg mx-auto space-y-4">
        {/* Municipal Staff Advisory Notice */}
        <div className="bg-white rounded-2xl p-4 shadow-sm border border-[#E2E7ED] flex items-start gap-3">
          <div className="w-8 h-8 rounded-xl bg-purple-100 text-purple-700 flex items-center justify-center shrink-0">
            <span className="material-symbols-outlined text-[18px]">verified_user</span>
          </div>
          <div className="text-xs">
            <h4 className="font-bold text-[#18232F]">{t('auth.staffAdvisoryTitle') || 'Staff Account Notice'}</h4>
            <p className="text-[#667384] mt-0.5 leading-relaxed">
              {t('auth.staffAdvisoryDesc') || 'Municipal Officers, Supervisors, and Field Workers are provisioned directly by Municipal IT Administration. Staff should sign in directly with their assigned credentials.'}
            </p>
          </div>
        </div>

        {/* Main Enrollment Card */}
        <div className="bg-white rounded-2xl p-6 sm:p-8 shadow-sm border border-[#E2E7ED] space-y-5">
          {/* Language Toggle Header */}
          <div className="flex items-center justify-between pb-2 border-b border-[#E2E7ED]">
            <span className="text-[11px] font-bold text-tertiary uppercase tracking-wider">{t('common.language')}</span>
            <div className="flex items-center gap-2">
              <ThemeToggle />
              <LanguageToggle />
            </div>
          </div>

          {/* Header */}
          <div className="text-center space-y-1">
            <div className="w-12 h-12 rounded-2xl bg-[#E8F4F1] text-[#137A70] flex items-center justify-center mx-auto mb-3 shadow-xs">
              <span className="material-symbols-outlined text-2xl">how_to_reg</span>
            </div>
            <h1 className="text-2xl font-bold text-[#18232F]">{t('auth.registerTitle')}</h1>
            <p className="text-xs text-[#667384]">
              {t('auth.registerSubtitle')}
            </p>
          </div>

          {/* Error Message */}
          {errorMsg && (
            <div
              id="register-error-alert"
              role="alert"
              aria-live="assertive"
              className="p-3.5 bg-red-50 border border-red-200 rounded-xl text-xs text-red-700 flex items-start gap-2 animate-fade-in"
            >
              <span className="material-symbols-outlined text-[18px] shrink-0 mt-0.5">error</span>
              <div className="leading-relaxed flex-1">
                <p>{errorMsg}</p>
                {duplicateAccountNotice && (
                  <div className="mt-2 pt-2 border-t border-red-200 flex items-center gap-2">
                    <span className="text-[11px] font-semibold text-red-900">Already have an account?</span>
                    <Link
                      to="/login"
                      className="px-2 py-1 bg-red-700 hover:bg-red-800 text-white rounded text-[10px] font-semibold transition-colors"
                    >
                      Sign In Now
                    </Link>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Google Sign In / Registration Quick Action */}
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

          {/* Or Divider */}
          <div className="relative flex py-1 items-center">
            <div className="flex-grow border-t border-[#E2E7ED]"></div>
            <span className="flex-shrink mx-3 text-[11px] text-[#667384] uppercase font-semibold">Or enroll with details</span>
            <div className="flex-grow border-t border-[#E2E7ED]"></div>
          </div>

          {/* Contact Mode Selector (Mobile OTP / Email Verification) */}
          <div className="flex p-1 bg-slate-100 rounded-xl gap-1">
            <button
              type="button"
              onClick={() => {
                setContactMethod('MOBILE');
                if (errorMsg) setErrorMsg('');
              }}
              className={`flex-1 py-2 text-xs font-semibold rounded-lg transition-all flex items-center justify-center gap-1.5 ${
                contactMethod === 'MOBILE'
                  ? 'bg-white text-[#18232F] shadow-xs'
                  : 'text-[#667384] hover:text-[#18232F]'
              }`}
            >
              <span className="material-symbols-outlined text-[16px]">smartphone</span>
              <span>Indian Mobile (+91)</span>
            </button>
            <button
              type="button"
              onClick={() => {
                setContactMethod('EMAIL');
                if (errorMsg) setErrorMsg('');
              }}
              className={`flex-1 py-2 text-xs font-semibold rounded-lg transition-all flex items-center justify-center gap-1.5 ${
                contactMethod === 'EMAIL'
                  ? 'bg-white text-[#18232F] shadow-xs'
                  : 'text-[#667384] hover:text-[#18232F]'
              }`}
            >
              <span className="material-symbols-outlined text-[16px]">alternate_email</span>
              <span>Email Verification</span>
            </button>
          </div>

          <form onSubmit={handleRegister} className="space-y-4">
            {/* Full Name */}
            <div>
              <label htmlFor="reg-name" className="block text-xs font-semibold text-[#18232F] uppercase tracking-wider mb-1">
                {t('auth.fullNameLabel')} *
              </label>
              <div className="relative">
                <span className="material-symbols-outlined absolute left-3 top-2.5 text-[#667384] text-[18px]">
                  person
                </span>
                <input
                  id="reg-name"
                  type="text"
                  name="name"
                  required
                  placeholder={t('auth.fullNamePlaceholder') || 'e.g. Alex Morgan'}
                  value={formData.name}
                  onChange={handleChange}
                  className="w-full pl-9 pr-3.5 py-2.5 text-sm bg-slate-50 border border-[#E2E7ED] rounded-xl text-[#18232F] focus:outline-none focus:ring-2 focus:ring-[#137A70] focus:bg-white transition-all font-sans"
                />
              </div>
            </div>

            {/* Contact Input based on Selection */}
            {contactMethod === 'MOBILE' ? (
              <div>
                <div className="flex items-center justify-between mb-1">
                  <label htmlFor="reg-phone" className="block text-xs font-semibold text-[#18232F] uppercase tracking-wider">
                    {t('auth.phoneLabel')} *
                  </label>
                  <span className="text-[10px] text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full font-mono font-semibold flex items-center gap-1 border border-emerald-200">
                    <span>🇮🇳</span> +91 Only
                  </span>
                </div>
                <div className="relative flex items-center">
                  <span className="absolute left-3 text-xs font-bold text-[#18232F] select-none font-mono">
                    +91
                  </span>
                  <input
                    id="reg-phone"
                    type="tel"
                    name="phone"
                    required
                    placeholder="98401 23456"
                    maxLength={10}
                    value={formData.phone}
                    onChange={handlePhoneChange}
                    className="w-full pl-12 pr-3.5 py-2.5 text-sm bg-slate-50 border border-[#E2E7ED] rounded-xl text-[#18232F] focus:outline-none focus:ring-2 focus:ring-[#137A70] focus:bg-white transition-all font-mono tracking-wider"
                  />
                </div>
                <p className="text-[11px] text-[#667384] mt-1">
                  Enter 10-digit mobile starting with 6, 7, 8, or 9.
                </p>
              </div>
            ) : (
              <div>
                <label htmlFor="reg-email" className="block text-xs font-semibold text-[#18232F] uppercase tracking-wider mb-1">
                  {t('auth.emailLabel')} *
                </label>
                <div className="relative">
                  <span className="material-symbols-outlined absolute left-3 top-2.5 text-[#667384] text-[18px]">
                    alternate_email
                  </span>
                  <input
                    id="reg-email"
                    type="email"
                    name="email"
                    required
                    placeholder="resident@example.com"
                    value={formData.email}
                    onChange={handleChange}
                    className="w-full pl-9 pr-3.5 py-2.5 text-sm bg-slate-50 border border-[#E2E7ED] rounded-xl text-[#18232F] focus:outline-none focus:ring-2 focus:ring-[#137A70] focus:bg-white transition-all font-sans"
                  />
                </div>
                <p className="text-[11px] text-[#667384] mt-1">
                  A single-use verification link will be sent to activate your account.
                </p>
              </div>
            )}

            {/* Municipal Ward Selector */}
            <div>
              <label htmlFor="reg-ward" className="block text-xs font-semibold text-[#18232F] uppercase tracking-wider mb-1">
                {t('auth.wardLabel')} *
              </label>
              <div className="relative">
                <span className="material-symbols-outlined absolute left-3 top-2.5 text-[#667384] text-[18px]">
                  location_city
                </span>
                <select
                  id="reg-ward"
                  name="ward"
                  required
                  value={formData.ward}
                  onChange={handleChange}
                  className="w-full pl-9 pr-8 py-2.5 text-sm bg-slate-50 border border-[#E2E7ED] rounded-xl text-[#18232F] focus:outline-none focus:ring-2 focus:ring-[#137A70] focus:bg-white transition-all appearance-none"
                >
                  <option value="">Select your municipal zone / ward</option>
                  <option value="Zone 01, Thiruvottiyur, Chennai">Zone 01, Thiruvottiyur, Chennai</option>
                  <option value="Zone 02, Manali, Chennai">Zone 02, Manali, Chennai</option>
                  <option value="Zone 03, Madhavaram, Chennai">Zone 03, Madhavaram, Chennai</option>
                  <option value="Zone 04, Tondiarpet, Chennai">Zone 04, Tondiarpet, Chennai</option>
                  <option value="Zone 05, Royapuram, Chennai">Zone 05, Royapuram, Chennai</option>
                  <option value="Zone 06, Thiru. Vi. Ka. Nagar, Chennai">Zone 06, Thiru. Vi. Ka. Nagar, Chennai</option>
                  <option value="Zone 07, Ambattur, Chennai">Zone 07, Ambattur, Chennai</option>
                  <option value="Zone 08, Anna Nagar, Chennai">Zone 08, Anna Nagar, Chennai</option>
                  <option value="Zone 09, Teynampet, Chennai">Zone 09, Teynampet, Chennai</option>
                  <option value="Zone 10, Kodambakkam, Chennai">Zone 10, Kodambakkam, Chennai</option>
                  <option value="Zone 11, Valasaravakkam, Chennai">Zone 11, Valasaravakkam, Chennai</option>
                  <option value="Zone 12, Alandur, Chennai">Zone 12, Alandur, Chennai</option>
                  <option value="Zone 13, Adyar, Chennai">Zone 13, Adyar, Chennai</option>
                  <option value="Zone 14, Perungudi, Chennai">Zone 14, Perungudi, Chennai</option>
                  <option value="Zone 15, Sholinganallur, Chennai">Zone 15, Sholinganallur, Chennai</option>
                </select>
                <span className="material-symbols-outlined absolute right-3 top-2.5 text-[#667384] text-[18px] pointer-events-none">
                  expand_more
                </span>
              </div>
            </div>

            {/* Password */}
            <div>
              <label htmlFor="reg-password" className="block text-xs font-semibold text-[#18232F] uppercase tracking-wider mb-1">
                {t('auth.passwordLabel')} *
              </label>
              <div className="relative">
                <span className="material-symbols-outlined absolute left-3 top-2.5 text-[#667384] text-[18px]">
                  lock
                </span>
                <input
                  id="reg-password"
                  type={showPass ? 'text' : 'password'}
                  name="password"
                  required
                  placeholder={t('auth.passwordPlaceholder')}
                  value={formData.password}
                  onChange={handleChange}
                  className="w-full pl-9 pr-10 py-2.5 text-sm bg-slate-50 border border-[#E2E7ED] rounded-xl text-[#18232F] focus:outline-none focus:ring-2 focus:ring-[#137A70] focus:bg-white transition-all"
                />
                <button
                  type="button"
                  onClick={() => setShowPass(!showPass)}
                  className="absolute right-3 top-2.5 text-[#667384] hover:text-[#18232F] p-0.5"
                  aria-label={showPass ? 'Hide password' : 'Show password'}
                >
                  <span className="material-symbols-outlined text-[18px]">
                    {showPass ? 'visibility_off' : 'visibility'}
                  </span>
                </button>
              </div>

              {/* Password Strength Indicator */}
              {formData.password && (
                <div className="mt-2 space-y-1">
                  <div className="flex items-center justify-between text-[11px]">
                    <span className="text-[#667384]">Password strength:</span>
                    <span className="font-semibold">{strength.label}</span>
                  </div>
                  <div className="w-full h-1.5 bg-slate-100 rounded-full overflow-hidden">
                    <div className={`h-full ${strength.color} ${strength.width} transition-all duration-300`}></div>
                  </div>
                </div>
              )}
            </div>

            {/* Confirm Password */}
            <div>
              <label htmlFor="reg-confirm-password" className="block text-xs font-semibold text-[#18232F] uppercase tracking-wider mb-1">
                {t('auth.confirmPasswordLabel')} *
              </label>
              <div className="relative">
                <span className="material-symbols-outlined absolute left-3 top-2.5 text-[#667384] text-[18px]">
                  lock_reset
                </span>
                <input
                  id="reg-confirm-password"
                  type={showConfirmPass ? 'text' : 'password'}
                  name="confirmPassword"
                  required
                  placeholder={t('auth.confirmPasswordPlaceholder')}
                  value={formData.confirmPassword}
                  onChange={handleChange}
                  className="w-full pl-9 pr-10 py-2.5 text-sm bg-slate-50 border border-[#E2E7ED] rounded-xl text-[#18232F] focus:outline-none focus:ring-2 focus:ring-[#137A70] focus:bg-white transition-all"
                />
                <button
                  type="button"
                  onClick={() => setShowConfirmPass(!showConfirmPass)}
                  className="absolute right-3 top-2.5 text-[#667384] hover:text-[#18232F] p-0.5"
                  aria-label={showConfirmPass ? 'Hide password' : 'Show password'}
                >
                  <span className="material-symbols-outlined text-[18px]">
                    {showConfirmPass ? 'visibility_off' : 'visibility'}
                  </span>
                </button>
              </div>
            </div>

            {/* Consent and Privacy Disclosures */}
            <div className="space-y-2 pt-2 border-t border-[#E2E7ED] text-xs">
              <div className="flex items-start gap-2.5 text-[#18232F]">
                <input
                  id="reg-agree-terms"
                  type="checkbox"
                  name="agreeTerms"
                  checked={formData.agreeTerms}
                  onChange={handleChange}
                  className="mt-1 h-4 w-4 rounded border-gray-300 text-[#137A70] focus:ring-[#137A70] cursor-pointer"
                />
                <label htmlFor="reg-agree-terms" className="leading-relaxed select-none">
                  I have read and agree to the{' '}
                  <Link
                    to="/legal/terms"
                    target="_blank"
                    rel="noopener noreferrer"
                    onClick={(e) => e.stopPropagation()}
                    className="font-semibold text-[#137A70] hover:underline inline-flex items-center gap-0.5"
                  >
                    <span>CivicFix Terms of Service</span>
                    <span className="material-symbols-outlined text-[13px]">open_in_new</span>
                  </Link>{' '}
                  and{' '}
                  <Link
                    to="/legal/privacy"
                    target="_blank"
                    rel="noopener noreferrer"
                    onClick={(e) => e.stopPropagation()}
                    className="font-semibold text-[#137A70] hover:underline inline-flex items-center gap-0.5"
                  >
                    <span>Citizen Data Privacy Policy</span>
                    <span className="material-symbols-outlined text-[13px]">open_in_new</span>
                  </Link>.
                </label>
              </div>

              <p className="text-[11px] text-[#667384] pl-6 leading-relaxed">
                Your contact information is strictly utilized for dispatch notifications, issue status alerts, and resolution verifications.
              </p>
            </div>

            {/* Submit Button */}
            <button
              type="submit"
              disabled={loading || googleLoading}
              className="w-full min-h-[44px] py-3 bg-[#137A70] hover:bg-[#0F625B] text-white text-sm font-semibold rounded-xl shadow-sm transition-all flex items-center justify-center gap-2 disabled:opacity-60"
            >
              {loading ? (
                <>
                  <span className="material-symbols-outlined animate-spin text-[18px]">progress_activity</span>
                  <span>{t('auth.registering') || 'Registering Citizen...'}</span>
                </>
              ) : (
                <>
                  <span>{t('auth.registerButton') || 'Complete Citizen Enrollment'}</span>
                  <span className="material-symbols-outlined text-[18px]">arrow_forward</span>
                </>
              )}
            </button>
          </form>

          {/* Identity Verification Clarity Notice */}
          <div className="p-3 bg-slate-50 border border-slate-200 rounded-xl text-[11px] text-[#667384] flex items-start gap-2">
            <span className="material-symbols-outlined text-[16px] text-primary-container shrink-0 mt-0.5">
              shield_person
            </span>
            <p>
              <strong>Privacy Assurance:</strong> CivicFix does not mandate Aadhaar or biometric documents to report civic defects. Direct registration preserves resident privacy while maintaining authentic municipal accountability.
            </p>
          </div>

          {/* Direct Sign In link */}
          <div className="text-center text-xs text-[#667384] pt-2 border-t border-[#E2E7ED]">
            {t('auth.alreadyHaveAccount')}{' '}
            <Link to="/login" className="font-semibold text-[#137A70] hover:underline">
              {t('auth.loginLink')}
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
};

export default RegisterPage;
