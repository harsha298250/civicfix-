import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../../context/AppContext';
import { useLanguage } from '../../context/LanguageContext';
import { Breadcrumb } from '../../components/common/Breadcrumb';
import ModalDialog from '../../components/common/ModalDialog';
import { apiClient } from '../../services/apiClient';

export const CitizenProfilePage = () => {
  const { currentUser, showToast, logout } = useApp();
  const { t } = useLanguage();
  const navigate = useNavigate();

  const [phone, setPhone] = useState(currentUser?.phone || '+91 98401 23456');
  const [ward, setWard] = useState(currentUser?.ward || 'Ward 08, Anna Nagar');
  
  // Notification alert preferences (persisted across sessions)
  const [emailAlerts, setEmailAlerts] = useState(() => {
    try {
      const saved = localStorage.getItem(`civicfix_notif_pref_${currentUser?.id || 'default'}`);
      return saved ? JSON.parse(saved).emailAlerts !== false : true;
    } catch {
      return true;
    }
  });
  const [smsAlerts, setSmsAlerts] = useState(() => {
    try {
      const saved = localStorage.getItem(`civicfix_notif_pref_${currentUser?.id || 'default'}`);
      return saved ? JSON.parse(saved).smsAlerts !== false : true;
    } catch {
      return true;
    }
  });

  // Export state
  const [isExporting, setIsExporting] = useState(false);

  // Deletion modal state
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [deletePassword, setDeletePassword] = useState('');
  const [deleteError, setDeleteError] = useState('');
  const [isDeleting, setIsDeleting] = useState(false);

  const handleSave = (e) => {
    e.preventDefault();
    try {
      localStorage.setItem(`civicfix_notif_pref_${currentUser?.id || 'default'}`, JSON.stringify({
        emailAlerts,
        smsAlerts,
        phone,
        ward,
        updatedAt: new Date().toISOString()
      }));
    } catch (err) {
      console.warn('Could not persist preferences:', err);
    }
    showToast('Profile and notification preferences updated successfully!', 'success');
  };

  const handleExportData = async () => {
    setIsExporting(true);
    try {
      const data = await apiClient.citizen.exportAccountData();
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `civicfix-resident-data-${new Date().toISOString().slice(0, 10)}.json`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
      showToast(t('citizen.account.exportSuccess'), 'success');
    } catch (err) {
      console.error('Export error:', err);
      showToast(err.message || t('citizen.account.exportFailed'), 'error');
    } finally {
      setIsExporting(false);
    }
  };

  const handleDeleteAccount = async (e) => {
    e.preventDefault();
    if (!deletePassword) {
      setDeleteError(t('citizen.account.passwordRequired'));
      return;
    }
    setDeleteError('');
    setIsDeleting(true);
    try {
      await apiClient.citizen.deleteAccount(deletePassword);
      setIsDeleteModalOpen(false);
      await logout();
      showToast(t('citizen.account.deleteSuccess'), 'success');
      navigate('/');
    } catch (err) {
      console.error('Deletion error:', err);
      setDeleteError(err.message || t('citizen.account.deleteFailed'));
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <div className="w-full max-w-4xl mx-auto flex flex-col gap-6">
      <Breadcrumb
        items={[
          { label: 'Citizen Portal', path: '/citizen/dashboard', icon: 'home' },
          { label: 'Profile & Preferences' }
        ]}
      />

      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-headline-lg text-2xl sm:text-3xl font-extrabold text-on-surface tracking-tight">
            Resident Profile & Preferences
          </h1>
          <p className="text-body-md text-sm text-on-surface-variant mt-1">
            Manage your verified citizen credentials, contact details, and dispatch alert subscriptions.
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-12 gap-6 items-start">
        {/* Left Column: Profile Card */}
        <div className="md:col-span-5 bg-surface-container-lowest rounded-2xl p-6 shadow-sm border border-outline-variant/30 text-center space-y-4">
          <div className="w-20 h-20 rounded-full bg-primary-container text-white text-2xl font-bold flex items-center justify-center mx-auto shadow-md">
            {currentUser?.avatar || 'AM'}
          </div>
          <div>
            <h2 className="font-headline-sm text-lg font-bold text-on-surface">{currentUser?.name || 'Resident'}</h2>
            <p className="text-xs text-tertiary">{currentUser?.email || ''}</p>
            <span className="inline-block mt-2 px-3 py-1 rounded-full bg-emerald-500/10 text-emerald-700 text-xs font-semibold">
              Verified Resident ID
            </span>
          </div>

          <div className="pt-4 border-t border-outline-variant/30 text-left space-y-2.5 text-xs">
            <div className="flex justify-between">
              <span className="text-tertiary">Community Trust Score:</span>
              <span className="font-bold text-primary-container font-code">98% Verified</span>
            </div>
            <div className="flex justify-between">
              <span className="text-tertiary">Primary Ward:</span>
              <span className="font-semibold text-on-surface">{currentUser?.ward || ward}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-tertiary">Member Since:</span>
              <span className="font-semibold text-on-surface">January 2026</span>
            </div>
          </div>
        </div>

        {/* Right Column: Preferences Form & Account Data Management */}
        <div className="md:col-span-7 space-y-6">
          <div className="bg-surface-container-lowest rounded-2xl p-6 shadow-sm border border-outline-variant/30 space-y-5">
            <h3 className="font-title text-base font-bold text-on-surface">Update Contact & Alert Channels</h3>

            <form onSubmit={handleSave} className="space-y-4">
              <div className="space-y-1">
                <label htmlFor="registered-phone-input" className="block text-xs font-semibold text-on-surface">Registered Mobile Phone</label>
                <input
                  id="registered-phone-input"
                  type="tel"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  className="w-full px-3.5 py-2.5 bg-surface-container-low rounded-xl text-sm text-on-surface border border-transparent focus:border-primary-container focus:outline-none"
                />
              </div>

              <div className="space-y-1">
                <label htmlFor="registered-ward-input" className="block text-xs font-semibold text-on-surface">Primary Residential Ward</label>
                <input
                  id="registered-ward-input"
                  type="text"
                  value={ward}
                  onChange={(e) => setWard(e.target.value)}
                  className="w-full px-3.5 py-2.5 bg-surface-container-low rounded-xl text-sm text-on-surface border border-transparent focus:border-primary-container focus:outline-none"
                />
              </div>

              <div className="pt-2 border-t border-outline-variant/30 space-y-3">
                <h4 className="text-xs font-bold text-on-surface uppercase tracking-wider">Alert Subscriptions</h4>
                <label className="flex items-center gap-2 cursor-pointer text-xs text-on-surface">
                  <input
                    type="checkbox"
                    checked={emailAlerts}
                    onChange={(e) => setEmailAlerts(e.target.checked)}
                    className="rounded text-primary-container"
                  />
                  <span>Email alerts on work-order status changes and officer approvals</span>
                </label>
                <label className="flex items-center gap-2 cursor-pointer text-xs text-on-surface">
                  <input
                    type="checkbox"
                    checked={smsAlerts}
                    onChange={(e) => setSmsAlerts(e.target.checked)}
                    className="rounded text-primary-container"
                  />
                  <span>Instant SMS notifications when crew dispatches to site</span>
                </label>
              </div>

              <button
                type="submit"
                className="px-5 py-2.5 bg-primary-container hover:bg-primary text-white font-semibold text-xs rounded-xl shadow transition-colors"
              >
                Save Preferences
              </button>
            </form>
          </div>

          {/* Privacy & Account Management Card */}
          <div className="bg-surface-container-lowest rounded-2xl p-6 shadow-sm border border-outline-variant/30 space-y-4">
            <div>
              <h3 className="font-title text-base font-bold text-on-surface flex items-center gap-2">
                <span className="material-symbols-outlined text-primary text-xl" aria-hidden="true">privacy_tip</span>
                {t('citizen.account.privacyTitle')}
              </h3>
              <p className="text-xs text-on-surface-variant mt-1 leading-relaxed">
                {t('citizen.account.privacyDesc')}
              </p>
            </div>

            <div className="pt-3 border-t border-outline-variant/30 flex flex-wrap gap-3 items-center justify-between">
              <div>
                <button
                  type="button"
                  onClick={handleExportData}
                  disabled={isExporting}
                  className="inline-flex items-center gap-2 px-4 py-2.5 bg-surface-container-low hover:bg-surface-container text-on-surface font-semibold text-xs rounded-xl border border-outline-variant/40 transition-colors disabled:opacity-50"
                  aria-label={t('citizen.account.exportData')}
                >
                  <span className="material-symbols-outlined text-sm" aria-hidden="true">
                    {isExporting ? 'hourglass_top' : 'download'}
                  </span>
                  {isExporting ? t('citizen.account.exporting') : t('citizen.account.exportData')}
                </button>
              </div>

              <div>
                <button
                  type="button"
                  onClick={() => {
                    setDeletePassword('');
                    setDeleteError('');
                    setIsDeleteModalOpen(true);
                  }}
                  className="inline-flex items-center gap-2 px-4 py-2.5 bg-error/10 hover:bg-error/20 text-error font-semibold text-xs rounded-xl border border-error/30 transition-colors"
                  aria-label={t('citizen.account.deleteAccount')}
                >
                  <span className="material-symbols-outlined text-sm" aria-hidden="true">delete_forever</span>
                  {t('citizen.account.deleteAccount')}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Account Deletion Confirmation Modal */}
      <ModalDialog
        isOpen={isDeleteModalOpen}
        onClose={() => {
          if (!isDeleting) setIsDeleteModalOpen(false);
        }}
        title={t('citizen.account.deleteModalTitle')}
      >
        <form onSubmit={handleDeleteAccount} className="space-y-4">
          <div className="p-3 bg-error/10 border border-error/20 rounded-xl text-xs text-error leading-relaxed">
            {t('citizen.account.deleteWarning')}
          </div>

          {deleteError && (
            <div role="alert" className="p-2.5 bg-error/10 border border-error/30 rounded-lg text-xs text-error font-medium">
              {deleteError}
            </div>
          )}

          <div className="space-y-1">
            <label htmlFor="delete-account-password-input" className="block text-xs font-semibold text-on-surface">
              {t('citizen.account.passwordPrompt')}
            </label>
            <input
              id="delete-account-password-input"
              type="password"
              value={deletePassword}
              onChange={(e) => setDeletePassword(e.target.value)}
              placeholder={t('citizen.account.passwordPlaceholder')}
              required
              autoFocus
              className="w-full px-3.5 py-2.5 bg-surface-container-low rounded-xl text-sm text-on-surface border border-outline-variant/40 focus:border-error focus:outline-none"
            />
          </div>

          <div className="flex justify-end gap-3 pt-3 border-t border-outline-variant/30">
            <button
              type="button"
              disabled={isDeleting}
              onClick={() => setIsDeleteModalOpen(false)}
              className="px-4 py-2 rounded-xl text-xs font-semibold text-on-surface hover:bg-surface-container transition-colors disabled:opacity-50"
            >
              {t('common.cancel')}
            </button>
            <button
              type="submit"
              disabled={isDeleting || !deletePassword}
              className="px-4 py-2 bg-error hover:bg-error/90 text-white font-semibold text-xs rounded-xl shadow transition-colors disabled:opacity-50"
            >
              {isDeleting ? t('citizen.account.deleting') : t('citizen.account.confirmDelete')}
            </button>
          </div>
        </form>
      </ModalDialog>
    </div>
  );
};

