import React from 'react';
import { useApp } from '../../context/AppContext';
import { useTranslation } from '../../context/LanguageContext';
import { Breadcrumb } from '../../components/common/Breadcrumb';

export const OfficerProfilePage = () => {
  const { currentUser } = useApp();
  const { t } = useTranslation();

  return (
    <div className="w-full max-w-4xl mx-auto flex flex-col gap-6">
      <Breadcrumb
        items={[
          { label: t('profilePages.officer.breadcrumbCommand'), path: '/officer/dashboard', icon: 'account_balance' },
          { label: t('profilePages.officer.breadcrumbTitle') }
        ]}
      />

      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-headline-lg text-2xl sm:text-3xl font-extrabold text-on-surface tracking-tight">
            {t('profilePages.officer.title')}
          </h1>
          <p className="text-body-md text-sm text-on-surface-variant mt-1">
            {t('profilePages.officer.subtitle')}
          </p>
        </div>
      </div>

      <div className="bg-surface-container-lowest rounded-2xl p-6 sm:p-8 shadow-sm border border-outline-variant/30 space-y-6">
        <div className="flex items-center gap-4">
          <div className="w-16 h-16 rounded-2xl bg-primary flex items-center justify-center text-white text-xl font-bold shadow-md">
            {currentUser?.avatar || 'EV'}
          </div>
          <div>
            <h2 className="font-headline-sm text-xl font-bold text-on-surface">{currentUser?.name || t('profilePages.officer.defaultName')}</h2>
            <p className="text-xs text-on-surface-variant">{currentUser?.department || t('profilePages.officer.defaultDept')}</p>
            <div className="flex items-center gap-2 mt-1">
              <span className="px-2.5 py-0.5 rounded-full bg-primary/10 text-primary-container text-xs font-semibold">
                {t('profilePages.officer.badge')}
              </span>
              <span className="font-code text-xs text-tertiary">#MO-04-DISTRICT</span>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-4 border-t border-outline-variant/30 text-xs">
          <div className="p-4 rounded-xl bg-surface-container-low space-y-1 min-w-0">
            <span className="text-tertiary block text-[10px] uppercase">{t('profilePages.officer.officialEmail')}</span>
            <span className="font-semibold text-on-surface break-all block">{currentUser?.email || 'officer@civicfix.gov'}</span>
          </div>
          <div className="p-4 rounded-xl bg-surface-container-low space-y-1 min-w-0">
            <span className="text-tertiary block text-[10px] uppercase">{t('profilePages.officer.officialContact')}</span>
            <span className="font-semibold text-on-surface break-words block">{currentUser?.phone || '+91 94440 12345'}</span>
          </div>
          <div className="p-4 rounded-xl bg-surface-container-low space-y-1 min-w-0">
            <span className="text-tertiary block text-[10px] uppercase">{t('profilePages.officer.assignedZone')}</span>
            <span className="font-semibold text-on-surface break-words block">{currentUser.ward || t('profilePages.officer.defaultZone')}</span>
          </div>
          <div className="p-4 rounded-xl bg-surface-container-low space-y-1 min-w-0">
            <span className="text-tertiary block text-[10px] uppercase">{t('profilePages.officer.securityClearance')}</span>
            <span className="font-semibold text-emerald-700 break-words block">{t('profilePages.officer.clearanceValue')}</span>
          </div>
        </div>
      </div>
    </div>
  );
};
