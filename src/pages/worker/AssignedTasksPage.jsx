import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { useApp } from '../../context/AppContext';
import { useTranslation } from '../../context/LanguageContext';
import { workerService } from '../../services/workerService.js';
import { StatusBadge } from '../../components/common/StatusBadge';
import { PriorityBadge } from '../../components/common/PriorityBadge';
import { Breadcrumb } from '../../components/common/Breadcrumb';
import EmptyState from '../../components/common/EmptyState';
import SkeletonLoader from '../../components/common/SkeletonLoader';
import { COMPLAINT_STATUS } from '../../constants/statusConstants';

export const AssignedTasksPage = () => {
  const { currentUser, showToast } = useApp();
  const { t } = useTranslation();
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [isUnauthorized, setIsUnauthorized] = useState(false);
  const [filter, setFilter] = useState('ALL');
  const [searchTerm, setSearchTerm] = useState('');

  // Offline outbox state
  const [offlineOutboxCount, setOfflineOutboxCount] = useState(0);
  const [isSyncingOutbox, setIsSyncingOutbox] = useState(false);

  const fetchTasks = useCallback(async () => {
    setLoading(true);
    setError(null);
    setIsUnauthorized(false);
    try {
      const data = await workerService.getAssignedTasks();
      setTasks(data);
    } catch (err) {
      console.error('Failed to fetch assigned field worker tasks:', err);
      if (err.status === 401 || err.status === 403) {
        setIsUnauthorized(true);
      }
      setError(err.message || 'Unable to connect to field worker dispatch queue.');
      setTasks([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchTasks();
  }, [fetchTasks]);

  const refreshOutboxCount = useCallback(async () => {
    try {
      const count = await workerService.getOfflineOutboxCount();
      setOfflineOutboxCount(count);
    } catch (e) {
      // ignore
    }
  }, []);

  useEffect(() => {
    refreshOutboxCount();
    const cleanup = workerService.initOfflineSyncListener(async (res) => {
      await refreshOutboxCount();
      if (res?.syncedCount > 0) {
        showToast(t('worker.offlineSyncSuccess', { count: res.syncedCount }), 'success');
        fetchTasks();
      }
    });
    return cleanup;
  }, [refreshOutboxCount, fetchTasks, showToast, t]);

  const handleManualSync = async () => {
    setIsSyncingOutbox(true);
    try {
      const res = await workerService.syncOfflineEvidence();
      await refreshOutboxCount();
      if (res.syncedCount > 0) {
        showToast(t('worker.offlineSyncSuccess', { count: res.syncedCount }), 'success');
        fetchTasks();
      } else if (res.total === 0) {
        showToast(t('worker.offlineSyncNone'), 'info');
      } else {
        showToast(t('worker.offlineSyncPartial', { success: res.syncedCount, failed: res.failedCount }), 'error');
      }
    } catch (err) {
      showToast(err.message || 'Offline sync failed.', 'error');
    } finally {
      setIsSyncingOutbox(false);
    }
  };

  const filtered = tasks.filter(t => {
    const term = searchTerm.toLowerCase();
    const matchesSearch =
      !term ||
      (t.title && t.title.toLowerCase().includes(term)) ||
      (t.id && t.id.toLowerCase().includes(term)) ||
      (t.referenceId && t.referenceId.toLowerCase().includes(term)) ||
      (t.location?.address && t.location.address.toLowerCase().includes(term)) ||
      (t.category && t.category.toLowerCase().includes(term));

    const matchesStatus =
      filter === 'ALL' ? true :
      filter === 'ASSIGNED' ? t.status === COMPLAINT_STATUS.ASSIGNED :
      filter === 'IN_PROGRESS' ? t.status === COMPLAINT_STATUS.IN_PROGRESS :
      filter === 'RESOLUTION_SUBMITTED' ? t.status === COMPLAINT_STATUS.RESOLUTION_SUBMITTED :
      t.status === filter;

    return matchesSearch && matchesStatus;
  });

  return (
    <div className="w-full flex flex-col gap-6">
      <Breadcrumb
        items={[
          { label: 'Field Ops', path: '/worker/dashboard', icon: 'engineering' },
          { label: 'Assigned Work Orders' }
        ]}
      />

      {/* Header Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <h1 className="font-headline-lg text-2xl sm:text-3xl font-extrabold text-on-surface tracking-tight">
              {t('worker.myTasksTitle')}
            </h1>
            <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-emerald-500/10 text-emerald-700 border border-emerald-500/20">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-600 animate-pulse"></span>
              Live Backend
            </span>
          </div>
          <p className="text-body-md text-sm text-on-surface-variant mt-1">
            Real-time queue of field repair orders assigned to {currentUser?.name || 'Field Technician'} ({currentUser?.department || 'Field Operations'}).
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={fetchTasks}
            disabled={loading}
            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-surface-container-high hover:bg-surface-container text-on-surface text-xs font-semibold border border-outline-variant/30 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            title="Refresh from PostgreSQL backend"
          >
            <span className={`material-symbols-outlined text-[16px] ${loading ? 'animate-spin' : ''}`}>
              refresh
            </span>
            <span>Refresh</span>
          </button>
        </div>
      </div>

      {/* Offline Outbox Alert Banner */}
      {offlineOutboxCount > 0 && (
        <div role="status" className="p-3.5 bg-amber-500/10 border border-amber-500/30 rounded-2xl flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs text-amber-900 dark:text-amber-200">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-amber-600 text-lg" aria-hidden="true">cloud_off</span>
            <span>
              <strong>{t('worker.offlinePendingTitle', { count: offlineOutboxCount })}</strong> — {t('worker.offlinePendingDesc')}
            </span>
          </div>
          <button
            type="button"
            onClick={handleManualSync}
            disabled={isSyncingOutbox}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-amber-600 hover:bg-amber-700 text-white font-semibold rounded-xl text-xs shadow-sm transition-colors disabled:opacity-50 self-start sm:self-auto"
          >
            <span className={`material-symbols-outlined text-sm ${isSyncingOutbox ? 'animate-spin' : ''}`} aria-hidden="true">
              sync
            </span>
            {isSyncingOutbox ? t('worker.offlineSyncing') : t('worker.offlineSyncNow')}
          </button>
        </div>
      )}

      {/* Search and Status Filters */}
      <div className="bg-surface-container-lowest p-4 rounded-2xl shadow-sm border border-outline-variant/30 flex flex-col md:flex-row gap-3 items-center justify-between">
        <div className="relative flex-1 w-full">
          <span className="material-symbols-outlined absolute left-3 top-2.5 text-tertiary text-[18px]">
            search
          </span>
          <input
            type="text"
            placeholder={t('worker.searchTasksPlaceholder')}
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-9 pr-4 py-2 bg-surface-container-low rounded-xl text-xs text-on-surface placeholder:text-outline border border-transparent focus:border-primary-container focus:bg-surface-container-lowest focus:outline-none transition-all"
          />
        </div>

        <div className="flex flex-wrap items-center gap-1.5 bg-surface-container-low p-1 rounded-xl text-xs w-full md:w-auto">
          {[
            { id: 'ALL', label: t('worker.tabAll') },
            { id: 'ASSIGNED', label: t('worker.tabPending') },
            { id: 'IN_PROGRESS', label: t('worker.tabInProgress') },
            { id: 'RESOLUTION_SUBMITTED', label: t('worker.tabCompleted') }
          ].map(tab => (
            <button
              key={tab.id}
              onClick={() => setFilter(tab.id)}
              className={`px-3 py-1.5 rounded-lg font-semibold transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
                filter === tab.id
                  ? 'bg-primary-container text-white shadow-sm'
                  : 'text-tertiary hover:text-on-surface'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {/* Unauthorized State (401/403) */}
      {isUnauthorized && (
        <div className="p-6 bg-error-container/20 border border-error/30 rounded-2xl flex flex-col sm:flex-row items-center gap-4 text-center sm:text-left">
          <div className="w-12 h-12 rounded-xl bg-error/10 text-error flex items-center justify-center shrink-0">
            <span className="material-symbols-outlined text-[28px]">lock</span>
          </div>
          <div className="flex-1 min-w-0">
            <h3 className="font-bold text-base text-on-surface">Field Worker Authorization Required</h3>
            <p className="text-xs text-on-surface-variant mt-1">
              You must be signed in with field technician permissions (ROLE_WORKER) to inspect assigned work orders.
            </p>
          </div>
          <Link
            to="/login"
            className="px-4 py-2 rounded-xl bg-primary text-white text-xs font-semibold hover:bg-primary-container shrink-0"
          >
            Sign In as Field Technician
          </Link>
        </div>
      )}

      {/* General Error State */}
      {!isUnauthorized && error && (
        <div className="p-6 bg-amber-500/10 border border-amber-500/30 rounded-2xl flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <span className="material-symbols-outlined text-amber-600 text-[28px]">warning</span>
            <div>
              <h3 className="font-bold text-sm text-on-surface">Backend Dispatch Queue Unavailable</h3>
              <p className="text-xs text-on-surface-variant mt-0.5">{error}</p>
            </div>
          </div>
          <button
            type="button"
            onClick={fetchTasks}
            className="px-4 py-2 rounded-xl bg-primary-container text-white text-xs font-semibold hover:bg-primary transition-colors shrink-0"
          >
            Retry Connection
          </button>
        </div>
      )}

      {/* Loading Skeleton */}
      {loading && !error && (
        <SkeletonLoader type="list" count={4} />
      )}

      {/* Task Cards List */}
      {!loading && !error && (
        <div className="space-y-3">
          {filtered.length === 0 ? (
            <EmptyState
              icon={searchTerm || filter !== 'ALL' ? 'filter_list_off' : 'assignment_turned_in'}
              title={searchTerm || filter !== 'ALL' ? 'No Matching Work Orders' : t('worker.noTasksTitle')}
              description={
                searchTerm || filter !== 'ALL'
                  ? 'No work orders match the current filter and search criteria.'
                  : t('worker.noTasksDesc')
              }
              actionText={searchTerm || filter !== 'ALL' ? 'Clear Filters' : undefined}
              onAction={() => { setSearchTerm(''); setFilter('ALL'); }}
            />
          ) : (
            filtered.map(t => (
              <div
                key={t.id || t.referenceId}
                className="bg-surface-container-lowest p-5 rounded-2xl shadow-sm border border-outline-variant/30 hover:border-primary-container/40 transition-all flex flex-col md:flex-row md:items-center justify-between gap-4 group"
              >
                <div className="space-y-1.5 min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-code text-xs font-bold bg-surface-container text-on-surface px-2.5 py-0.5 rounded">
                      #{t.referenceId || t.id}
                    </span>
                    <StatusBadge status={t.status} size="sm" />
                    <PriorityBadge priority={t.priority} />
                    <span className="text-[11px] text-tertiary font-code">
                      Target: {t.assignedWorker?.estimatedCompletion || '48h SLA'}
                    </span>
                    <span className="text-[11px] text-tertiary flex items-center gap-1">
                      <span className="material-symbols-outlined text-[13px]">calendar_today</span>
                      <span>{new Date(t.createdAt).toLocaleDateString()}</span>
                    </span>
                  </div>

                  <Link
                    to={`/worker/tasks/${t.id || t.referenceId}`}
                    className="font-title text-base font-bold text-on-surface group-hover:text-primary transition-colors block"
                  >
                    {t.title}
                  </Link>

                  <div className="flex items-center gap-2 text-xs text-on-surface-variant flex-wrap">
                    <span className="material-symbols-outlined text-[14px] text-tertiary">location_on</span>
                    <span>{t.location?.address || t.address}</span>
                    <span>•</span>
                    <span className="text-primary-container font-semibold">{t.category}</span>
                    {t.assignedOfficer?.name && (
                      <>
                        <span>•</span>
                        <span className="text-tertiary">Dispatched by: {t.assignedOfficer.name}</span>
                      </>
                    )}
                  </div>

                  {t.progressPercent > 0 && (
                    <div className="pt-1 flex items-center gap-2">
                      <div className="w-24 bg-surface-container-high rounded-full h-1.5 overflow-hidden">
                        <div className="bg-primary-container h-full rounded-full" style={{ width: `${t.progressPercent}%` }} />
                      </div>
                      <span className="text-[10px] text-tertiary font-code">{t.progressPercent}% completed</span>
                    </div>
                  )}
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  <Link
                    to={`/worker/tasks/${t.id || t.referenceId}`}
                    className="px-3 py-2 rounded-xl bg-surface-container hover:bg-surface-container-high text-xs font-semibold text-on-surface border border-outline-variant/30 flex items-center gap-1"
                  >
                    <span className="material-symbols-outlined text-[15px]">description</span>
                    <span>Details</span>
                  </Link>
                  <Link
                    to={`/worker/tasks/${t.id || t.referenceId}/progress`}
                    className="px-3.5 py-2 rounded-xl bg-surface-container hover:bg-surface-container-high text-xs font-semibold text-on-surface"
                  >
                    Progress Update
                  </Link>
                  <Link
                    to={`/worker/tasks/${t.id || t.referenceId}/evidence`}
                    className="px-3.5 py-2 rounded-xl bg-primary-container hover:bg-primary text-white text-xs font-semibold shadow"
                  >
                    Submit Proof
                  </Link>
                </div>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
};

export default AssignedTasksPage;
