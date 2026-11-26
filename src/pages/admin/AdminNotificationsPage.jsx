import React, { useState, useEffect } from 'react';
import { useApp } from '../../context/AppContext';
import { useTranslation } from '../../context/LanguageContext';
import Breadcrumb from '../../components/common/Breadcrumb';
import { translateNotificationItem } from '../../utils/i18nHelpers';

export const AdminNotificationsPage = () => {
  const { t } = useTranslation();
  const {
    notifications,
    unreadCount,
    notificationsLoading,
    notificationsPagination,
    refreshNotifications,
    markNotificationRead,
    markAllNotificationsRead,
    showToast
  } = useApp();

  const [unreadOnly, setUnreadOnly] = useState(false);

  useEffect(() => {
    refreshNotifications({ page: 0, size: 15, unreadOnly });
  }, [unreadOnly]);

  const handlePageChange = (newPage) => {
    if (newPage < 0 || newPage >= notificationsPagination.totalPages) return;
    refreshNotifications({ page: newPage, size: notificationsPagination.size, unreadOnly });
  };

  const handleAcknowledgeAll = async () => {
    await markAllNotificationsRead();
    showToast(t('notifications.allAckToast'), 'success');
  };

  return (
    <div className="space-y-6">
      <Breadcrumb
        items={[
          { label: t('common.adminConsole'), to: '/admin/dashboard' },
          { label: t('notifications.adminBreadcrumb'), to: '/admin/notifications' },
        ]}
      />

      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-[#18232F]">{t('notifications.adminTitle')}</h1>
          <p className="text-sm text-[#667384]">
            {t('notifications.adminSubtitle')}
          </p>
        </div>

        <div className="flex items-center gap-2 self-start sm:self-auto">
          {/* Unread Filter Toggle */}
          <div className="flex items-center bg-slate-100 p-1 rounded-xl border border-slate-200 text-xs font-semibold">
            <button
              type="button"
              onClick={() => setUnreadOnly(false)}
              className={`px-3 py-1.5 rounded-lg transition-colors ${
                !unreadOnly
                  ? 'bg-[#137A70] text-white shadow-sm'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              {t('notifications.all')}
            </button>
            <button
              type="button"
              onClick={() => setUnreadOnly(true)}
              className={`px-3 py-1.5 rounded-lg transition-colors flex items-center gap-1.5 ${
                unreadOnly
                  ? 'bg-[#137A70] text-white shadow-sm'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <span>{t('notifications.unread')}</span>
              {unreadCount > 0 && (
                <span className="px-1.5 py-0.2 rounded-full bg-red-600 text-white text-[10px] font-bold">
                  {unreadCount}
                </span>
              )}
            </button>
          </div>

          {unreadCount > 0 && (
            <button
              type="button"
              onClick={handleAcknowledgeAll}
              className="px-4 py-2 text-sm font-semibold text-[#137A70] bg-[#E8F4F1] hover:bg-[#137A70] hover:text-white rounded-lg transition-colors shadow-sm self-start"
            >
              {t('notifications.acknowledgeAll')} ({unreadCount})
            </button>
          )}
        </div>
      </div>

      <div className="bg-white rounded-xl border border-[#E2E7ED] shadow-sm divide-y divide-[#E2E7ED] overflow-hidden">
        {notificationsLoading && notifications.length === 0 ? (
          <div className="p-12 text-center space-y-2">
            <span className="material-symbols-outlined animate-spin text-[#137A70] text-3xl">sync</span>
            <p className="text-sm font-semibold text-slate-800">{t('notifications.loadingAlerts')}</p>
            <p className="text-xs text-slate-500">{t('notifications.connectingEngine')}</p>
          </div>
        ) : notifications.length === 0 ? (
          <div className="p-12 text-center space-y-2">
            <span className="material-symbols-outlined text-slate-400 text-4xl">check_circle</span>
            <p className="text-base font-semibold text-slate-800">{t('notifications.allSystemClear')}</p>
            <p className="text-sm text-slate-500">
              {unreadOnly ? t('notifications.allMarkedRead') : t('notifications.noSystemAlertsDesc')}
            </p>
          </div>
        ) : (
          notifications.map((item) => {
            const translatedNotif = translateNotificationItem(item, t);
            return (
              <div
                key={item.id}
                className={`p-5 flex items-start justify-between gap-4 transition-colors ${
                  !item.read ? 'bg-amber-50/40' : 'hover:bg-slate-50/60'
                }`}
              >
                <div className="flex items-start gap-4 min-w-0 flex-1">
                  <div className="w-10 h-10 rounded-xl bg-purple-100 text-purple-700 flex items-center justify-center shrink-0">
                    <span className="material-symbols-outlined text-xl">admin_panel_settings</span>
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <h3 className="font-semibold text-sm text-[#18232F]">{translatedNotif.title}</h3>
                      {!item.read && (
                        <span className="px-2 py-0.5 rounded-full bg-red-100 text-red-700 text-[10px] font-bold">
                          {t('notifications.actionRequired')}
                        </span>
                      )}
                    </div>
                    <p className="text-sm text-[#667384] mt-1">{translatedNotif.message}</p>
                    <span className="text-xs text-[#667384] block font-mono mt-1">{item.time}</span>
                  </div>
                </div>

                <div className="shrink-0 flex items-center gap-2">
                  {!item.read && (
                    <button
                      type="button"
                      onClick={() => markNotificationRead(item.id)}
                      className="px-3 py-1.5 text-xs font-semibold text-[#137A70] bg-[#E8F4F1] hover:bg-[#137A70] hover:text-white rounded-lg transition-colors"
                    >
                      {t('notifications.markRead')}
                    </button>
                  )}
                </div>
              </div>
            );
          })
        )}

        {/* Pagination Bar */}
        {notificationsPagination.totalPages > 1 && (
          <div className="p-4 bg-slate-50 flex items-center justify-between border-t border-[#E2E7ED]">
            <button
              type="button"
              disabled={notificationsPagination.page === 0}
              onClick={() => handlePageChange(notificationsPagination.page - 1)}
              className="px-3 py-1.5 rounded-lg bg-white border border-[#E2E7ED] hover:bg-slate-100 disabled:opacity-40 disabled:pointer-events-none text-xs font-semibold text-slate-700 transition-colors flex items-center gap-1"
            >
              <span className="material-symbols-outlined text-sm">chevron_left</span>
              <span>{t('common.previous')}</span>
            </button>

            <span className="text-xs text-slate-600 font-medium">
              {t('common.pageOf', { page: notificationsPagination.page + 1, total: notificationsPagination.totalPages })}
            </span>

            <button
              type="button"
              disabled={notificationsPagination.last || notificationsPagination.page >= notificationsPagination.totalPages - 1}
              onClick={() => handlePageChange(notificationsPagination.page + 1)}
              className="px-3 py-1.5 rounded-lg bg-white border border-[#E2E7ED] hover:bg-slate-100 disabled:opacity-40 disabled:pointer-events-none text-xs font-semibold text-slate-700 transition-colors flex items-center gap-1"
            >
              <span>{t('common.next')}</span>
              <span className="material-symbols-outlined text-sm">chevron_right</span>
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default AdminNotificationsPage;
