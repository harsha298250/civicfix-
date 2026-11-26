import React, { useState, useEffect, useCallback } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useTranslation } from '../../context/LanguageContext';
import { officerService } from '../../services/officerService.js';
import { StatusBadge } from '../../components/common/StatusBadge';
import { PriorityBadge } from '../../components/common/PriorityBadge';
import { Breadcrumb } from '../../components/common/Breadcrumb';
import EmptyState from '../../components/common/EmptyState';
import SkeletonLoader from '../../components/common/SkeletonLoader';
import ModalDialog from '../../components/common/ModalDialog';
import { COMPLAINT_STATUS } from '../../constants/statusConstants';

export const ComplaintQueuePage = () => {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const queryFromUrl = searchParams.get('q') || '';

  const [complaints, setComplaints] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [isUnauthorized, setIsUnauthorized] = useState(false);
  const [searchTerm, setSearchTerm] = useState(queryFromUrl);
  const [statusFilter, setStatusFilter] = useState('ALL');

  // Bulk Selection State
  const [selectedIds, setSelectedIds] = useState(new Set());
  const [bulkModalOpen, setBulkModalOpen] = useState(false);
  const [availableWorkers, setAvailableWorkers] = useState([]);
  const [selectedWorkerId, setSelectedWorkerId] = useState('');
  const [bulkNotes, setBulkNotes] = useState('');
  const [bulkSubmitting, setBulkSubmitting] = useState(false);
  const [bulkFeedback, setBulkFeedback] = useState(null);

  useEffect(() => {
    officerService.getFieldWorkers().then(workers => {
      setAvailableWorkers(workers);
      if (workers.length > 0) setSelectedWorkerId(workers[0].id);
    }).catch(err => console.error('Failed to load eligible field technicians:', err));
  }, []);

  const fetchQueue = useCallback(async () => {
    setLoading(true);
    setError(null);
    setIsUnauthorized(false);
    try {
      const data = await officerService.getQueue();
      setComplaints(data);
    } catch (err) {
      console.error('Failed to fetch officer complaint queue:', err);
      if (err.status === 401 || err.status === 403) {
        setIsUnauthorized(true);
      }
      setError(err.message || 'Unable to connect to municipal complaint queue.');
      setComplaints([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchQueue();
  }, [fetchQueue]);

  const filtered = complaints.filter(c => {
    const term = searchTerm.toLowerCase();
    const matchesSearch =
      (c.title && c.title.toLowerCase().includes(term)) ||
      (c.id && c.id.toLowerCase().includes(term)) ||
      (c.referenceId && c.referenceId.toLowerCase().includes(term)) ||
      (c.location?.address && c.location.address.toLowerCase().includes(term)) ||
      (c.category && c.category.toLowerCase().includes(term));

    const matchesStatus =
      statusFilter === 'ALL' ? (c.status !== COMPLAINT_STATUS.REJECTED && c.status !== 'CLOSED') :
      statusFilter === 'AWAITING' ? (c.status === COMPLAINT_STATUS.SUBMITTED || c.status === COMPLAINT_STATUS.UNDER_REVIEW) :
      statusFilter === 'ASSIGNED' ? c.status === COMPLAINT_STATUS.ASSIGNED :
      statusFilter === 'IN_PROGRESS' ? c.status === COMPLAINT_STATUS.IN_PROGRESS :
      statusFilter === 'VERIFICATION' ? c.status === COMPLAINT_STATUS.RESOLUTION_SUBMITTED :
      statusFilter === 'REOPENED' ? c.status === COMPLAINT_STATUS.REOPENED :
      statusFilter === 'PENDING_CITIZEN_CONFIRMATION' ? c.status === COMPLAINT_STATUS.PENDING_CITIZEN_CONFIRMATION :
      c.status === statusFilter;

    return matchesSearch && matchesStatus;
  });

  const assignableFiltered = filtered.filter(
    c => c.status !== 'RESOLVED' && c.status !== 'REJECTED' && c.status !== 'CLOSED' && c.status !== 'PENDING_CITIZEN_CONFIRMATION'
  );

  const isAllSelected = assignableFiltered.length > 0 && assignableFiltered.every(c => selectedIds.has(c.id));

  const toggleSelect = (id) => {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const handleSelectAll = () => {
    if (isAllSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(assignableFiltered.map(c => c.id)));
    }
  };

  const clearSelection = () => {
    setSelectedIds(new Set());
  };

  const handleBulkAssignSubmit = async (e) => {
    e.preventDefault();
    if (selectedIds.size === 0 || !selectedWorkerId) return;

    setBulkSubmitting(true);
    try {
      const res = await officerService.bulkAssignWorkers(
        Array.from(selectedIds),
        selectedWorkerId,
        bulkNotes.trim()
      );
      setBulkFeedback({
        type: res.failureCount === 0 ? 'success' : 'warning',
        message: `Batch dispatch completed: ${res.successCount} dispatched successfully, ${res.failureCount} failed.`,
        results: res.results || []
      });
      setSelectedIds(new Set());
      setBulkModalOpen(false);
      setBulkNotes('');
      fetchQueue();
    } catch (err) {
      console.error('Bulk assignment failed:', err);
      setBulkFeedback({
        type: 'error',
        message: err.message || 'Bulk assignment failed. Please verify permissions and try again.'
      });
    } finally {
      setBulkSubmitting(false);
    }
  };

  return (
    <div className="w-full flex flex-col gap-6">
      <Breadcrumb
        items={[
          { label: 'Operations Command', path: '/officer/dashboard', icon: 'account_balance' },
          { label: 'Complaint Queue' }
        ]}
      />

      {/* Header Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <h1 className="font-headline-lg text-2xl sm:text-3xl font-extrabold text-on-surface tracking-tight">
              {t('officer.queueTitle')}
            </h1>
            <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-emerald-500/10 text-emerald-700 border border-emerald-500/20">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-600 animate-pulse"></span>
              Live Backend
            </span>
          </div>
          <p className="text-body-md text-sm text-on-surface-variant mt-1">
            {t('officer.queueSubtitle')}
          </p>
        </div>

        <div className="flex items-center gap-2.5">
          <button
            type="button"
            onClick={fetchQueue}
            disabled={loading}
            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-surface-container-high hover:bg-surface-container text-on-surface text-xs font-semibold border border-outline-variant/30 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            title="Refresh from PostgreSQL backend"
          >
            <span className={`material-symbols-outlined text-[16px] ${loading ? 'animate-spin' : ''}`}>
              refresh
            </span>
            <span>Refresh</span>
          </button>

          <Link
            to="/officer/verifications"
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-secondary text-white text-xs font-semibold shadow-sm hover:bg-secondary-container transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <span className="material-symbols-outlined text-[18px]">verified</span>
            <span>{t('nav.resolutionVerification')}</span>
          </Link>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="bg-surface-container-lowest p-4 rounded-2xl shadow-sm border border-outline-variant/30 flex flex-col md:flex-row gap-3 items-center justify-between">
        <div className="relative flex-1 w-full">
          <span className="material-symbols-outlined absolute left-3 top-2.5 text-tertiary text-[18px]">
            search
          </span>
          <input
            type="text"
            placeholder={t('officer.searchPlaceholder')}
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-9 pr-4 py-2 bg-surface-container-low rounded-xl text-xs text-on-surface placeholder:text-outline border border-transparent focus:border-primary-container focus:bg-surface-container-lowest focus:outline-none transition-all"
          />
        </div>

        <div className="flex flex-wrap items-center gap-1.5 w-full md:w-auto">
          {[
            { id: 'ALL', label: t('officer.filterAll') },
            { id: 'AWAITING', label: t('officer.filterAwaitingTriage') },
            { id: 'ASSIGNED', label: t('officer.filterAssigned') },
            { id: 'IN_PROGRESS', label: t('officer.filterInProgress') },
            { id: 'VERIFICATION', label: t('officer.filterVerificationReady') },
            { id: 'PENDING_CITIZEN_CONFIRMATION', label: t('officer.filterAwaitingCitizen') },
            { id: 'REOPENED', label: t('officer.filterReopened') }
          ].map(tab => (
            <button
              key={tab.id}
              onClick={() => setStatusFilter(tab.id)}
              className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
                statusFilter === tab.id
                  ? 'bg-primary-container text-white shadow-sm'
                  : 'bg-surface-container-low text-on-surface hover:bg-surface-container'
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
            <h3 className="font-bold text-base text-on-surface">Officer Authorization Required</h3>
            <p className="text-xs text-on-surface-variant mt-1">
              You must be signed in with municipal officer permissions (ROLE_OFFICER) to access the live triage queue.
            </p>
          </div>
          <Link
            to="/login"
            className="px-4 py-2 rounded-xl bg-primary text-white text-xs font-semibold hover:bg-primary-container shrink-0"
          >
            Sign In as Municipal Officer
          </Link>
        </div>
      )}

      {/* General Error State */}
      {!isUnauthorized && error && (
        <div className="p-6 bg-amber-500/10 border border-amber-500/30 rounded-2xl flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <span className="material-symbols-outlined text-amber-600 text-[28px]">warning</span>
            <div>
              <h3 className="font-bold text-sm text-on-surface">Backend Queue Unavailable</h3>
              <p className="text-xs text-on-surface-variant mt-0.5">{error}</p>
            </div>
          </div>
          <button
            type="button"
            onClick={fetchQueue}
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

      {/* Bulk Operation Feedback Alert */}
      {bulkFeedback && (
        <div
          role="alert"
          aria-live="assertive"
          className={`p-4 rounded-2xl border flex items-start justify-between gap-3 ${
            bulkFeedback.type === 'success'
              ? 'bg-emerald-50 text-emerald-800 border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-800'
              : 'bg-amber-50 text-amber-800 border-amber-200 dark:bg-amber-950/40 dark:text-amber-300 dark:border-amber-800'
          }`}
        >
          <div className="flex items-start gap-2.5">
            <span className="material-symbols-outlined text-[20px] shrink-0 mt-0.5">
              {bulkFeedback.type === 'success' ? 'check_circle' : 'warning'}
            </span>
            <div>
              <p className="text-xs font-bold">{bulkFeedback.message}</p>
              {bulkFeedback.results && bulkFeedback.results.length > 0 && (
                <ul className="mt-1.5 space-y-1 text-[11px] opacity-90 max-h-32 overflow-y-auto">
                  {bulkFeedback.results.map((r, idx) => (
                    <li key={idx} className="flex items-center gap-1.5">
                      <span className={r.success ? 'text-emerald-700 dark:text-emerald-400 font-bold' : 'text-red-700 dark:text-red-400 font-bold'}>
                        {r.success ? '✔' : '✖'}
                      </span>
                      <span>#{r.referenceId || r.complaintId}: {r.message}</span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
          <button
            type="button"
            onClick={() => setBulkFeedback(null)}
            className="text-xs opacity-60 hover:opacity-100"
            aria-label="Dismiss feedback"
          >
            ✕
          </button>
        </div>
      )}

      {/* Select All & Multi-select Header Bar */}
      {!loading && !error && assignableFiltered.length > 0 && (
        <div className="flex items-center justify-between px-4 py-2.5 bg-surface-container-low rounded-xl text-xs text-on-surface-variant border border-outline-variant/20">
          <label className="flex items-center gap-2.5 cursor-pointer font-semibold select-none">
            <input
              type="checkbox"
              checked={isAllSelected}
              onChange={handleSelectAll}
              className="w-4 h-4 rounded text-primary-container focus:ring-primary focus:ring-2"
              aria-label={t('officer.selectAll')}
            />
            <span>{t('officer.selectAll')} ({assignableFiltered.length} assignable)</span>
          </label>

          {selectedIds.size > 0 && (
            <div className="flex items-center gap-3">
              <span className="font-bold text-primary">
                {t('officer.selectedCount', { count: selectedIds.size })}
              </span>
              <button
                type="button"
                onClick={clearSelection}
                className="text-tertiary hover:text-on-surface underline font-medium"
              >
                {t('officer.clearSelection')}
              </button>
            </div>
          )}
        </div>
      )}

      {/* Queue Items */}
      {!loading && !error && (
        <div className="space-y-3">
          {filtered.length === 0 ? (
            <EmptyState
              icon={searchTerm || statusFilter !== 'ALL' ? 'filter_list_off' : 'inbox'}
              title={searchTerm || statusFilter !== 'ALL' ? 'No Matching Complaints' : t('officer.noComplaintsQueue')}
              description={
                searchTerm || statusFilter !== 'ALL'
                  ? 'No complaints match the current filter and search criteria.'
                  : t('officer.noComplaintsQueueDesc')
              }
              actionText={searchTerm || statusFilter !== 'ALL' ? 'Clear Filters' : undefined}
              onAction={() => { setSearchTerm(''); setStatusFilter('ALL'); }}
            />
          ) : (
            filtered.map(c => {
              const isAssignable = c.status !== 'RESOLVED' && c.status !== 'REJECTED' && c.status !== 'CLOSED' && c.status !== 'PENDING_CITIZEN_CONFIRMATION';
              const isSelected = selectedIds.has(c.id);

              return (
                <div
                  key={c.id || c.referenceId}
                  className={`bg-surface-container-lowest p-5 rounded-2xl shadow-sm border transition-all flex flex-col md:flex-row md:items-center justify-between gap-4 group ${
                    isSelected ? 'border-primary ring-1 ring-primary bg-primary/5' : 'border-outline-variant/30 hover:border-primary-container/40'
                  }`}
                >
                  <div className="flex items-start gap-3 flex-1 min-w-0">
                    {/* Checkbox for Bulk Selection */}
                    <div className="pt-1">
                      <input
                        type="checkbox"
                        checked={isSelected}
                        onChange={() => toggleSelect(c.id)}
                        disabled={!isAssignable}
                        className="w-4 h-4 rounded text-primary-container focus:ring-primary focus:ring-2 disabled:opacity-30 disabled:cursor-not-allowed"
                        aria-label={`Select defect #${c.referenceId || c.id}`}
                      />
                    </div>

                    <div className="space-y-1.5 min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-code text-xs font-bold bg-surface-container text-on-surface px-2.5 py-0.5 rounded">
                          #{c.referenceId || c.id}
                        </span>
                        <StatusBadge status={c.status} size="sm" />
                        <PriorityBadge priority={c.priority} />
                        {c.aiTriage ? (
                          <span className="text-[10px] bg-secondary-fixed text-on-secondary-fixed font-bold px-2 py-0.5 rounded-full">
                            Rule-match score: {c.aiTriage.confidence != null ? `${c.aiTriage.confidence}%` : 'Not available'}
                          </span>
                        ) : (
                          <span className="text-[10px] bg-surface-container text-tertiary font-bold px-2 py-0.5 rounded-full">
                            Triage pending
                          </span>
                        )}
                        <span className="text-[11px] text-tertiary flex items-center gap-1">
                          <span className="material-symbols-outlined text-[13px]">calendar_today</span>
                          <span>{new Date(c.createdAt).toLocaleDateString()}</span>
                        </span>
                      </div>

                      <h3 className="font-title text-base font-bold text-on-surface group-hover:text-primary transition-colors">
                        {c.title}
                      </h3>

                      <div className="flex items-center gap-2 text-xs text-on-surface-variant flex-wrap">
                        <span className="material-symbols-outlined text-[14px] text-tertiary">location_on</span>
                        <span>{c.location?.address || c.address}</span>
                        <span>•</span>
                        <span className="text-primary-container font-semibold">{c.category}</span>
                        {c.assignedWorker && (
                          <>
                            <span>•</span>
                            <span className="text-secondary font-medium">Assigned: {c.assignedWorker.name}</span>
                          </>
                        )}
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center gap-2 shrink-0 self-end md:self-center">
                    {c.status === 'RESOLUTION_SUBMITTED' ? (
                      <Link
                        to={`/officer/verifications/${c.id}`}
                        className="px-4 py-2 rounded-xl bg-secondary hover:bg-secondary-container text-white text-xs font-semibold transition-colors flex items-center gap-1.5 shadow-sm"
                      >
                        <span className="material-symbols-outlined text-[16px]">verified</span>
                        <span>Verify Evidence</span>
                      </Link>
                    ) : c.assignedWorker ? (
                      <Link
                        to={`/officer/complaints/${c.id}/review`}
                        className="px-4 py-2 rounded-xl bg-surface-container-high hover:bg-primary-container hover:text-white text-on-surface text-xs font-semibold transition-colors flex items-center gap-1.5 border border-outline-variant/30"
                      >
                        <span className="material-symbols-outlined text-[16px]">assignment_turned_in</span>
                        <span>Manage Work Order</span>
                      </Link>
                    ) : (
                      <Link
                        to={`/officer/complaints/${c.id}/review`}
                        className="px-4 py-2 rounded-xl bg-primary-container hover:bg-primary text-white text-xs font-semibold transition-colors flex items-center gap-1.5 shadow-sm"
                      >
                        <span>Review & Dispatch</span>
                        <span className="material-symbols-outlined text-[16px]">arrow_forward</span>
                      </Link>
                    )}
                  </div>
                </div>
              );
            })
          )}
        </div>
      )}

      {/* Floating Bottom Toolbar for Bulk Actions */}
      {selectedIds.size > 0 && (
        <div className="fixed bottom-6 left-1/2 -translate-x-1/2 z-40 bg-surface-container-highest text-on-surface px-6 py-3.5 rounded-2xl shadow-2xl border border-outline-variant/40 flex items-center gap-4 animate-fade-in backdrop-blur-md">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-primary text-[22px]">checklist</span>
            <span className="text-xs font-bold">
              {t('officer.selectedCount', { count: selectedIds.size })}
            </span>
          </div>

          <div className="h-4 w-px bg-outline-variant/40" />

          <button
            type="button"
            onClick={() => setBulkModalOpen(true)}
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-primary text-white text-xs font-bold hover:bg-primary-container transition-all shadow-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <span className="material-symbols-outlined text-[16px]">send_and_archive</span>
            <span>{t('officer.assignToCrew')}</span>
          </button>

          <button
            type="button"
            onClick={clearSelection}
            className="text-xs font-semibold text-tertiary hover:text-on-surface transition-colors"
          >
            {t('officer.clearSelection')}
          </button>
        </div>
      )}

      {/* Bulk Assignment Modal */}
      <ModalDialog
        isOpen={bulkModalOpen}
        onClose={() => setBulkModalOpen(false)}
        title={t('officer.bulkAssignModalTitle')}
        maxWidth="max-w-lg"
      >
        <div className="p-6 space-y-4">
          <p className="text-xs text-on-surface-variant">
            {t('officer.bulkAssignDesc')}
          </p>

          <div className="p-3 bg-primary/10 rounded-xl flex items-center justify-between text-xs font-semibold text-primary">
            <span>Work Orders Selected:</span>
            <span className="font-bold font-code text-sm">{selectedIds.size}</span>
          </div>

          <form onSubmit={handleBulkAssignSubmit} className="space-y-4">
            <div>
              <label htmlFor="bulk-worker-select" className="block text-xs font-bold uppercase tracking-wider text-on-surface-variant mb-1.5">
                {t('officer.selectCrewLead')}
              </label>
              <select
                id="bulk-worker-select"
                value={selectedWorkerId}
                onChange={(e) => setSelectedWorkerId(e.target.value)}
                className="w-full bg-surface border border-outline/20 rounded-xl px-3 py-2.5 text-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-primary"
                required
              >
                {availableWorkers.map(w => (
                  <option key={w.id} value={w.id}>
                    {w.name || w.fullName} — {w.department || w.unit || 'Field Crew'}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label htmlFor="bulk-notes-input" className="block text-xs font-bold uppercase tracking-wider text-on-surface-variant mb-1.5">
                {t('officer.dispatchNotes')}
              </label>
              <textarea
                id="bulk-notes-input"
                value={bulkNotes}
                onChange={(e) => setBulkNotes(e.target.value)}
                placeholder={t('officer.dispatchNotesPlaceholder')}
                rows={3}
                maxLength={500}
                className="w-full bg-surface border border-outline/20 rounded-xl p-3 text-sm text-on-surface placeholder:text-on-surface-variant/50 focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>

            <div className="flex items-center justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={() => setBulkModalOpen(false)}
                disabled={bulkSubmitting}
                className="px-4 py-2 rounded-xl text-sm font-semibold border border-outline/20 text-on-surface hover:bg-surface-container focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={bulkSubmitting || selectedIds.size === 0}
                className="inline-flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold bg-primary text-white hover:bg-primary-container shadow-md disabled:opacity-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-primary"
              >
                {bulkSubmitting && (
                  <span className="material-symbols-outlined text-[16px] animate-spin">progress_activity</span>
                )}
                <span>{t('officer.confirmBulkDispatch')}</span>
              </button>
            </div>
          </form>
        </div>
      </ModalDialog>
    </div>
  );
};

export default ComplaintQueuePage;
