import React, { useState, useEffect, useCallback } from 'react';
import { useLocation } from 'react-router-dom';
import { useTranslation } from '../../context/LanguageContext';
import { officerService } from '../../services/officerService';
import { apiClient } from '../../services/apiClient';
import { Breadcrumb } from '../../components/common/Breadcrumb';
import ClosureTrustBadge from '../../components/common/ClosureTrustBadge';
import { translateCategory, translateErrorMessage } from '../../utils/i18nHelpers';

const CATEGORY_COLORS = [
  'bg-primary-container',
  'bg-secondary',
  'bg-amber-600',
  'bg-emerald-600',
  'bg-indigo-600',
  'bg-rose-600',
  'bg-cyan-600',
  'bg-purple-600',
];

export const OfficerAnalyticsPage = () => {
  const { t } = useTranslation();
  const location = useLocation();
  const isSupervisorScope = location.pathname.startsWith('/supervisor');
  const [summary, setSummary] = useState(null);
  const [trustScores, setTrustScores] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [wardFilter, setWardFilter] = useState('ALL');
  const [categoryFilter, setCategoryFilter] = useState('ALL');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');

  const fetchAnalytics = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const summaryParams = {
        from: fromDate || undefined,
        to: toDate || undefined,
        ward: wardFilter !== 'ALL' ? wardFilter : undefined,
        category: categoryFilter !== 'ALL' ? categoryFilter : undefined,
      };
      const [summaryData, trustData] = await Promise.all([
        isSupervisorScope
          ? apiClient.supervisor.getAnalyticsSummary(summaryParams).then((res) => res?.data || res)
          : officerService.getAnalyticsSummary(summaryParams),
        officerService.getClosureTrustScores({
          from: fromDate || undefined,
          to: toDate || undefined,
          ward: wardFilter !== 'ALL' ? wardFilter : undefined,
        })
      ]);
      setSummary(summaryData);
      setTrustScores(trustData);
    } catch (err) {
      console.error('Failed to load operational analytics:', err);
      setError(err?.message || 'Cannot reach the server. Failed to fetch database analytics.');
    } finally {
      setLoading(false);
    }
  }, [fromDate, toDate, wardFilter, categoryFilter, isSupervisorScope]);

  useEffect(() => {
    fetchAnalytics();
  }, [fetchAnalytics]);

  const categories = summary?.countsByCategory ? Object.entries(summary.countsByCategory) : [];
  const wards = summary?.countsByWard ? Object.entries(summary.countsByWard) : [];
  const statuses = summary?.countsByStatus ? Object.entries(summary.countsByStatus) : [];
  const total = summary?.totalComplaints || 0;

  return (
    <div className="w-full flex flex-col gap-6">
      <Breadcrumb
        items={[
          { label: 'Operations Command', path: '/officer/dashboard', icon: 'account_balance' },
          { label: 'Operational Analytics & SLA' }
        ]}
      />

      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="font-headline-lg text-2xl sm:text-3xl font-extrabold text-on-surface tracking-tight">
            {t('officer.analytics.title')}
          </h1>
          <p className="text-body-md text-sm text-on-surface-variant mt-1">
            {t('officer.analytics.subtitle')}
          </p>
        </div>
        <button
          onClick={fetchAnalytics}
          disabled={loading}
          className="px-4 py-2 bg-surface-container hover:bg-surface-container-high text-on-surface text-xs font-semibold rounded-xl transition-colors flex items-center gap-1.5 self-start sm:self-auto border border-outline-variant/30"
          title={t('officer.analytics.refreshButton')}
        >
          <span className={`material-symbols-outlined text-[16px] ${loading ? 'animate-spin' : ''}`}>refresh</span>
          <span>{t('officer.analytics.refreshButton')}</span>
        </button>
      </div>

      {/* Filter and Date Bar */}
      <div className="bg-surface-container-lowest p-4 rounded-2xl border border-outline-variant/30 shadow-sm flex flex-wrap gap-3 items-center justify-between text-xs">
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-1.5">
            <span className="text-tertiary font-semibold uppercase text-[11px]">{t('officer.analytics.from')}</span>
            <input
              type="date"
              value={fromDate}
              onChange={(e) => setFromDate(e.target.value)}
              className="px-2.5 py-1.5 border border-outline-variant/40 rounded-lg text-on-surface bg-surface-container-lowest"
            />
          </div>
          <div className="flex items-center gap-1.5">
            <span className="text-tertiary font-semibold uppercase text-[11px]">{t('officer.analytics.to')}</span>
            <input
              type="date"
              value={toDate}
              onChange={(e) => setToDate(e.target.value)}
              className="px-2.5 py-1.5 border border-outline-variant/40 rounded-lg text-on-surface bg-surface-container-lowest"
            />
          </div>
          <select
            value={wardFilter}
            onChange={(e) => setWardFilter(e.target.value)}
            className="px-3 py-1.5 border border-outline-variant/40 rounded-lg text-on-surface bg-surface-container-lowest"
          >
            <option value="ALL">{t('common.allWards')}</option>
            <option value="Ward 10, T. Nagar">Ward 10, T. Nagar</option>
            <option value="Ward 13, Adyar">Ward 13, Adyar</option>
            <option value="Ward 14, Velachery">Ward 14, Velachery</option>
            <option value="Ward 08, Anna Nagar">Ward 08, Anna Nagar</option>
            <option value="Ward 09, Kodambakkam">Ward 09, Kodambakkam</option>
            <option value="Ward 04, Tondiarpet">Ward 04, Tondiarpet</option>
            <option value="Ward 11, Guindy">Ward 11, Guindy</option>
          </select>
          <select
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value)}
            className="px-3 py-1.5 border border-outline-variant/40 rounded-lg text-on-surface bg-surface-container-lowest"
          >
            <option value="ALL">{t('common.allCategories')}</option>
            <option value="Roads & Pavements">{t('categories.roads')}</option>
            <option value="Water Supply & Sewage Management">{t('categories.water')}</option>
            <option value="Solid Waste & Sanitation">{t('categories.garbage')}</option>
            <option value="Municipal Traffic & Electrical">{t('categories.lighting')}</option>
            <option value="Parks & Urban Environment">{t('categories.health')}</option>
          </select>
        </div>

        {(fromDate || toDate || wardFilter !== 'ALL' || categoryFilter !== 'ALL') && (
          <button
            onClick={() => {
              setFromDate('');
              setToDate('');
              setWardFilter('ALL');
              setCategoryFilter('ALL');
            }}
            className="text-primary hover:underline font-semibold text-xs"
          >
            {t('common.resetFilters')}
          </button>
        )}
      </div>

      {error && (
        <div className="p-4 bg-error-container/20 border border-error/30 rounded-2xl flex items-center justify-between text-error text-xs font-semibold">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-[20px]">error</span>
            <span>{translateErrorMessage(error, t)}</span>
          </div>
          <button
            onClick={fetchAnalytics}
            className="px-3 py-1 bg-error text-white rounded-lg text-xs font-bold hover:bg-error/90 transition-colors"
          >
            {t('common.retry')}
          </button>
        </div>
      )}

      {loading && !summary ? (
        <div className="p-16 text-center text-on-surface-variant flex flex-col items-center justify-center gap-3">
          <span className="material-symbols-outlined text-4xl animate-spin text-primary-container">progress_activity</span>
          <p className="text-sm font-semibold">{t('officer.analytics.computing')}</p>
        </div>
      ) : (
        <>
          {/* Top 4 KPI Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {/* Mean & Median Resolution Time */}
            <div className="bg-surface-container-lowest p-5 rounded-2xl shadow-sm border border-outline-variant/30 flex flex-col justify-between">
              <div>
                <span className="text-xs font-bold text-tertiary uppercase">{t('officer.analytics.meanResolutionTime')}</span>
                <p className="font-display text-3xl font-bold text-primary mt-2">
                  {summary?.averageResolutionHours != null ? `${summary.averageResolutionHours} hrs` : t('common.noData')}
                </p>
              </div>
              <span className="text-xs text-on-surface-variant font-medium mt-2 block">
                {summary?.medianResolutionHours != null
                  ? t('officer.analytics.medianHours', { hours: summary.medianResolutionHours })
                  : t('officer.analytics.awaitingResolutions')}
              </span>
            </div>

            {/* First-Pass Verification & Rework Rate */}
            <div className="bg-surface-container-lowest p-5 rounded-2xl shadow-sm border border-outline-variant/30 flex flex-col justify-between">
              <div>
                <span className="text-xs font-bold text-tertiary uppercase">{t('officer.analytics.firstPassRate')}</span>
                <p className="font-display text-3xl font-bold text-secondary mt-2">
                  {summary?.reworkRate != null ? `${(100 - summary.reworkRate).toFixed(1)}%` : '100%'}
                </p>
              </div>
              <span className="text-xs text-secondary font-semibold mt-2 block">
                {t('officer.analytics.reworkRate', { rate: summary?.reworkRate != null ? summary.reworkRate : 0 })}
              </span>
            </div>

            {/* Rule Adjudication & Duplicates */}
            <div className="bg-surface-container-lowest p-5 rounded-2xl shadow-sm border border-outline-variant/30 flex flex-col justify-between">
              <div>
                <span className="text-xs font-bold text-tertiary uppercase">{t('officer.analytics.ruleTriageTitle')}</span>
                <p className="font-display text-3xl font-bold text-emerald-700 mt-2">
                  {t('officer.analytics.acceptedCount', { count: summary?.aiAdjudicationCounts?.accepted ?? 0 })}
                </p>
              </div>
              <span className="text-xs text-tertiary mt-2 block truncate">
                {t('officer.analytics.triageBreakdown', {
                  overridden: summary?.aiAdjudicationCounts?.overridden ?? 0,
                  dismissed: summary?.aiAdjudicationCounts?.dismissed ?? 0,
                  duplicates: summary?.duplicatesLinked ?? 0
                })}
              </span>
            </div>

            {/* Total Defect Inventory */}
            <div className="bg-surface-container-lowest p-5 rounded-2xl shadow-sm border border-outline-variant/30 flex flex-col justify-between">
              <div>
                <span className="text-xs font-bold text-tertiary uppercase">{t('officer.analytics.totalInventory')}</span>
                <p className="font-display text-3xl font-bold text-on-surface mt-2">{total}</p>
              </div>
              <span className="text-xs text-tertiary mt-2 block">
                {t('officer.analytics.inventoryBreakdown', {
                  resolved: summary?.countsByStatus?.RESOLVED ?? 0,
                  inProgress: summary?.countsByStatus?.IN_PROGRESS ?? 0
                })}
              </span>
            </div>
          </div>

          {/* Main Distribution Matrices */}
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
            {/* Category Distribution (7 cols) */}
            <div className="lg:col-span-7 bg-surface-container-lowest rounded-2xl p-6 shadow-sm border border-outline-variant/30 space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="font-title text-base font-bold text-on-surface">{t('officer.analytics.categoryDistTitle')}</h3>
                <span className="text-xs text-tertiary font-mono">{t('officer.analytics.categoriesCount', { count: categories.length })}</span>
              </div>

              {categories.length === 0 ? (
                <div className="p-8 text-center text-xs text-tertiary">
                  {t('officer.analytics.noCategoryData')}
                </div>
              ) : (
                <div className="space-y-3">
                  {categories.map(([catName, count], idx) => {
                    const pct = total > 0 ? Math.round((count / total) * 100) : 0;
                    const color = CATEGORY_COLORS[idx % CATEGORY_COLORS.length];
                    return (
                      <div key={catName} className="space-y-1 text-xs">
                        <div className="flex justify-between">
                          <span className="font-semibold text-on-surface">{translateCategory(catName, t)}</span>
                          <span className="font-code text-tertiary">{t('common.casesCount', { count })} ({pct}%)</span>
                        </div>
                        <div className="w-full bg-surface-container-low rounded-full h-2 overflow-hidden">
                          <div className={`${color} h-full rounded-full transition-all`} style={{ width: `${pct}%` }} />
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            {/* Ward Distribution & SLA Status (5 cols) */}
            <div className="lg:col-span-5 space-y-6">
              {/* Ward Breakdown */}
              <div className="bg-surface-container-lowest rounded-2xl p-6 shadow-sm border border-outline-variant/30 space-y-4">
                <div className="flex items-center justify-between">
                  <h3 className="font-title text-base font-bold text-on-surface">{t('officer.analytics.wardVolumeTitle')}</h3>
                  <span className="text-xs text-tertiary font-mono">{t('officer.analytics.wardsCount', { count: wards.length })}</span>
                </div>

                {wards.length === 0 ? (
                  <div className="p-8 text-center text-xs text-tertiary">
                    {t('officer.analytics.noWardData')}
                  </div>
                ) : (
                  <div className="space-y-2">
                    {wards.map(([wardName, count]) => (
                      <div key={wardName} className="p-2.5 rounded-xl bg-surface-container-low flex justify-between items-center text-xs">
                        <span className="font-semibold text-on-surface">{wardName}</span>
                        <span className="font-code font-bold text-primary-container px-2 py-0.5 rounded bg-surface-container">
                          {t('common.ticketsCount', { count })}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* SLA & Compliance Status */}
              <div className="bg-surface-container-lowest rounded-2xl p-6 shadow-sm border border-outline-variant/30 space-y-4">
                <h3 className="font-title text-base font-bold text-on-surface">{t('officer.analytics.slaTelemetryTitle')}</h3>
                <div className="space-y-2.5 text-xs">
                  <div className="p-3 bg-surface-container-low rounded-xl flex justify-between items-center">
                    <span className="text-tertiary font-medium">{t('officer.analytics.slaAdherence')}</span>
                    <span className="font-semibold px-2 py-0.5 rounded text-[11px] bg-slate-200 text-slate-700">
                      {summary?.slaAdherenceRate != null ? `${summary.slaAdherenceRate}%` : t('officer.analytics.slaNotConfigured')}
                    </span>
                  </div>
                  <div className="p-3 bg-surface-container-low rounded-xl flex justify-between items-center">
                    <span className="text-tertiary font-medium">{t('officer.analytics.postgresAggregates')}:</span>
                    <span className="font-semibold text-emerald-600">SQL</span>
                  </div>
                  <div className="p-3 bg-surface-container-low rounded-xl flex justify-between items-center">
                    <span className="text-tertiary font-medium">{t('officer.analytics.auditLedgerTitle')}</span>
                    <span className="font-semibold text-primary-container">{t('officer.analytics.auditActive')}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Closure Trust Score Analytics Section */}
          <div className="bg-surface-container-lowest rounded-2xl p-6 shadow-sm border border-outline-variant/30 space-y-6">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-outline-variant/30 pb-4">
              <div>
                <div className="flex items-center gap-2">
                  <span className="material-symbols-outlined text-primary text-[24px]">verified_user</span>
                  <h3 className="font-title text-lg font-bold text-on-surface">{t('supervisor.closureTrustTitle')}</h3>
                </div>
                <p className="text-xs text-on-surface-variant mt-1">
                  {t('supervisor.closureTrustDesc')}
                </p>
              </div>

              {trustScores?.overall && (
                <div className="flex items-center gap-3 bg-surface-container-low px-4 py-2.5 rounded-2xl border border-outline-variant/20">
                  <div className="text-right">
                    <span className="text-[10px] font-bold text-tertiary uppercase tracking-wider block">Overall Trust Index</span>
                    <span className="text-xs text-on-surface-variant font-mono">
                      n = {trustScores.overall.totalClosures || 0} closures
                    </span>
                  </div>
                  <ClosureTrustBadge
                    score={trustScores.overall.trustScore}
                    total={trustScores.overall.totalClosures}
                    confirmed={trustScores.overall.confirmedClosures}
                    rejected={trustScores.overall.citizenRejections}
                    reopened={trustScores.overall.reopenedAfterResolution}
                    statusMessage={trustScores.overall.statusMessage}
                    size="lg"
                  />
                </div>
              )}
            </div>

            {/* Sub-tables: Wards & Officers */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* Trust Score by Ward */}
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <h4 className="font-title text-sm font-bold text-on-surface flex items-center gap-1.5">
                    <span className="material-symbols-outlined text-tertiary text-[18px]">location_city</span>
                    <span>Closure Trust by Municipal Ward</span>
                  </h4>
                  <span className="text-[11px] text-tertiary font-mono">
                    {trustScores?.byWard?.length || 0} Wards
                  </span>
                </div>

                {(!trustScores?.byWard || trustScores.byWard.length === 0) ? (
                  <div className="p-6 rounded-xl bg-surface-container-low text-center text-xs text-tertiary">
                    No ward closure events recorded in selected window.
                  </div>
                ) : (
                  <div className="border border-outline-variant/30 rounded-xl overflow-hidden overflow-x-auto">
                    <table className="w-full text-left text-xs">
                      <thead className="bg-surface-container-low border-b border-outline-variant/30 font-semibold text-tertiary uppercase text-[10px]">
                        <tr>
                          <th className="py-2.5 px-3">Ward</th>
                          <th className="py-2.5 px-2 text-center" title="Confirmed Closures">Conf.</th>
                          <th className="py-2.5 px-2 text-center" title="Citizen Rejections">Rej.</th>
                          <th className="py-2.5 px-2 text-center" title="Reopened After Resolution">Reop.</th>
                          <th className="py-2.5 px-2 text-center" title="Total Closures">Total</th>
                          <th className="py-2.5 px-3 text-right">Trust Score</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-outline-variant/20 bg-surface-container-lowest">
                        {trustScores.byWard.map((w) => (
                          <tr key={w.ward} className="hover:bg-surface-container-low/50 transition-colors">
                            <td className="py-2.5 px-3 font-semibold text-on-surface truncate max-w-[140px]" title={w.ward}>
                              {w.ward}
                            </td>
                            <td className="py-2.5 px-2 text-center font-mono text-emerald-600 font-medium">{w.confirmedClosures}</td>
                            <td className="py-2.5 px-2 text-center font-mono text-rose-600 font-medium">{w.citizenRejections}</td>
                            <td className="py-2.5 px-2 text-center font-mono text-amber-600 font-medium">{w.reopenedAfterResolution}</td>
                            <td className="py-2.5 px-2 text-center font-mono text-on-surface font-semibold">{w.totalClosures}</td>
                            <td className="py-2.5 px-3 text-right">
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

              {/* Trust Score by Officer */}
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <h4 className="font-title text-sm font-bold text-on-surface flex items-center gap-1.5">
                    <span className="material-symbols-outlined text-tertiary text-[18px]">badge</span>
                    <span>Closure Trust by Assigned Officer</span>
                  </h4>
                  <span className="text-[11px] text-tertiary font-mono">
                    {trustScores?.byOfficer?.length || 0} Officers
                  </span>
                </div>

                {(!trustScores?.byOfficer || trustScores.byOfficer.length === 0) ? (
                  <div className="p-6 rounded-xl bg-surface-container-low text-center text-xs text-tertiary">
                    No officer closure activity recorded in selected window.
                  </div>
                ) : (
                  <div className="border border-outline-variant/30 rounded-xl overflow-hidden overflow-x-auto">
                    <table className="w-full text-left text-xs">
                      <thead className="bg-surface-container-low border-b border-outline-variant/30 font-semibold text-tertiary uppercase text-[10px]">
                        <tr>
                          <th className="py-2.5 px-3">Officer</th>
                          <th className="py-2.5 px-2">Dept</th>
                          <th className="py-2.5 px-2 text-center" title="Confirmed Closures">Conf.</th>
                          <th className="py-2.5 px-2 text-center" title="Citizen Rejections">Rej.</th>
                          <th className="py-2.5 px-2 text-center" title="Reopened After Resolution">Reop.</th>
                          <th className="py-2.5 px-2 text-center" title="Total Closures">Total</th>
                          <th className="py-2.5 px-3 text-right">Trust Score</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-outline-variant/20 bg-surface-container-lowest">
                        {trustScores.byOfficer.map((off) => (
                          <tr key={off.officerId || off.officerName} className="hover:bg-surface-container-low/50 transition-colors">
                            <td className="py-2.5 px-3">
                              <p className="font-semibold text-on-surface truncate max-w-[120px]" title={off.officerName}>
                                {off.officerName}
                              </p>
                              <p className="text-[10px] text-tertiary truncate max-w-[120px]" title={off.officerEmail}>
                                {off.officerEmail}
                              </p>
                            </td>
                            <td className="py-2.5 px-2 text-tertiary truncate max-w-[100px]" title={off.department}>
                              {off.department || 'General'}
                            </td>
                            <td className="py-2.5 px-2 text-center font-mono text-emerald-600 font-medium">{off.confirmedClosures}</td>
                            <td className="py-2.5 px-2 text-center font-mono text-rose-600 font-medium">{off.citizenRejections}</td>
                            <td className="py-2.5 px-2 text-center font-mono text-amber-600 font-medium">{off.reopenedAfterResolution}</td>
                            <td className="py-2.5 px-2 text-center font-mono text-on-surface font-semibold">{off.totalClosures}</td>
                            <td className="py-2.5 px-3 text-right">
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
          </div>

          {/* Status State Machine Breakdown */}
          <div className="bg-surface-container-lowest rounded-2xl p-6 shadow-sm border border-outline-variant/30 space-y-4">
            <h3 className="font-title text-base font-bold text-on-surface">Lifecycle Status Breakdown</h3>
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-7 gap-3">
              {statuses.map(([st, cnt]) => (
                <div key={st} className="p-3 bg-surface-container-low rounded-xl text-center">
                  <span className="text-[10px] uppercase font-bold text-tertiary block truncate" title={st}>
                    {st.replace(/_/g, ' ')}
                  </span>
                  <span className="font-display text-2xl font-extrabold text-on-surface mt-1 block">
                    {cnt}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </>
      )}
    </div>
  );
};

export default OfficerAnalyticsPage;
