import React, { useState, useEffect } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useTranslation } from '../../context/LanguageContext';
import LanguageToggle from '../../components/common/LanguageToggle';
import InstallPwaButton from '../../components/common/InstallPwaButton';
import { authService } from '../../services/authService';
import { firebaseAuthService } from '../../services/firebaseAuth';

export const VerifyEmailPage = () => {
  const [searchParams] = useSearchParams();
  const rawToken = searchParams.get('token') || searchParams.get('oobCode') || '';
  const isFirebaseCode = searchParams.has('oobCode') || searchParams.get('mode') === 'verifyEmail';
  const { t } = useTranslation();

  const [verifying, setVerifying] = useState(!!rawToken.trim());
  const [status, setStatus] = useState(rawToken.trim() ? 'verifying' : 'idle'); // 'idle' | 'verifying' | 'success' | 'error'
  const [statusMessage, setStatusMessage] = useState('');

  // Resend form state
  const [resendEmail, setResendEmail] = useState('');
  const [resending, setResending] = useState(false);
  const [resendResult, setResendResult] = useState(null); // { type: 'success' | 'error', message: string }

  useEffect(() => {
    let mounted = true;
    if (!rawToken.trim()) {
      setStatus('idle');
      return;
    }

    setVerifying(true);
    const verifyPromise = (isFirebaseCode && firebaseAuthService.isConfigured())
      ? firebaseAuthService.verifyEmailWithActionCode(rawToken.trim())
          .then(() => ({ message: 'Your email address has been verified successfully. Your CivicFix account is now active.' }))
      : authService.verifyEmail(rawToken.trim());

    verifyPromise
      .then((res) => {
        if (!mounted) return;
        setStatus('success');
        setStatusMessage(res?.message || 'Your email address has been verified successfully. Your CivicFix account is now active.');
      })
      .catch((err) => {
        if (!mounted) return;
        setStatus('error');
        setStatusMessage(err?.message || 'Verification link is invalid, expired, or has already been used.');
      })
      .finally(() => {
        if (mounted) setVerifying(false);
      });

    return () => {
      mounted = false;
    };
  }, [rawToken, isFirebaseCode]);

  const handleResend = async (e) => {
    e.preventDefault();
    if (!resendEmail.trim() || !resendEmail.includes('@')) {
      setResendResult({ type: 'error', message: 'Please enter a valid email address.' });
      return;
    }

    setResending(true);
    setResendResult(null);

    try {
      const res = await authService.resendVerification(resendEmail.trim());
      setResendResult({
        type: 'success',
        message: res?.message || 'A new verification link has been sent to your email address.'
      });
    } catch (err) {
      setResendResult({
        type: 'error',
        message: err?.message || 'Failed to resend verification link. Please try again.'
      });
    } finally {
      setResending(false);
    }
  };

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

          {/* Verification in Progress */}
          {status === 'verifying' && (
            <div className="text-center space-y-4 py-4 animate-fade-in">
              <div className="w-14 h-14 rounded-2xl bg-[#E8F4F1] text-[#137A70] flex items-center justify-center mx-auto">
                <span className="material-symbols-outlined text-[32px] animate-spin">progress_activity</span>
              </div>
              <div className="space-y-1">
                <h1 className="text-xl font-bold text-[#18232F]">Verifying Email Address</h1>
                <p className="text-xs text-[#667384]">Connecting to CivicFix municipal security service...</p>
              </div>
            </div>
          )}

          {/* Verification Success */}
          {status === 'success' && (
            <div className="text-center space-y-4 py-2 animate-fade-in">
              <div className="w-14 h-14 rounded-2xl bg-emerald-50 border border-emerald-200 text-emerald-700 flex items-center justify-center mx-auto">
                <span className="material-symbols-outlined text-[32px]">verified</span>
              </div>
              <div className="space-y-1">
                <h1 className="text-xl font-bold text-[#18232F]">Email Verified!</h1>
                <p className="text-xs text-emerald-700 leading-relaxed font-medium">
                  {statusMessage}
                </p>
                <p className="text-xs text-[#667384] pt-1">
                  You can now sign in with your email and password to access the CivicFix portal.
                </p>
              </div>
              <div className="pt-4">
                <Link
                  to="/login"
                  className="w-full min-h-[44px] py-2.5 bg-[#137A70] hover:bg-[#0F625B] text-white text-sm font-semibold rounded-xl shadow-sm transition-all flex items-center justify-center gap-2"
                >
                  <span className="material-symbols-outlined text-[18px]">login</span>
                  <span>Sign In Now</span>
                </Link>
              </div>
            </div>
          )}

          {/* Verification Error or No Token */}
          {(status === 'error' || status === 'idle') && (
            <div className="space-y-4 animate-fade-in">
              <div className="text-center space-y-2">
                <div className={`w-14 h-14 rounded-2xl flex items-center justify-center mx-auto ${
                  status === 'error' ? 'bg-red-50 border border-red-200 text-red-700' : 'bg-amber-50 border border-amber-200 text-amber-700'
                }`}>
                  <span className="material-symbols-outlined text-[32px]">
                    {status === 'error' ? 'error' : 'mark_email_unread'}
                  </span>
                </div>
                <h1 className="text-xl font-bold text-[#18232F]">
                  {status === 'error' ? 'Verification Failed' : 'Email Verification Required'}
                </h1>
                <p className="text-xs text-[#667384] leading-relaxed">
                  {status === 'error'
                    ? (statusMessage || 'The verification link is invalid, has expired, or has already been used.')
                    : 'Please check your email inbox for the verification link sent when you enrolled.'}
                </p>
              </div>

              {/* Resend Verification Form */}
              <div className="pt-2 border-t border-[#E2E7ED] space-y-3">
                <p className="text-xs font-semibold text-[#18232F]">Request a New Verification Link</p>

                {resendResult && (
                  <div className={`p-3 rounded-xl text-xs flex items-start gap-2 border ${
                    resendResult.type === 'success'
                      ? 'bg-emerald-50 border-emerald-200 text-emerald-800'
                      : 'bg-red-50 border-red-200 text-red-800'
                  }`}>
                    <span className="material-symbols-outlined text-[16px] shrink-0 mt-0.5">
                      {resendResult.type === 'success' ? 'check_circle' : 'error'}
                    </span>
                    <p className="leading-relaxed">{resendResult.message}</p>
                  </div>
                )}

                <form onSubmit={handleResend} className="space-y-3">
                  <div>
                    <label htmlFor="resend-email" className="block text-xs font-semibold text-[#18232F] mb-1">
                      Registered Email Address
                    </label>
                    <div className="relative">
                      <span className="material-symbols-outlined absolute left-3 top-2.5 text-[#667384] text-[18px]">
                        alternate_email
                      </span>
                      <input
                        id="resend-email"
                        type="email"
                        required
                        placeholder="your.email@example.com"
                        value={resendEmail}
                        onChange={(e) => setResendEmail(e.target.value)}
                        className="w-full pl-9 pr-3.5 py-2.5 text-sm bg-slate-50 border border-[#E2E7ED] rounded-xl text-[#18232F] focus:outline-none focus:ring-2 focus:ring-[#137A70] focus:bg-white transition-all font-sans"
                      />
                    </div>
                  </div>

                  <button
                    type="submit"
                    disabled={resending}
                    className="w-full min-h-[40px] py-2 bg-[#137A70] hover:bg-[#0F625B] text-white text-xs font-semibold rounded-xl shadow-xs transition-all flex items-center justify-center gap-2 disabled:opacity-60"
                  >
                    {resending ? (
                      <>
                        <span className="material-symbols-outlined animate-spin text-[16px]">progress_activity</span>
                        <span>Sending Link...</span>
                      </>
                    ) : (
                      <>
                        <span className="material-symbols-outlined text-[16px]">send</span>
                        <span>Send Verification Email</span>
                      </>
                    )}
                  </button>
                </form>
              </div>

              {/* Navigation links */}
              <div className="pt-2 flex flex-col gap-2 text-center">
                <Link
                  to="/login"
                  className="w-full h-10 rounded-xl bg-slate-100 hover:bg-slate-200 text-[#18232F] font-semibold text-xs transition-all flex items-center justify-center gap-1.5"
                >
                  <span className="material-symbols-outlined text-[16px]">arrow_back</span>
                  <span>Return to Sign In</span>
                </Link>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default VerifyEmailPage;
