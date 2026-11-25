import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from '../../context/LanguageContext';
import LanguageToggle from '../../components/common/LanguageToggle';
import { authService } from '../../services/authService';
import { firebaseAuthService } from '../../services/firebaseAuth';

export const ForgotPasswordPage = () => {
  const { t } = useTranslation();

  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!email.trim()) {
      setErrorMessage(t('auth.registeredEmailLabel'));
      return;
    }

    setLoading(true);
    setErrorMessage('');

    try {
      if (firebaseAuthService.isConfigured()) {
        await firebaseAuthService.sendPasswordReset(email.trim());
      } else {
        await authService.forgotPassword(email.trim());
      }
      setSubmitted(true);
    } catch (err) {
      setErrorMessage(err.message || t('auth.errorUnreachable'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full min-h-[calc(100vh-64px)] flex flex-col items-center justify-center p-4 sm:p-6 bg-surface">
      <div className="w-full max-w-md mx-auto bg-surface-container-lowest rounded-2xl p-6 sm:p-8 shadow-xl border border-outline-variant/40 space-y-6">
        <div className="flex items-center justify-between pb-2 border-b border-outline-variant/30">
          <span className="text-[11px] font-bold text-tertiary uppercase tracking-wider">{t('common.language')}</span>
          <LanguageToggle />
        </div>

        <div>
          <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-surface-container-high text-on-surface-variant text-xs font-semibold mb-3">
            <span className="material-symbols-outlined text-[16px] text-primary">lock_reset</span>
            <span>{t('auth.forgotPasswordRecoveryBadge')}</span>
          </div>
          <h1 className="font-headline-sm text-2xl font-bold text-on-surface">
            {t('auth.forgotPasswordTitle')}
          </h1>
          <p className="text-body-md text-sm text-on-surface-variant mt-2 leading-relaxed">
            {t('auth.forgotPasswordDesc')}
          </p>
        </div>

        {errorMessage && (
          <div role="alert" aria-live="assertive" className="p-3.5 bg-red-50 border border-red-200 rounded-xl flex items-start gap-2.5 text-xs text-red-800">
            <span className="material-symbols-outlined text-red-600 text-[18px] shrink-0 mt-0.5">error</span>
            <span>{errorMessage}</span>
          </div>
        )}

        {submitted ? (
          <div className="space-y-4">
            <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200 space-y-2 text-xs text-emerald-900">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-emerald-600 text-[20px]">check_circle</span>
                <strong className="text-sm font-bold">{t('auth.checkInboxTitle')}</strong>
              </div>
              <p className="leading-relaxed text-emerald-800">
                {t('auth.checkInboxDesc', 'If an account exists with this email, instructions have been sent to reset your password. For security reasons, we do not reveal whether an email is registered.')}
              </p>
            </div>

            <div className="p-3 bg-surface-container-low rounded-xl border border-outline-variant/30 text-[11px] text-on-surface-variant space-y-1">
              <span className="font-semibold block text-on-surface">{t('auth.didntReceiveEmail')}</span>
              <p>{t('auth.didntReceiveEmailDesc')}</p>
            </div>

            <div className="pt-2 text-center">
              <Link
                to="/login"
                className="w-full h-11 rounded-xl bg-primary-container hover:bg-primary text-white font-semibold text-sm transition-all flex items-center justify-center gap-2 shadow-sm"
              >
                <span className="material-symbols-outlined text-[18px]">arrow_back</span>
                <span>{t('auth.backToSignIn')}</span>
              </Link>
            </div>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-1">
              <label htmlFor="recovery-email" className="block text-xs font-semibold text-on-surface">
                {t('auth.registeredEmailLabel')} *
              </label>
              <div className="relative">
                <span className="material-symbols-outlined absolute left-3 top-2.5 text-on-surface-variant text-[18px]">
                  mail
                </span>
                <input
                  id="recovery-email"
                  type="email"
                  required
                  placeholder={t('auth.emailPlaceholder')}
                  value={email}
                  onChange={(e) => {
                    setEmail(e.target.value);
                    if (errorMessage) setErrorMessage('');
                  }}
                  className="w-full pl-9 pr-3.5 py-2.5 bg-surface-container-low rounded-xl text-sm text-on-surface placeholder:text-outline border border-transparent focus:border-primary-container focus:bg-surface-container-lowest focus:outline-none transition-all"
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full h-11 rounded-xl bg-primary-container hover:bg-primary text-white font-semibold text-sm transition-all flex items-center justify-center gap-2 shadow-sm disabled:opacity-50"
            >
              {loading ? (
                <>
                  <span className="material-symbols-outlined text-[18px] animate-spin">progress_activity</span>
                  <span>{t('auth.sendingResetLink')}</span>
                </>
              ) : (
                <>
                  <span className="material-symbols-outlined text-[18px]">send</span>
                  <span>{t('auth.sendResetLinkButton')}</span>
                </>
              )}
            </button>

            <div className="pt-2 text-center">
              <Link
                to="/login"
                className="text-xs text-primary font-semibold hover:underline inline-flex items-center gap-1"
              >
                <span className="material-symbols-outlined text-[14px]">arrow_back</span>
                <span>{t('auth.backToSignIn')}</span>
              </Link>
            </div>
          </form>
        )}
      </div>
    </div>
  );
};

export default ForgotPasswordPage;
