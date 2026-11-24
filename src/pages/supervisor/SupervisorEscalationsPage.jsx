import React, { useState, useEffect, useCallback } from 'react';
import { apiClient } from '../../services/apiClient.js';
import { officerService, VERIFIED_FIELD_WORKERS } from '../../services/officerService.js';
import { SlaBadge } from '../../components/common/SlaBadge.jsx';
import ClosureTrustBadge from '../../components/common/ClosureTrustBadge.jsx';
import ModalDialog from '../../components/common/ModalDialog.jsx';
import { useTranslation } from '../../context/LanguageContext.jsx';

const WARDS = [
  'ALL',
  'Ward 08, Anna Nagar',
  'Ward 10, T. Nagar',
  'Ward 13, Adyar',
  'Ward 15, Velachery',
  'Ward 09, Kodambakkam',
  'Ward 04, Tondiarpet'
];

const CATEGORIES = [
  'ALL',
  'Pothole & Road Hazard',
  'Water Supply & Leakage',
  'Garbage & Sanitation',
  'Street Lighting',
  'Drainage & Sewage',
  'Encroachment & Illegal Parking',
  'Public Health & Mosquitoes'
];

export const SupervisorEscalationsPage = () => {
  const { t } = useTranslation();
  const [escalations, setEscalations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [actionSuccess, setActionSuccess] = useState(null);

  // Filters & Pagination
  const [selectedWard, setSelectedWard] = useState('ALL');
  const [selectedCategory, setSelectedCategory] = useState('ALL');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  // Closure Trust Score Analytics
  const [trustScores, setTrustScores] = useState(null);
  const [showTrustDetails, setShowTrustDetails] = useState(false);

  // Reassignment Modal State
  const [reassignModalOpen, setReassignModalOpen] = useState(false);
  const [targetComplaint, setTargetComplaint] = useState(null);
  const [availableWorkers, setAvailableWorkers] = useState([]);
  const [selectedWorkerId, setSelectedWorkerId] = useState('');
  const [reassignReason, setReassignReason] = useState('');
  const [reassignSubmitting, setReassignSubmitting] = useState(false);
  const [reassignError, setReassignError] = useState(null);

  // Extend SLA Modal State
  const [extendModalOpen, setExtendModalOpen] = useState(false);
  const [extendHours, setExtendHours] = useState(24);
  const [extendReason, setExtendReason] = useState('');
  const [extendSubmitting, setExtendSubmitting] = useState(false);
  const [extendError, setExtendError] = useState(null);

  // De-escalate Modal State
  const [deEscalateModalOpen, setDeEscalateModalOpen] = useState(false);
  const [deEscalateReason, setDeEscalateReason] = useState('');
  const [deEscalateGraceHours, setDeEscalateGraceHours] = useState(24);
  const [deEscalateSubmitting, setDeEscalateSubmitting] = useState(false);
  const [deEscalateError, setDeEscalateError] = useState(null);

  // Fetch escalated complaints and closure trust analytics
  const fetchEscalations = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [res, trustData] = await Promise.all([
        apiClient.supervisor.getEscalations({
          ward: selectedWard !== 'ALL' ? selectedWard : undefined,
          category: selectedCategory !== 'ALL' ? selectedCategory : undefined,
          page,
          size: 10
        }),
        officerService.getClosureTrustScores({
          ward: selectedWard !== 'ALL' ? selectedWard : undefined
        }).catch(err => {
          console.warn('Could not load supervisor closure trust scores:', err);
          return null;
        })
      ]);
      const data = res?.data || res;
      setEscalations(data.content || []);
      setTotalPages(data.totalPages || 1);
      setTotalElements(data.totalElements || 0);
      if (trustData) setTrustScores(trustData);
    } catch (err) {
      console.error('Failed to fetch escalations:', err);
      setError(err.message || 'Failed to retrieve escalated complaints.');
    } finally {
      setLoading(false);
    }
  }, [selectedWard, selectedCategory, page]);

  useEffect(() => {
    fetchEscalations();
  }, [fetchEscalations]);

  // Fetch available workers for reassignment
  useEffect(() => {
    const loadWorkers = async () => {
      try {
        const res = await apiClient.supervisor.getWorkers();
        const workers = res?.data || res;
        if (Array.isArray(workers) && workers.length > 0) {
          setAvailableWorkers(workers);
        } else {
          setAvailableWorkers(VERIFIED_FIELD_WORKERS);
        }
      } catch (err) {
        console.warn('Could not fetch dynamic workers from backend; using seed list:', err);
        setAvailableWorkers(VERIFIED_FIELD_WORKERS);
      }
    };
    loadWorkers();
  }, []);

  const openReassignModal = (complaint) => {
    setTargetComplaint(complaint);
    setReassignReason('');
    setReassignError(null);

    // Filter out currently assigned worker from default selection
    const currentWorkerId = complaint.assignedWorker?.id;
    const candidate = availableWorkers.find(w => w.id !== currentWorkerId);
    setSelectedWorkerId(candidate ? candidate.id : (availableWorkers[0]?.id || ''));

    setReassignModalOpen(true);
  };

  const closeReassignModal = () => {
    setReassignModalOpen(false);
    setTargetComplaint(null);
    setReassignReason('');
    setReassignError(null);
  };

  const handleReassignSubmit = async (e) => {
    e.preventDefault();
    if (!reassignReason.trim() || reassignReason.trim().length < 5) {
      setReassignError('A valid operational reason of at least 5 characters is required.');
      return;
    }
    if (!selectedWorkerId) {
      setReassignError('Please select a technician to reassign.');
      return;
    }

    setReassignSubmitting(true);
    setReassignError(null);
    try {
      await apiClient.supervisor.reassignWorker(targetComplaint.id, {
        newWorkerId: selectedWorkerId,
        reason: reassignReason.trim(),
        version: targetComplaint.version
      });

      const assignedWorkerObj = availableWorkers.find(w => w.id === selectedWorkerId);
      const workerName = assignedWorkerObj?.fullName || assignedWorkerObj?.name || 'Selected Technician';

      setActionSuccess(`Complaint #${targetComplaint.referenceId} was successfully reassigned to ${workerName}.`);
      closeReassignModal();
      fetchEscalations();
      setTimeout(() => setActionSuccess(null), 6000);
    } catch (err) {
      console.error('Reassignment failed:', err);
      setReassignError(err.message || 'Worker reassignment failed. Please check record version and try again.');
    } finally {
      setReassignSubmitting(false);
    }
  };

  const openExtendModal = (complaint) => {
    setTargetComplaint(complaint);
    setExtendHours(24);
    setExtendReason('');
    setExtendError(null);
    setExtendModalOpen(true);
  };

  const closeExtendModal = () => {
    setExtendModalOpen(false);
    setTargetComplaint(null);
    setExtendReason('');
    setExtendError(null);
  };

  const handleExtendSubmit = async (e) => {
    e.preventDefault();
    if (!extendReason.trim() || extendReason.trim().length < 5) {
      setExtendError('A valid operational reason of at least 5 characters is required.');
      return;
    }
    const hoursNum = parseInt(extendHours, 10);
    if (isNaN(hoursNum) || hoursNum < 1 || hoursNum > 720) {
      setExtendError('Additional hours must be between 1 and 720 (30 days).');
      return;
    }

    setExtendSubmitting(true);
    setExtendError(null);
    try {
      await apiClient.supervisor.extendSla(targetComplaint.id, {
        additionalHours: hoursNum,
        reason: extendReason.trim(),
        version: targetComplaint.version
      });

      setActionSuccess(`SLA deadline for complaint #${targetComplaint.referenceId} was extended by ${hoursNum} hours.`);
      closeExtendModal();
      fetchEscalations();
      setTimeout(() => setActionSuccess(null), 6000);
    } catch (err) {
      console.error('SLA extension failed:', err);
      setExtendError(err.message || 'SLA extension failed. Please try again.');
    } finally {
      setExtendSubmitting(false);
    }
  };

  const openDeEscalateModal = (complaint) => {
    setTargetComplaint(complaint);
    setDeEscalateReason('');
    setDeEscalateGraceHours(24);
    setDeEscalateError(null);
    setDeEscalateModalOpen(true);
  };

  const closeDeEscalateModal = () => {
    setDeEscalateModalOpen(false);
    setTargetComplaint(null);
    setDeEscalateReason('');
    setDeEscalateError(null);
  };

  const handleDeEscalateSubmit = async (e) => {
    e.preventDefault();
    if (!deEscalateReason.trim() || deEscalateReason.trim().length < 5) {
      setDeEscalateError('A valid explanation of at least 5 characters is required.');
      return;
    }

    setDeEscalateSubmitting(true);
    setDeEscalateError(null);
    try {
      await apiClient.supervisor.deEscalate(targetComplaint.id, {
        reason: deEscalateReason.trim(),
        additionalHours: parseInt(deEscalateGraceHours, 10) || 24,
        version: targetComplaint.version
      });

      setActionSuccess(`Complaint #${targetComplaint.referenceId} was successfully de-escalated.`);
      closeDeEscalateModal();
      fetchEscalations();
      setTimeout(() => setActionSuccess(null), 6000);
    } catch (err) {
      console.error('De-escalation failed:', err);
      setDeEscalateError(err.message || 'De-escalation failed. Please try again.');
    } finally {
      setDeEscalateSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-outline/10 pb-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-red-500 text-[28px]">crisis_alert</span>
            <h2 className="text-2xl font-bold font-title text-on-surface">Escalations Queue</h2>
            <span className="ml-2 px-2.5 py-0.5 rounded-full text-xs font-bold bg-red-100 text-red-800 dark:bg-red-950/60 dark:text-red-300">
              {totalElements} Active
            </span>
          </div>
          <p className="text-sm text-on-surface-variant mt-1">
            Supervisory oversight of SLA-breached reports and manual supervisor interventions.
          </p>
        </div>

        <button
          onClick={fetchEscalations}
          disabled={loading}
          className="inline-flex items-center gap-2 px-3.5 py-2 rounded-xl text-sm font-semibold bg-surface-container hover:bg-surface-container-high border border-outline/20 text-on-surface transition-all shadow-sm self-start sm:self-auto"
        >
          <span className={`material-symbols-outlined text-[18px] ${loading ? 'animate-spin' : ''}`}>refresh</span>
          <span>Refresh</span>
        </button>
      </div>

      {/* Success Notification Banner */}
      {actionSuccess && (
        <div className="p-4 rounded-xl bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-800 text-emerald-800 dark:text-emerald-300 flex items-center justify-between animate-fade-in shadow-sm">
          <div className="flex items-center gap-3">
            <span className="material-symbols-outlined text-emerald-600">check_circle</span>
            <span className="text-sm font-medium">{actionSuccess}</span>
          </div>
          <button onClick={() => setActionSuccess(null)} className="text-emerald-600 hover:text-emerald-800">
            <span className="material-symbols-outlined text-[18px]">close</span>
          </button>
        </div>
      )}

      {/* Supervisor Closure Trust Scores Telemetry Card */}
      {trustScores?.overall && (
        <div className="bg-surface-container-lowest border border-outline/10 rounded-2xl p-5 shadow-sm space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-primary/10 text-primary flex items-center justify-center">
                <span className="material-symbols-outlined text-[24px]">verified</span>
              </div>
              <div>
                <h3 className="text-base font-bold text-on-surface">Closure Trust Score & Verification Oversight</h3>
                <p className="text-xs text-on-surface-variant">
                  Quantifies citizen verification rates. Score = confirmed / (confirmed + rejected + reopened).
                </p>
              </div>
            </div>

            <div className="flex items-center gap-3 self-start sm:self-auto">
              <ClosureTrustBadge
                score={trustScores.overall.trustScore}
                total={trustScores.overall.totalClosures}
                confirmed={trustScores.overall.confirmedClosures}
                rejected={trustScores.overall.citizenRejections}
                reopened={trustScores.overall.reopenedAfterResolution}
                statusMessage={trustScores.overall.statusMessage}
                size="md"
              />
              <button
                type="button"
                onClick={() => setShowTrustDetails(prev => !prev)}
                className="text-xs font-semibold text-primary hover:underline flex items-center gap-1 px-2.5 py-1 rounded-lg bg-surface-container-low hover:bg-surface-container"
              >
                <span>{showTrustDetails ? 'Hide Ward & Officer Breakdown' : 'View Ward & Officer Breakdown'}</span>
                <span className="material-symbols-outlined text-[16px]">
                  {showTrustDetails ? 'expand_less' : 'expand_more'}
                </span>
              </button>
            </div>
          </div>

          {showTrustDetails && (
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 pt-4 border-t border-outline/10 animate-fade-in text-xs">
              {/* Ward Table */}
              <div className="space-y-2">
                <h4 className="font-semibold text-on-surface flex items-center gap-1.5 text-xs">
                  <span className="material-symbols-outlined text-[16px] text-tertiary">location_city</span>
                  <span>By Municipal Ward</span>
                </h4>
                {(!trustScores.byWard || trustScores.byWard.length === 0) ? (
                  <p className="text-tertiary py-3 text-center bg-surface-container-low rounded-xl">No ward records.</p>
                ) : (
                  <div className="border border-outline/10 rounded-xl overflow-hidden overflow-x-auto">
                    <table className="w-full text-left">
                      <thead className="bg-surface-container-low text-[10px] text-tertiary uppercase font-semibold">
                        <tr>
                          <th className="py-2 px-2.5">Ward</th>
                          <th className="py-2 px-1 text-center" title="Confirmed">Conf</th>
                          <th className="py-2 px-1 text-center" title="Rejected">Rej</th>
                          <th className="py-2 px-1 text-center" title="Reopened">Reop</th>
                          <th className="py-2 px-1 text-center">Total</th>
                          <th className="py-2 px-2.5 text-right">Score</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-outline/10 bg-surface-container-lowest text-[11px]">
                        {trustScores.byWard.map(w => (
                          <tr key={w.ward}>
                            <td className="py-2 px-2.5 font-medium text-on-surface truncate max-w-[130px]">{w.ward}</td>
                            <td className="py-2 px-1 text-center text-emerald-600 font-mono">{w.confirmedClosures}</td>
                            <td className="py-2 px-1 text-center text-rose-600 font-mono">{w.citizenRejections}</td>
                            <td className="py-2 px-1 text-center text-amber-600 font-mono">{w.reopenedAfterResolution}</td>
                            <td className="py-2 px-1 text-center font-mono font-semibold">{w.totalClosures}</td>
                            <td className="py-2 px-2.5 text-right">
                              <ClosureTrustBadge
                                score={w.trustScore}
                                total={w.totalClosures}
                                confirmed={w.confirmedClosures}
                                rejected={w.citizenRejections}
                                reopened={w.reopenedAfterResolution}
                                statusMessage={w.statusMessage}
                                size="sm"
                                showLabel={false}
                              />
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>

              {/* Officer Table */}
              <div className="space-y-2">
                <h4 className="font-semibold text-on-surface flex items-center gap-1.5 text-xs">
                  <span className="material-symbols-outlined text-[16px] text-tertiary">badge</span>
                  <span>By Assigned Officer</span>
                </h4>
                {(!trustScores.byOfficer || trustScores.byOfficer.length === 0) ? (
                  <p className="text-tertiary py-3 text-center bg-surface-container-low rounded-xl">No officer records.</p>
                ) : (
                  <div className="border border-outline/10 rounded-xl overflow-hidden overflow-x-auto">
                    <table className="w-full text-left">
                      <thead className="bg-surface-container-low text-[10px] text-tertiary uppercase font-semibold">
                        <tr>
                          <th className="py-2 px-2.5">Officer</th>
                          <th className="py-2 px-1 text-center" title="Confirmed">Conf</th>
                          <th className="py-2 px-1 text-center" title="Rejected">Rej</th>
                          <th className="py-2 px-1 text-center" title="Reopened">Reop</th>
                          <th className="py-2 px-1 text-center">Total</th>
                          <th className="py-2 px-2.5 text-right">Score</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-outline/10 bg-surface-container-lowest text-[11px]">
                        {trustScores.byOfficer.map(off => (
                          <tr key={off.officerId || off.officerName}>
                            <td className="py-2 px-2.5 font-medium text-on-surface truncate max-w-[130px]" title={off.officerName}>
                              {off.officerName}
                            </td>
                            <td className="py-2 px-1 text-center text-emerald-600 font-mono">{off.confirmedClosures}</td>
                            <td className="py-2 px-1 text-center text-rose-600 font-mono">{off.citizenRejections}</td>
                            <td className="py-2 px-1 text-center text-amber-600 font-mono">{off.reopenedAfterResolution}</td>
                            <td className="py-2 px-1 text-center font-mono font-semibold">{off.totalClosures}</td>
                            <td className="py-2 px-2.5 text-right">
                              <ClosureTrustBadge
                                score={off.trustScore}
                                total={off.totalClosures}
                                confirmed={off.confirmedClosures}
                                rejected={off.citizenRejections}
                                reopened={off.reopenedAfterResolution}
                                statusMessage={off.statusMessage}
                                size="sm"
                                showLabel={false}
                              />
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      )}

      {/* Filter Controls Bar */}
      <div className="bg-surface-container-low p-4 rounded-2xl border border-outline/10 flex flex-wrap items-center gap-4">
        <div className="flex items-center gap-2 min-w-[200px]">
          <span className="text-xs font-bold uppercase tracking-wider text-on-surface-variant">Ward:</span>
          <select
            value={selectedWard}
            onChange={(e) => {
              setSelectedWard(e.target.value);
              setPage(0);
            }}
            className="flex-1 bg-surface border border-outline/20 rounded-xl px-3 py-1.5 text-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-primary"
          >
            {WARDS.map(w => (
              <option key={w} value={w}>{w === 'ALL' ? 'All Municipal Wards' : w}</option>
            ))}
          </select>
        </div>

        <div className="flex items-center gap-2 min-w-[220px]">
          <span className="text-xs font-bold uppercase tracking-wider text-on-surface-variant">Category:</span>
          <select
            value={selectedCategory}
            onChange={(e) => {
              setSelectedCategory(e.target.value);
              setPage(0);
            }}
            className="flex-1 bg-surface border border-outline/20 rounded-xl px-3 py-1.5 text-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-primary"
          >
            {CATEGORIES.map(c => (
              <option key={c} value={c}>{c === 'ALL' ? 'All Categories' : c}</option>
            ))}
          </select>
        </div>

        {(selectedWard !== 'ALL' || selectedCategory !== 'ALL') && (
          <button
            onClick={() => {
              setSelectedWard('ALL');
              setSelectedCategory('ALL');
              setPage(0);
            }}
            className="text-xs text-primary font-semibold hover:underline ml-auto"
          >
            Clear Filters
          </button>
        )}
      </div>

      {/* Main Table Content */}
      <div className="bg-surface-container-lowest border border-outline/10 rounded-2xl shadow-sm overflow-hidden">
        {loading ? (
          <div className="p-12 text-center text-on-surface-variant flex flex-col items-center justify-center">
            <span className="material-symbols-outlined text-[36px] animate-spin text-primary">progress_activity</span>
            <p className="mt-3 text-sm font-medium">Loading escalated complaints from database...</p>
          </div>
        ) : error ? (
          <div className="p-12 text-center text-error flex flex-col items-center justify-center">
            <span className="material-symbols-outlined text-[40px]">error</span>
            <p className="mt-2 text-base font-bold">Failed to load escalated complaints</p>
            <p className="text-sm text-on-surface-variant mt-1">{error}</p>
            <button
              onClick={fetchEscalations}
              className="mt-4 px-4 py-2 rounded-xl bg-primary text-white font-semibold text-sm hover:bg-primary/90"
            >
              Retry
            </button>
          </div>
        ) : escalations.length === 0 ? (
          <div className="p-12 text-center text-on-surface-variant flex flex-col items-center justify-center">
            <div className="w-16 h-16 rounded-full bg-emerald-100 dark:bg-emerald-950/40 flex items-center justify-center text-emerald-600 mb-3">
              <span className="material-symbols-outlined text-[32px]">task_alt</span>
            </div>
            <h3 className="text-base font-bold text-on-surface">No Escalated Complaints</h3>
            <p className="text-sm max-w-md mt-1 text-on-surface-variant">
              There are currently no complaints in breached or manually escalated status for the selected criteria.
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-surface-container-low text-xs uppercase font-bold text-on-surface-variant border-b border-outline/10">
                <tr>
                  <th className="px-4 py-3.5">Reference & Title</th>
                  <th className="px-4 py-3.5">Category & Priority</th>
                  <th className="px-4 py-3.5">Ward / Location</th>
                  <th className="px-4 py-3.5">SLA State</th>
                  <th className="px-4 py-3.5">Assigned Worker</th>
                  <th className="px-4 py-3.5 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline/10">
                {escalations.map((c) => (
                  <tr key={c.id} className="hover:bg-surface-container-low/50 transition-colors">
                    <td className="px-4 py-3.5">
                      <div className="font-code font-bold text-primary">{c.referenceId}</div>
                      <div className="font-semibold text-on-surface line-clamp-1 max-w-[240px]" title={c.title}>
                        {c.title}
                      </div>
                    </td>
                    <td className="px-4 py-3.5">
                      <div className="text-on-surface font-medium">{c.category}</div>
                      <span className={`inline-block text-[11px] font-bold px-2 py-0.5 rounded-full mt-1 ${
                        c.priority === 'CRITICAL' ? 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300' :
                        c.priority === 'HIGH' ? 'bg-orange-100 text-orange-800 dark:bg-orange-950 dark:text-orange-300' :
                        c.priority === 'MEDIUM' ? 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300' :
                        'bg-slate-100 text-slate-800 dark:bg-slate-800 dark:text-slate-300'
                      }`}>
                        {c.priority}
                      </span>
                    </td>
                    <td className="px-4 py-3.5">
                      <div className="text-on-surface font-medium">{c.ward || 'Central Ward'}</div>
                      <div className="text-xs text-on-surface-variant line-clamp-1 max-w-[200px]" title={c.address}>
                        {c.address}
                      </div>
                    </td>
                    <td className="px-4 py-3.5">
                      <SlaBadge
                        slaState={c.slaState}
                        slaDueAt={c.slaDueAt}
                        escalatedAt={c.escalatedAt}
                        showDueTime={true}
                      />
                    </td>
                    <td className="px-4 py-3.5">
                      {c.assignedWorker ? (
                        <div>
                          <div className="font-semibold text-on-surface">{c.assignedWorker.name}</div>
                          <div className="text-xs text-on-surface-variant">{c.assignedWorker.department || 'Field Tech'}</div>
                        </div>
                      ) : (
                        <span className="text-xs italic text-on-surface-variant">Not yet assigned</span>
                      )}
                    </td>
                    <td className="px-4 py-3.5 text-right">
                      <div className="flex items-center justify-end gap-1.5 flex-wrap">
                        <button
                          onClick={() => openReassignModal(c)}
                          disabled={!c.assignedWorker || c.status === 'RESOLVED' || c.status === 'REJECTED' || c.status === 'CLOSED'}
                          className={`inline-flex items-center gap-1 px-2.5 py-1.5 rounded-xl text-xs font-semibold transition-all shadow-xs ${
                            c.assignedWorker && c.status !== 'RESOLVED' && c.status !== 'REJECTED' && c.status !== 'CLOSED'
                              ? 'bg-red-50 text-red-700 hover:bg-red-100 border border-red-200 dark:bg-red-950/40 dark:text-red-300 dark:border-red-800'
                              : 'bg-surface-container text-on-surface-variant opacity-50 cursor-not-allowed'
                          }`}
                          title={!c.assignedWorker ? 'Worker must be initially assigned before reassignment' : t('supervisor.reassignWorker')}
                        >
                          <span className="material-symbols-outlined text-[15px]">swap_horiz</span>
                          <span>{t('supervisor.reassignWorker')}</span>
                        </button>

                        <button
                          onClick={() => openExtendModal(c)}
                          disabled={c.status === 'RESOLVED' || c.status === 'REJECTED' || c.status === 'CLOSED'}
                          className={`inline-flex items-center gap-1 px-2.5 py-1.5 rounded-xl text-xs font-semibold transition-all shadow-xs ${
                            c.status !== 'RESOLVED' && c.status !== 'REJECTED' && c.status !== 'CLOSED'
                              ? 'bg-amber-50 text-amber-800 hover:bg-amber-100 border border-amber-200 dark:bg-amber-950/40 dark:text-amber-300 dark:border-amber-800'
                              : 'bg-surface-container text-on-surface-variant opacity-50 cursor-not-allowed'
                          }`}
                          title={t('supervisor.extendSla')}
                        >
                          <span className="material-symbols-outlined text-[15px]">more_time</span>
                          <span>{t('supervisor.extendSla')}</span>
                        </button>

                        <button
                          onClick={() => openDeEscalateModal(c)}
                          disabled={!c.escalatedAt || c.status === 'RESOLVED' || c.status === 'REJECTED' || c.status === 'CLOSED'}
                          className={`inline-flex items-center gap-1 px-2.5 py-1.5 rounded-xl text-xs font-semibold transition-all shadow-xs ${
                            c.escalatedAt && c.status !== 'RESOLVED' && c.status !== 'REJECTED' && c.status !== 'CLOSED'
                              ? 'bg-emerald-50 text-emerald-800 hover:bg-emerald-100 border border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-800'
                              : 'bg-surface-container text-on-surface-variant opacity-50 cursor-not-allowed'
                          }`}
                          title={t('supervisor.deEscalate')}
                        >
                          <span className="material-symbols-outlined text-[15px]">check_circle</span>
                          <span>{t('supervisor.deEscalate')}</span>
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination Bar */}
        {totalPages > 1 && (
          <div className="px-4 py-3 bg-surface-container-low border-t border-outline/10 flex items-center justify-between text-xs text-on-surface-variant">
            <span>
              Showing page <strong>{page + 1}</strong> of <strong>{totalPages}</strong> ({totalElements} total complaints)
            </span>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
                className="px-3 py-1 rounded-lg border border-outline/20 disabled:opacity-40 hover:bg-surface"
              >
                Previous
              </button>
              <button
                onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                className="px-3 py-1 rounded-lg border border-outline/20 disabled:opacity-40 hover:bg-surface"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Technician Reassignment Modal */}
      {targetComplaint && (
        <ModalDialog
          isOpen={reassignModalOpen}
          onClose={closeReassignModal}
          title={
            <div className="flex items-center gap-2">
              <span className="material-symbols-outlined text-red-600 text-[24px]">swap_horiz</span>
              <span>{t('supervisor.reassignModalTitle')}</span>
            </div>
          }
        >
          <div className="space-y-4">
            {/* Target Ticket Details */}
            <div className="p-3 rounded-xl bg-surface-container-low text-xs space-y-1.5 border border-outline/10">
              <div className="flex justify-between">
                <span className="text-on-surface-variant">Ticket Reference:</span>
                <span className="font-code font-bold text-primary">{targetComplaint.referenceId}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-on-surface-variant">Category:</span>
                <span className="font-semibold text-on-surface">{targetComplaint.category}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-on-surface-variant">Currently Assigned:</span>
                <span className="font-bold text-red-600 dark:text-red-400">
                  {targetComplaint.assignedWorker?.name || 'Unassigned'}
                </span>
              </div>
            </div>

            {reassignError && (
              <div role="alert" className="p-3 rounded-xl bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-800 text-red-700 dark:text-red-300 text-xs">
                {reassignError}
              </div>
            )}

            <form onSubmit={handleReassignSubmit} className="space-y-4">
              <div>
                <label htmlFor="select-worker-input" className="block text-xs font-bold uppercase tracking-wider text-on-surface-variant mb-1.5">
                  {t('supervisor.selectWorker')} <span className="text-red-500">*</span>
                </label>
                <select
                  id="select-worker-input"
                  value={selectedWorkerId}
                  onChange={(e) => setSelectedWorkerId(e.target.value)}
                  className="w-full bg-surface border border-outline/20 rounded-xl px-3 py-2 text-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-primary"
                  required
                >
                  {availableWorkers.map(w => {
                    const isCurrent = w.id === targetComplaint.assignedWorker?.id;
                    const wName = w.fullName || w.name;
                    const wDept = w.department || 'Field Tech';
                    return (
                      <option key={w.id} value={w.id} disabled={isCurrent}>
                        {wName} — {wDept} | {w.activeOrdersCount !== undefined ? `${w.activeOrdersCount} active` : 'Active'} {w.overdueOrdersCount > 0 ? `(${w.overdueOrdersCount} overdue)` : ''} {isCurrent ? '(Currently Assigned)' : ''}
                      </option>
                    );
                  })}
                </select>
              </div>

              <div>
                <label htmlFor="reassign-reason-input" className="block text-xs font-bold uppercase tracking-wider text-on-surface-variant mb-1.5">
                  {t('supervisor.reassignReason')}
                </label>
                <textarea
                  id="reassign-reason-input"
                  value={reassignReason}
                  onChange={(e) => setReassignReason(e.target.value)}
                  placeholder={t('supervisor.reassignReasonPlaceholder')}
                  rows={3}
                  minLength={5}
                  maxLength={500}
                  className="w-full bg-surface border border-outline/20 rounded-xl p-3 text-sm text-on-surface placeholder:text-on-surface-variant/50 focus:outline-none focus:ring-2 focus:ring-primary"
                  required
                />
                <div className="text-right text-[11px] text-on-surface-variant mt-1">
                  {reassignReason.length} / 500 characters (min 5)
                </div>
              </div>

              <div className="flex items-center justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={closeReassignModal}
                  disabled={reassignSubmitting}
                  className="px-4 py-2 rounded-xl text-sm font-semibold border border-outline/20 text-on-surface hover:bg-surface-container focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={reassignSubmitting}
                  className="inline-flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold bg-red-600 text-white hover:bg-red-700 shadow-md disabled:opacity-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-red-500"
                >
                  {reassignSubmitting && (
                    <span className="material-symbols-outlined text-[16px] animate-spin">progress_activity</span>
                  )}
                  <span>{t('supervisor.confirmReassign')}</span>
                </button>
              </div>
            </form>
          </div>
        </ModalDialog>
      )}

      {/* Extend SLA Modal */}
      {targetComplaint && (
        <ModalDialog
          isOpen={extendModalOpen}
          onClose={closeExtendModal}
          title={`${t('supervisor.extendSlaModalTitle')} — #${targetComplaint.referenceId}`}
          maxWidth="max-w-lg"
        >
          <div className="p-6 space-y-4">
            <p className="text-xs text-on-surface-variant">
              Extend the SLA resolution deadline for defect <strong>#{targetComplaint.referenceId}</strong>.
              All deadline modifications are recorded immutably in the municipal audit ledger.
            </p>

            {extendError && (
              <div role="alert" aria-live="assertive" className="p-3 bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-800 rounded-xl text-xs text-red-700 dark:text-red-300">
                {extendError}
              </div>
            )}

            <form onSubmit={handleExtendSubmit} className="space-y-4">
              <div>
                <label htmlFor="extend-hours-input" className="block text-xs font-bold uppercase tracking-wider text-on-surface-variant mb-1.5">
                  {t('supervisor.additionalHours')}
                </label>
                <div className="relative">
                  <input
                    id="extend-hours-input"
                    type="number"
                    min="1"
                    max="720"
                    value={extendHours}
                    onChange={(e) => setExtendHours(e.target.value)}
                    className="w-full bg-surface border border-outline/20 rounded-xl px-3 py-2 text-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-primary"
                    required
                  />
                  <span className="absolute right-3 top-2.5 text-xs text-on-surface-variant">Hours</span>
                </div>
                <div className="text-[11px] text-on-surface-variant mt-1">
                  Common: 24h (1 day), 48h (2 days), 72h (weekend buffer)
                </div>
              </div>

              <div>
                <label htmlFor="extend-reason-input" className="block text-xs font-bold uppercase tracking-wider text-on-surface-variant mb-1.5">
                  {t('supervisor.extendReason')}
                </label>
                <textarea
                  id="extend-reason-input"
                  value={extendReason}
                  onChange={(e) => setExtendReason(e.target.value)}
                  placeholder={t('supervisor.extendReasonPlaceholder')}
                  rows={3}
                  minLength={5}
                  maxLength={500}
                  className="w-full bg-surface border border-outline/20 rounded-xl p-3 text-sm text-on-surface placeholder:text-on-surface-variant/50 focus:outline-none focus:ring-2 focus:ring-primary"
                  required
                />
                <div className="text-right text-[11px] text-on-surface-variant mt-1">
                  {extendReason.length} / 500 characters (min 5)
                </div>
              </div>

              <div className="flex items-center justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={closeExtendModal}
                  disabled={extendSubmitting}
                  className="px-4 py-2 rounded-xl text-sm font-semibold border border-outline/20 text-on-surface hover:bg-surface-container focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={extendSubmitting}
                  className="inline-flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold bg-amber-600 text-white hover:bg-amber-700 shadow-md disabled:opacity-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-amber-500"
                >
                  {extendSubmitting && (
                    <span className="material-symbols-outlined text-[16px] animate-spin">progress_activity</span>
                  )}
                  <span>{t('supervisor.confirmExtendSla')}</span>
                </button>
              </div>
            </form>
          </div>
        </ModalDialog>
      )}

      {/* De-escalate Modal */}
      {targetComplaint && (
        <ModalDialog
          isOpen={deEscalateModalOpen}
          onClose={closeDeEscalateModal}
          title={`${t('supervisor.deEscalateModalTitle')} — #${targetComplaint.referenceId}`}
          maxWidth="max-w-lg"
        >
          <div className="p-6 space-y-4">
            <p className="text-xs text-on-surface-variant">
              De-escalate defect <strong>#{targetComplaint.referenceId}</strong>.
              This removes the escalation flag and applies a mandatory 24h+ operational grace period to prevent immediate re-escalation by the SLA monitoring engine.
            </p>

            {deEscalateError && (
              <div role="alert" aria-live="assertive" className="p-3 bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-800 rounded-xl text-xs text-red-700 dark:text-red-300">
                {deEscalateError}
              </div>
            )}

            <form onSubmit={handleDeEscalateSubmit} className="space-y-4">
              <div>
                <label htmlFor="deescalate-grace-input" className="block text-xs font-bold uppercase tracking-wider text-on-surface-variant mb-1.5">
                  Operational Grace Window (Hours)
                </label>
                <div className="relative">
                  <input
                    id="deescalate-grace-input"
                    type="number"
                    min="1"
                    max="168"
                    value={deEscalateGraceHours}
                    onChange={(e) => setDeEscalateGraceHours(e.target.value)}
                    className="w-full bg-surface border border-outline/20 rounded-xl px-3 py-2 text-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-primary"
                    required
                  />
                  <span className="absolute right-3 top-2.5 text-xs text-on-surface-variant">Hours</span>
                </div>
              </div>

              <div>
                <label htmlFor="deescalate-reason-input" className="block text-xs font-bold uppercase tracking-wider text-on-surface-variant mb-1.5">
                  {t('supervisor.deEscalateReason')}
                </label>
                <textarea
                  id="deescalate-reason-input"
                  value={deEscalateReason}
                  onChange={(e) => setDeEscalateReason(e.target.value)}
                  placeholder={t('supervisor.deEscalateReasonPlaceholder')}
                  rows={3}
                  minLength={5}
                  maxLength={500}
                  className="w-full bg-surface border border-outline/20 rounded-xl p-3 text-sm text-on-surface placeholder:text-on-surface-variant/50 focus:outline-none focus:ring-2 focus:ring-primary"
                  required
                />
                <div className="text-right text-[11px] text-on-surface-variant mt-1">
                  {deEscalateReason.length} / 500 characters (min 5)
                </div>
              </div>

              <div className="flex items-center justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={closeDeEscalateModal}
                  disabled={deEscalateSubmitting}
                  className="px-4 py-2 rounded-xl text-sm font-semibold border border-outline/20 text-on-surface hover:bg-surface-container focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={deEscalateSubmitting}
                  className="inline-flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold bg-emerald-600 text-white hover:bg-emerald-700 shadow-md disabled:opacity-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-emerald-500"
                >
                  {deEscalateSubmitting && (
                    <span className="material-symbols-outlined text-[16px] animate-spin">progress_activity</span>
                  )}
                  <span>{t('supervisor.confirmDeEscalate')}</span>
                </button>
              </div>
            </form>
          </div>
        </ModalDialog>
      )}
    </div>
  );
};

export default SupervisorEscalationsPage;

