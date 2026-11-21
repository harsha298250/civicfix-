import React, { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useApp } from '../../context/AppContext';
import { authService } from '../../services/authService';

export const ResetPasswordPage = () => {
  const [searchParams] = useSearchParams();
  const rawToken = searchParams.get('token') || '';

  const { showToast } = useApp();
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPass, setShowPass] = useState(false);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [success, setSuccess] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrorMessage('');

    if (!rawToken.trim()) {
      setErrorMessage('Missing password reset token. Please request a new reset link.');
      return;
    }

    if (password.length < 8) {
      setErrorMessage('Password must be at least 8 characters long.');
      return;
    }

    if (password !== confirmPassword) {
      setErrorMessage('Passwords do not match. Please verify both fields.');
      return;
    }

    setLoading(true);

    try {
      await authService.resetPassword(rawToken.trim(), password);
      setSuccess(true);
      showToast('Password updated successfully! You can now log in.', 'success');
    } catch (err) {
      setErrorMessage(err.message || 'Unable to reset password. The link may have expired or already been used.');
    } finally {
      setLoading(false);
    }
  };

  if (!rawToken.trim()) {
    return (
      <div className="w-full min-h-[calc(100vh-64px)] flex flex-col items-center justify-center p-4 sm:p-6 bg-surface">
        <div className="w-full max-w-md mx-auto bg-surface-container-lowest rounded-2xl p-6 sm:p-8 shadow-xl border border-outline-variant/40 space-y-6 text-center">
          <div className="w-14 h-14 rounded-2xl bg-amber-50 border border-amber-200 text-amber-700 flex items-center justify-center mx-auto">
            <span className="material-symbols-outlined text-[32px]">link_off</span>
          </div>
          <div className="space-y-2">
            <h1 className="font-headline-sm text-xl font-bold text-on-surface">Invalid Reset Link</h1>
            <p className="text-xs text-on-surface-variant max-w-xs mx-auto leading-relaxed">
              No password reset token was detected in the URL. Reset links must be accessed directly from your notification email.
            </p>
          </div>
          <div className="pt-2 flex flex-col gap-2">
            <Link
              to="/forgot-password"
              className="w-full h-11 rounded-xl bg-primary-container hover:bg-primary text-white font-semibold text-xs transition-all flex items-center justify-center gap-2 shadow-sm"
            >
              <span>Request New Reset Link</span>
            </Link>
            <Link
              to="/login"
              className="w-full h-10 rounded-xl bg-surface-container hover:bg-surface-container-high text-on-surface font-semibold text-xs transition-all flex items-center justify-center gap-1"
            >
              <span>Back to Sign In</span>
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="w-full min-h-[calc(100vh-64px)] flex flex-col items-center justify-center p-4 sm:p-6 bg-surface">
      <div className="w-full max-w-md mx-auto bg-surface-container-lowest rounded-2xl p-6 sm:p-8 shadow-xl border border-outline-variant/40 space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <span className="font-label-sm uppercase tracking-wider text-primary font-bold text-xs">
              Account Security
            </span>
            <h1 className="font-headline-sm text-2xl font-bold text-on-surface mt-0.5">
              Create a new password
            </h1>
          </div>
          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-primary/10 text-primary-container text-xs font-semibold">
            <span className="material-symbols-outlined text-[14px]">lock_reset</span>
            <span>Single-Use</span>
          </span>
        </div>

        {errorMessage && (
          <div role="alert" aria-live="assertive" className="p-3.5 bg-red-50 border border-red-200 rounded-xl flex items-start gap-2.5 text-xs text-red-800">
            <span className="material-symbols-outlined text-red-600 text-[18px] shrink-0 mt-0.5">error</span>
            <div className="space-y-1">
              <span className="block font-semibold">Password Reset Failed</span>
              <p>{errorMessage}</p>
            </div>
          </div>
        )}

        {success ? (
          <div className="space-y-4">
            <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200 space-y-2 text-xs text-emerald-900">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-emerald-600 text-[22px]">check_circle</span>
                <strong className="text-sm font-bold">Password Reset Complete</strong>
              </div>
              <p className="leading-relaxed text-emerald-800">
                Your new password has been securely saved. Any other outstanding recovery sessions have been invalidated.
              </p>
            </div>

            <div className="pt-2">
              <Link
                to="/login"
                className="w-full h-11 rounded-xl bg-primary-container hover:bg-primary text-white font-semibold text-sm transition-all flex items-center justify-center gap-2 shadow-sm"
              >
                <span className="material-symbols-outlined text-[18px]">login</span>
                <span>Sign In With New Password</span>
              </Link>
            </div>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-1">
              <label className="block text-xs font-semibold text-on-surface" htmlFor="new-pass">
                New Password *
              </label>
              <div className="relative">
                <span className="material-symbols-outlined absolute left-3 top-2.5 text-on-surface-variant text-[18px]">
                  lock
                </span>
                <input
                  id="new-pass"
                  type={showPass ? 'text' : 'password'}
                  required
                  placeholder="At least 8 characters"
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    if (errorMessage) setErrorMessage('');
                  }}
                  className="w-full pl-9 pr-10 py-2.5 bg-surface-container-low rounded-xl text-sm text-on-surface placeholder:text-outline border border-transparent focus:border-primary-container focus:bg-surface-container-lowest focus:outline-none transition-all"
                />
                <button
                  type="button"
                  onClick={() => setShowPass(!showPass)}
                  className="absolute right-3 top-2.5 text-on-surface-variant"
                  aria-label={showPass ? 'Hide password' : 'Show password'}
                >
                  <span className="material-symbols-outlined text-[18px]">
                    {showPass ? 'visibility_off' : 'visibility'}
                  </span>
                </button>
              </div>
            </div>

            <div className="space-y-1">
              <label className="block text-xs font-semibold text-on-surface" htmlFor="confirm-pass">
                Confirm New Password *
              </label>
              <div className="relative">
                <span className="material-symbols-outlined absolute left-3 top-2.5 text-on-surface-variant text-[18px]">
                  lock_reset
                </span>
                <input
                  id="confirm-pass"
                  type={showPass ? 'text' : 'password'}
                  required
                  placeholder="Re-enter new password"
                  value={confirmPassword}
                  onChange={(e) => {
                    setConfirmPassword(e.target.value);
                    if (errorMessage) setErrorMessage('');
                  }}
                  className="w-full pl-9 pr-10 py-2.5 bg-surface-container-low rounded-xl text-sm text-on-surface placeholder:text-outline border border-transparent focus:border-primary-container focus:bg-surface-container-lowest focus:outline-none transition-all"
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
                  <span>Updating Password...</span>
                </>
              ) : (
                <>
                  <span className="material-symbols-outlined text-[18px]">check</span>
                  <span>Update Password</span>
                </>
              )}
            </button>

            <div className="pt-2 text-center">
              <Link
                to="/login"
                className="text-xs text-primary font-semibold hover:underline inline-flex items-center gap-1"
              >
                <span className="material-symbols-outlined text-[14px]">arrow_back</span>
                <span>Return to Sign In</span>
              </Link>
            </div>
          </form>
        )}
      </div>
    </div>
  );
};

export default ResetPasswordPage;
