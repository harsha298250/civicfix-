import React, { useState, useEffect } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router-dom';
import { useTranslation } from '../../context/LanguageContext';
import { translateErrorMessage } from '../../utils/i18nHelpers';
import { apiClient } from '../../services/apiClient';
import { firebaseAuthService } from '../../services/firebaseAuth';

export default function StaffActivationPage() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get('token');

  const [loading, setLoading] = useState(true);
  const [staffInfo, setStaffInfo] = useState(null);
  const [verifyError, setVerifyError] = useState('');

  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState('');
  const [success, setSuccess] = useState(false);

  useEffect(() => {
    if (!token) {
      setVerifyError(t('staffActivation.noToken'));
      setLoading(false);
      return;
    }

    let isMounted = true;
    apiClient.auth.verifyStaffActivation(token)
      .then(res => {
        if (!isMounted) return;
        if (res?.data) {
          setStaffInfo(res.data);
        } else {
          setVerifyError(t('staffActivation.invalidResponse'));
        }
      })
      .catch(err => {
        if (!isMounted) return;
        setVerifyError(translateErrorMessage(err, t, 'staffActivation.invalidOrExpired'));
      })
      .finally(() => {
        if (isMounted) setLoading(false);
      });

    return () => { isMounted = false; };
  }, [token, t]);

  // Validation rules
  const hasMinLength = password.length >= 8;
  const hasUpper = /[A-Z]/.test(password);
  const hasLower = /[a-z]/.test(password);
  const hasDigit = /[0-9]/.test(password);
  const hasSpecial = /[^A-Za-z0-9]/.test(password);
  const isMatch = password && password === confirmPassword;
  const isValid = hasMinLength && hasUpper && hasLower && hasDigit && hasSpecial && isMatch;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!isValid) return;

    setSubmitting(true);
    setSubmitError('');

    try {
      let idToken = null;
      if (firebaseAuthService.isConfigured() && staffInfo?.email) {
        try {
          idToken = await firebaseAuthService.registerOrLinkStaff(staffInfo.email, password);
        } catch (fbErr) {
          console.warn('[StaffActivation] Firebase auth warning:', fbErr.message);
          if (fbErr.code === 'auth/weak-password' || fbErr.code === 'auth/invalid-email') {
            setSubmitError(fbErr.message);
            setSubmitting(false);
            return;
          }
        }
      }
      await apiClient.auth.activateStaff({ token, newPassword: password, idToken });
      setSuccess(true);
    } catch (err) {
      setSubmitError(translateErrorMessage(err, t, 'staffActivation.activateFailed'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        <div className="flex justify-center items-center space-x-2">
          <div className="w-10 h-10 rounded-xl bg-blue-600 flex items-center justify-center text-white font-bold text-xl shadow-md">
            CF
          </div>
          <span className="text-2xl font-bold tracking-tight text-slate-900">CivicFix AI</span>
        </div>
        <h2 className="mt-4 text-center text-2xl font-bold tracking-tight text-slate-900">
          {t('staffActivation.title')}
        </h2>
        <p className="mt-1 text-center text-sm text-slate-600">
          {t('staffActivation.subtitle')}
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="bg-white py-8 px-6 shadow-sm rounded-2xl border border-slate-200 sm:px-10">
          {loading ? (
            <div className="py-12 flex flex-col items-center justify-center space-y-4">
              <div className="w-10 h-10 border-4 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
              <p className="text-sm font-medium text-slate-600">{t('staffActivation.verifying')}</p>
            </div>
          ) : verifyError ? (
            <div className="space-y-6">
              <div className="p-4 bg-red-50 border border-red-200 rounded-xl text-red-800 text-sm">
                <div className="flex items-start space-x-3">
                  <span className="text-red-500 font-bold text-lg">⚠️</span>
                  <div>
                    <h3 className="font-semibold text-red-900">{t('staffActivation.linkInvalidTitle')}</h3>
                    <p className="mt-1">{verifyError}</p>
                  </div>
                </div>
              </div>
              <p className="text-xs text-slate-500 text-center">
                {t('staffActivation.expiryNote')}
              </p>
              <div className="pt-2 text-center">
                <Link
                  to="/login"
                  className="w-full inline-flex justify-center items-center px-4 py-2.5 text-sm font-semibold rounded-xl text-white bg-blue-600 hover:bg-blue-700 shadow-sm transition-colors"
                >
                  {t('staffActivation.returnToLogin')}
                </Link>
              </div>
            </div>
          ) : success ? (
            <div className="space-y-6 text-center py-4">
              <div className="w-14 h-14 bg-emerald-100 text-emerald-600 rounded-full flex items-center justify-center mx-auto text-2xl shadow-inner">
                ✓
              </div>
              <div>
                <h3 className="text-lg font-bold text-slate-900">{t('staffActivation.successTitle')}</h3>
                <p className="mt-2 text-sm text-slate-600">
                  {t('staffActivation.successDesc')}
                </p>
              </div>
              <button
                type="button"
                onClick={() => navigate('/login')}
                className="w-full inline-flex justify-center items-center px-4 py-2.5 text-sm font-semibold rounded-xl text-white bg-blue-600 hover:bg-blue-700 shadow-sm transition-colors"
              >
                {t('staffActivation.signInNow')}
              </button>
            </div>
          ) : (
            <div>
              {/* Staff Details Card */}
              <div className="mb-6 p-4 bg-blue-50/70 border border-blue-200/60 rounded-xl space-y-2">
                <div className="text-xs font-semibold text-blue-800 uppercase tracking-wider">
                  {t('staffActivation.verifiedStaffMember')}
                </div>
                <div className="text-base font-bold text-slate-900">
                  {staffInfo?.fullName}
                </div>
                <div className="text-xs text-slate-600 flex flex-wrap gap-y-1 gap-x-3">
                  <span><strong className="text-slate-700">{t('staffActivation.emailLabel')}</strong> {staffInfo?.email}</span>
                  <span><strong className="text-slate-700">{t('staffActivation.roleLabel')}</strong> {staffInfo?.role}</span>
                  {staffInfo?.department && (
                    <span><strong className="text-slate-700">{t('staffActivation.deptLabel')}</strong> {staffInfo?.department}</span>
                  )}
                </div>
              </div>

              {submitError && (
                <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-xs font-medium">
                  {submitError}
                </div>
              )}

              <form onSubmit={handleSubmit} className="space-y-5">
                <div>
                  <div className="flex justify-between items-center mb-1">
                    <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                      {t('staffActivation.newPassword')}
                    </label>
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      className="text-xs text-blue-600 hover:text-blue-800 font-medium"
                    >
                      {showPassword ? t('staffActivation.hide') : t('staffActivation.show')}
                    </button>
                  </div>
                  <input
                    type={showPassword ? 'text' : 'password'}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                    placeholder={t('staffActivation.passwordPlaceholder')}
                    className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-600 focus:bg-white"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                    {t('staffActivation.confirmPassword')}
                  </label>
                  <input
                    type={showPassword ? 'text' : 'password'}
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    required
                    placeholder={t('staffActivation.confirmPlaceholder')}
                    className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-600 focus:bg-white"
                  />
                </div>

                {/* Password Criteria Checklist */}
                <div className="p-3 bg-slate-50 border border-slate-200 rounded-xl space-y-1.5 text-xs text-slate-600">
                  <div className="font-semibold text-slate-700 mb-1">{t('staffActivation.requirementsTitle')}</div>
                  <div className={`flex items-center space-x-2 ${hasMinLength ? 'text-emerald-700 font-medium' : 'text-slate-500'}`}>
                    <span>{hasMinLength ? '✓' : '•'}</span>
                    <span>{t('staffActivation.reqLength')}</span>
                  </div>
                  <div className={`flex items-center space-x-2 ${hasUpper && hasLower ? 'text-emerald-700 font-medium' : 'text-slate-500'}`}>
                    <span>{hasUpper && hasLower ? '✓' : '•'}</span>
                    <span>{t('staffActivation.reqUpperLower')}</span>
                  </div>
                  <div className={`flex items-center space-x-2 ${hasDigit ? 'text-emerald-700 font-medium' : 'text-slate-500'}`}>
                    <span>{hasDigit ? '✓' : '•'}</span>
                    <span>{t('staffActivation.reqDigit')}</span>
                  </div>
                  <div className={`flex items-center space-x-2 ${hasSpecial ? 'text-emerald-700 font-medium' : 'text-slate-500'}`}>
                    <span>{hasSpecial ? '✓' : '•'}</span>
                    <span>{t('staffActivation.reqSpecial')}</span>
                  </div>
                  <div className={`flex items-center space-x-2 ${isMatch ? 'text-emerald-700 font-medium' : 'text-slate-500'}`}>
                    <span>{isMatch ? '✓' : '•'}</span>
                    <span>{t('staffActivation.reqMatch')}</span>
                  </div>
                </div>

                <button
                  type="submit"
                  disabled={!isValid || submitting}
                  className={`w-full py-2.5 px-4 rounded-xl text-sm font-semibold text-white shadow-sm transition-all ${
                    isValid && !submitting
                      ? 'bg-blue-600 hover:bg-blue-700 cursor-pointer'
                      : 'bg-slate-300 cursor-not-allowed opacity-70'
                  }`}
                >
                  {submitting ? t('staffActivation.activating') : t('staffActivation.submitBtn')}
                </button>
              </form>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
