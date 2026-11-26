import React, { useState, useRef, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../../context/AppContext';
import { useLanguage } from '../../context/LanguageContext';
import { Breadcrumb } from '../../components/common/Breadcrumb';
import ModalDialog from '../../components/common/ModalDialog';
import ThemeToggle from '../../components/common/ThemeToggle';
import AuthenticatedImage from '../../components/common/AuthenticatedImage';
import { apiClient } from '../../services/apiClient';
import { authService } from '../../services/authService';
import {
  TAMIL_NADU_DISTRICTS_BILINGUAL,
  CHENNAI_GCC_ZONES,
  getDistrictDisplayLabel,
  getZoneDisplayLabel
} from '../../data/tamilNaduLocations';

export const CitizenProfilePage = () => {
  const { currentUser, setCurrentUser, showToast, logout } = useApp();
  const { t, isTamil } = useLanguage();
  const navigate = useNavigate();

  const photoInputRef = useRef(null);
  const cameraPhotoInputRef = useRef(null);

  const initialZone = (() => {
    if (currentUser?.zone) return currentUser.zone;
    const matched = CHENNAI_GCC_ZONES.find((z) => z.value === currentUser?.ward);
    return matched ? matched.value : '';
  })();

  const [phone, setPhone] = useState(currentUser?.phone || '');
  const [district, setDistrict] = useState(currentUser?.district || (initialZone ? 'Chennai' : ''));
  const [zone, setZone] = useState(initialZone);
  const [ward, setWard] = useState(currentUser?.ward || '');
  const [locationSearch, setLocationSearch] = useState('');
  const [savingProfile, setSavingProfile] = useState(false);

  const [photoUploading, setPhotoUploading] = useState(false);
  const [photoPreviewUrl, setPhotoPreviewUrl] = useState(null);
  const [photoError, setPhotoError] = useState('');
  
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

  const filteredDistricts = useMemo(() => {
    const q = locationSearch.trim().toLowerCase();
    if (!q) return TAMIL_NADU_DISTRICTS_BILINGUAL;
    return TAMIL_NADU_DISTRICTS_BILINGUAL.filter(
      (d) => d.name.toLowerCase().includes(q) || d.taName.includes(locationSearch.trim())
    );
  }, [locationSearch]);

  const filteredZones = useMemo(() => {
    const q = locationSearch.trim().toLowerCase();
    if (!q) return CHENNAI_GCC_ZONES;
    return CHENNAI_GCC_ZONES.filter(
      (z) =>
        z.value.toLowerCase().includes(q) ||
        z.zoneName.toLowerCase().includes(q) ||
        z.taZoneName.includes(locationSearch.trim()) ||
        z.coveredWards.some((w) => w.toLowerCase().includes(q))
    );
  }, [locationSearch]);

  const selectedZoneObj = useMemo(
    () => CHENNAI_GCC_ZONES.find((z) => z.value === zone) || null,
    [zone]
  );

  const syncUpdatedUser = (backendUser) => {
    if (!backendUser) return;
    const merged = {
      ...currentUser,
      name: backendUser.name || currentUser?.name,
      phone: backendUser.phone || '',
      district: backendUser.district || null,
      zone: backendUser.zone || null,
      ward: backendUser.ward || null,
      profilePhotoUrl: backendUser.profilePhotoUrl || null
    };
    setCurrentUser(merged);
    authService.setCurrentUser(merged);
  };

  const handlePhotoFileSelected = async (fileList) => {
    const file = fileList && fileList[0];
    if (!file) return;
    setPhotoError('');

    const allowed = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp'];
    if (!allowed.includes((file.type || '').toLowerCase())) {
      setPhotoError(t('profile.photoInvalidType') || 'Only JPEG, PNG, and WebP images are supported.');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      setPhotoError(t('profile.photoTooLarge') || 'Profile photo must be under 5 MB.');
      return;
    }

    const tempPreview = URL.createObjectURL(file);
    setPhotoPreviewUrl(tempPreview);
    setPhotoUploading(true);
    try {
      const res = await apiClient.citizen.uploadProfilePhoto(file);
      const updatedUser = res?.data || res;
      syncUpdatedUser(updatedUser);
      setPhotoPreviewUrl(null);
      URL.revokeObjectURL(tempPreview);
      showToast(t('profile.photoUpdated') || 'Profile photo updated successfully!', 'success');
    } catch (err) {
      setPhotoPreviewUrl(null);
      URL.revokeObjectURL(tempPreview);
      setPhotoError(err.message || t('profile.photoUploadFailed') || 'Unable to upload profile photo.');
    } finally {
      setPhotoUploading(false);
      if (photoInputRef.current) photoInputRef.current.value = '';
      if (cameraPhotoInputRef.current) cameraPhotoInputRef.current.value = '';
    }
  };

  const handleRemovePhoto = async () => {
    setPhotoError('');
    setPhotoUploading(true);
    try {
      const res = await apiClient.citizen.deleteProfilePhoto();
      const updatedUser = res?.data || res;
      syncUpdatedUser(updatedUser);
      setPhotoPreviewUrl(null);
      showToast(t('profile.photoRemoved') || 'Profile photo removed.', 'success');
    } catch (err) {
      setPhotoError(err.message || 'Unable to remove profile photo.');
    } finally {
      setPhotoUploading(false);
    }
  };

  const handleSave = async (e) => {
    e.preventDefault();
    const effectiveWard =
      ward && ward.trim()
        ? ward.trim()
        : zone && zone.trim()
        ? zone.trim()
        : district
        ? `${district}, Tamil Nadu`
        : null;

    setSavingProfile(true);
    try {
      localStorage.setItem(`civicfix_notif_pref_${currentUser?.id || 'default'}`, JSON.stringify({
        emailAlerts,
        smsAlerts,
        phone,
        district,
        zone,
        ward: effectiveWard,
        updatedAt: new Date().toISOString()
      }));

      const res = await apiClient.citizen.updateProfile({
        phone: phone ? phone.trim() : '',
        district: district || '',
        zone: district === 'Chennai' ? (zone || '') : '',
        ward: effectiveWard || ''
      });
      const updatedUser = res?.data || res;
      syncUpdatedUser(updatedUser);
      showToast(t('profile.savedSuccess') || 'Profile and notification preferences updated successfully!', 'success');
    } catch (err) {
      showToast(err.message || t('profile.saveFailed') || 'Could not update profile on server.', 'error');
    } finally {
      setSavingProfile(false);
    }
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
        {/* Left Column: Profile Card & Photo Upload */}
        <div className="md:col-span-5 bg-surface-container-lowest rounded-2xl p-6 shadow-sm border border-outline-variant/30 text-center space-y-4">
          <input
            ref={photoInputRef}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            onChange={(e) => handlePhotoFileSelected(e.target.files)}
            className="hidden"
          />
          <input
            ref={cameraPhotoInputRef}
            type="file"
            accept="image/*"
            capture="user"
            onChange={(e) => handlePhotoFileSelected(e.target.files)}
            className="hidden"
          />

          <div className="relative w-24 h-24 rounded-full bg-primary-container text-white text-2xl font-bold flex items-center justify-center mx-auto shadow-md overflow-hidden border-2 border-outline-variant/40">
            {photoPreviewUrl ? (
              <img src={photoPreviewUrl} alt={currentUser?.name || 'Resident'} className="w-full h-full object-cover" />
            ) : currentUser?.profilePhotoUrl ? (
              <AuthenticatedImage
                src={currentUser.profilePhotoUrl}
                alt={currentUser?.name || 'Resident'}
                className="w-full h-full object-cover"
                fallback={<span>{currentUser?.avatar || (currentUser?.name ? currentUser.name.slice(0, 2).toUpperCase() : 'AM')}</span>}
              />
            ) : (
              <span>{currentUser?.avatar || (currentUser?.name ? currentUser.name.slice(0, 2).toUpperCase() : 'AM')}</span>
            )}
            {photoUploading && (
              <div className="absolute inset-0 bg-black/55 flex items-center justify-center">
                <span className="material-symbols-outlined text-white animate-spin">progress_activity</span>
              </div>
            )}
          </div>

          <div className="flex flex-wrap items-center justify-center gap-2">
            <button
              type="button"
              disabled={photoUploading}
              onClick={() => photoInputRef.current?.click()}
              className="px-3 py-1.5 rounded-lg bg-surface-container-low hover:bg-surface-container text-on-surface text-xs font-semibold border border-outline-variant/40 flex items-center gap-1 disabled:opacity-50"
            >
              <span className="material-symbols-outlined text-[15px]">upload</span>
              <span>{currentUser?.profilePhotoUrl ? (t('profile.changePhoto') || 'Change Photo') : (t('profile.uploadPhoto') || 'Upload Photo')}</span>
            </button>
            <button
              type="button"
              disabled={photoUploading}
              onClick={() => cameraPhotoInputRef.current?.click()}
              className="px-3 py-1.5 rounded-lg bg-surface-container-low hover:bg-surface-container text-on-surface text-xs font-semibold border border-outline-variant/40 flex items-center gap-1 disabled:opacity-50"
            >
              <span className="material-symbols-outlined text-[15px]">photo_camera</span>
              <span>{t('profile.cameraPhoto') || 'Camera'}</span>
            </button>
            {currentUser?.profilePhotoUrl && (
              <button
                type="button"
                disabled={photoUploading}
                onClick={handleRemovePhoto}
                className="px-3 py-1.5 rounded-lg bg-red-500/10 hover:bg-red-500/20 text-error text-xs font-semibold border border-red-500/30 flex items-center gap-1 disabled:opacity-50"
              >
                <span className="material-symbols-outlined text-[15px]">delete</span>
                <span>{t('profile.removePhoto') || 'Remove'}</span>
              </button>
            )}
          </div>

          {photoError && (
            <p className="text-xs text-error bg-error-container/40 px-3 py-1.5 rounded-lg" role="alert">
              {photoError}
            </p>
          )}

          <div>
            <h2 className="font-headline-sm text-lg font-bold text-on-surface">{currentUser?.name || 'Resident'}</h2>
            <p className="text-xs text-tertiary">{currentUser?.email || ''}</p>
            <span className="inline-block mt-2 px-3 py-1 rounded-full bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 text-xs font-semibold">
              {t('profile.verifiedResidentId') || 'Verified Resident ID'}
            </span>
          </div>

          <div className="pt-4 border-t border-outline-variant/30 text-left space-y-2.5 text-xs">
            <div className="flex justify-between gap-2">
              <span className="text-tertiary">{t('profile.trustScore') || 'Community Trust Score:'}</span>
              <span className="font-bold text-primary-container font-code">{currentUser?.trustScore ?? 90}%</span>
            </div>
            <div className="flex justify-between gap-2">
              <span className="text-tertiary">{t('profile.primaryLocation') || 'Registered Location:'}</span>
              <span className="font-semibold text-on-surface text-right">
                {currentUser?.ward
                  ? getZoneDisplayLabel(currentUser.ward, isTamil)
                  : currentUser?.district
                  ? getDistrictDisplayLabel(currentUser.district, isTamil)
                  : (t('profile.locationNotSet') || 'Location not set')}
              </span>
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

              <div className="space-y-3 pt-2 border-t border-outline-variant/30">
                <div className="flex items-center justify-between gap-2">
                  <label htmlFor="profile-location-search" className="block text-xs font-semibold text-on-surface">
                    {t('profile.residentialHierarchyLabel') || 'Residential Location (Tamil Nadu)'}
                  </label>
                  <span className="text-[11px] text-tertiary">{t('profile.stateFixed') || 'State: Tamil Nadu'}</span>
                </div>

                <input
                  id="profile-location-search"
                  type="search"
                  value={locationSearch}
                  onChange={(e) => setLocationSearch(e.target.value)}
                  placeholder={t('profile.searchLocationPlaceholder') || 'Search district, GCC zone (e.g. Royapuram), or ward...'}
                  className="w-full px-3.5 py-2 bg-surface-container-low rounded-xl text-xs text-on-surface border border-outline-variant/30 focus:border-primary-container focus:outline-none"
                />

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div className="space-y-1">
                    <label htmlFor="profile-district-select" className="block text-[11px] font-semibold text-on-surface-variant">
                      {t('profile.districtLabel') || 'District'}
                    </label>
                    <select
                      id="profile-district-select"
                      value={district}
                      onChange={(e) => {
                        const nextDist = e.target.value;
                        setDistrict(nextDist);
                        if (nextDist !== 'Chennai') {
                          setZone('');
                          setWard(nextDist ? `${nextDist}, Tamil Nadu` : '');
                        } else {
                          setWard('');
                        }
                      }}
                      className="w-full px-3 py-2.5 bg-surface-container-low rounded-xl text-sm text-on-surface border border-outline-variant/30 focus:border-primary-container focus:outline-none"
                    >
                      <option value="">{t('profile.selectDistrict') || 'Select District'}</option>
                      {filteredDistricts.map((d) => (
                        <option key={d.name} value={d.name}>
                          {isTamil ? `${d.taName} (${d.name})` : d.name}
                        </option>
                      ))}
                    </select>
                  </div>

                  {district === 'Chennai' && (
                    <div className="space-y-1">
                      <label htmlFor="profile-zone-select" className="block text-[11px] font-semibold text-on-surface-variant">
                        {t('profile.gccZoneLabel') || 'Greater Chennai Corporation Zone'}
                      </label>
                      <select
                        id="profile-zone-select"
                        value={zone}
                        onChange={(e) => {
                          const nextZone = e.target.value;
                          setZone(nextZone);
                          setWard(nextZone);
                        }}
                        className="w-full px-3 py-2.5 bg-surface-container-low rounded-xl text-sm text-on-surface border border-outline-variant/30 focus:border-primary-container focus:outline-none"
                      >
                        <option value="">{t('profile.selectZone') || 'Select GCC Zone'}</option>
                        {filteredZones.map((z) => (
                          <option key={z.zoneNumber} value={z.value}>
                            {isTamil ? z.taLabel : z.value}
                          </option>
                        ))}
                      </select>
                    </div>
                  )}
                </div>

                {district === 'Chennai' && selectedZoneObj && (
                  <div className="space-y-1">
                    <label htmlFor="profile-ward-select" className="block text-[11px] font-semibold text-on-surface-variant">
                      {t('profile.wardOptionalLabel') || 'Verified Ward (Optional)'}
                    </label>
                    <select
                      id="profile-ward-select"
                      value={selectedZoneObj.coveredWards.some((w) => ward.startsWith(w)) ? ward : ''}
                      onChange={(e) => {
                        const w = e.target.value;
                        setWard(w ? `${w}, ${selectedZoneObj.value}` : selectedZoneObj.value);
                      }}
                      className="w-full px-3 py-2.5 bg-surface-container-low rounded-xl text-sm text-on-surface border border-outline-variant/30 focus:border-primary-container focus:outline-none"
                    >
                      <option value="">{isTamil ? selectedZoneObj.taLabel : selectedZoneObj.value}</option>
                      {selectedZoneObj.coveredWards.map((w) => {
                        const fullVal = `${w}, ${selectedZoneObj.value}`;
                        return (
                          <option key={w} value={fullVal}>
                            {w} — {isTamil ? selectedZoneObj.taZoneName : selectedZoneObj.zoneName}
                          </option>
                        );
                      })}
                    </select>
                  </div>
                )}
              </div>

              <div className="pt-2 border-t border-outline-variant/30 space-y-2">
                <h4 className="text-xs font-bold text-on-surface uppercase tracking-wider">{t('profile.appearanceTheme') || 'Appearance Theme'}</h4>
                <ThemeToggle variant="selector" />
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
                disabled={savingProfile}
                className="px-5 py-2.5 bg-primary-container hover:bg-primary text-white font-semibold text-xs rounded-xl shadow transition-colors disabled:opacity-50"
              >
                {savingProfile ? (t('common.saving') || 'Saving...') : (t('profile.savePreferences') || 'Save Preferences')}
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

