import React from 'react';
import { useApp } from '../../context/AppContext';
import { useTranslation } from '../../context/LanguageContext';
import Breadcrumb from '../../components/common/Breadcrumb';

const AdminProfilePage = () => {
  const { currentPersona } = useApp();
  const { t } = useTranslation();

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <Breadcrumb
        items={[
          { label: t('profilePages.admin.breadcrumbConsole'), to: '/admin/dashboard' },
          { label: t('profilePages.admin.breadcrumbTitle'), to: '/admin/profile' },
        ]}
      />

      <div className="bg-white rounded-xl border border-[#E2E7ED] shadow-sm p-6 sm:p-8">
        <div className="flex flex-col sm:flex-row items-center sm:items-start gap-6 pb-6 border-b border-[#E2E7ED]">
          <div className="w-20 h-20 rounded-2xl bg-purple-100 text-purple-700 font-bold text-3xl flex items-center justify-center border border-purple-200">
            {(currentPersona?.name || t('profilePages.admin.defaultName')).split(' ').filter(Boolean).map((n) => n[0]).join('')}
          </div>
          <div className="flex-1 text-center sm:text-left">
            <div className="flex flex-col sm:flex-row sm:items-center gap-2">
              <h1 className="text-2xl font-bold text-[#18232F]">{currentPersona?.name || t('profilePages.admin.defaultName')}</h1>
              <span className="inline-block px-3 py-0.5 rounded-full text-xs font-semibold bg-purple-100 text-purple-800">
                {t('profilePages.admin.badge')}
              </span>
            </div>
            <p className="text-sm text-[#667384] mt-1">{currentPersona?.email || 'admin@civicfix.gov'}</p>
            <p className="text-xs text-[#667384] mt-0.5">{t('profilePages.admin.roleDesc')}</p>
          </div>
        </div>

        <div className="pt-6 space-y-6">
          <h2 className="text-base font-bold text-[#18232F]">{t('profilePages.admin.rootCreds')}</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
            <div className="p-4 rounded-lg bg-slate-50 border border-[#E2E7ED]">
              <span className="text-xs text-[#667384]">{t('profilePages.admin.hardwareKey')}</span>
              <p className="font-semibold text-[#18232F] mt-1">{t('profilePages.admin.hardwareKeyVal')}</p>
            </div>
            <div className="p-4 rounded-lg bg-slate-50 border border-[#E2E7ED]">
              <span className="text-xs text-[#667384]">{t('profilePages.admin.sessionIsolation')}</span>
              <p className="font-semibold text-[#18232F] mt-1">{t('profilePages.admin.sessionIsolationVal')}</p>
            </div>
          </div>

          <div className="pt-4 flex items-center justify-between border-t border-[#E2E7ED]">
            <span className="text-xs text-[#667384]">{t('profilePages.admin.sessionManaged')}</span>
            <span className="text-xs font-semibold text-emerald-700 bg-emerald-50 px-3 py-1 rounded-full border border-emerald-200">
              {t('profilePages.admin.sessionActive')}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default AdminProfilePage;
