import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from '../../context/LanguageContext';
import { translateCategory, translateErrorMessage } from '../../utils/i18nHelpers';
import { complaintService } from '../../services/complaintService';
import { StatusBadge } from '../../components/common/StatusBadge';
import { PriorityBadge } from '../../components/common/PriorityBadge';
import { Breadcrumb } from '../../components/common/Breadcrumb';
import { TamilNaduOverviewMap } from '../../components/common/TamilNaduOverviewMap';
import { CATEGORIES } from '../../constants/statusConstants';
import { TAMIL_NADU_DISTRICTS } from '../../constants/tamilNaduDistricts';

export const CitizenIssueMapPage = () => {
  const { t } = useTranslation();
  const [complaints, setComplaints] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedComplaint, setSelectedComplaint] = useState(null);
  const [viewMode, setViewMode] = useState('split'); // 'split', 'map'
  const [activeCategory, setActiveCategory] = useState('ALL');
  const [activeStatus, setActiveStatus] = useState('ALL');
  const [activeDistrict, setActiveDistrict] = useState('ALL');
  const [searchQuery, setSearchQuery] = useState('');

  const fetchComplaints = useCallback(() => {
    setLoading(true);
    setError(null);
    const params = {};
    if (activeStatus !== 'ALL') params.status = activeStatus;
    if (activeCategory !== 'ALL') params.category = activeCategory;

    complaintService.getPublicMapComplaints(params)
      .then((data) => {
        setComplaints(data);
        if (data.length > 0) {
          setSelectedComplaint((prev) => prev ? (data.find(c => c.id === prev.id) || data[0]) : data[0]);
        } else {
          setSelectedComplaint(null);
        }
      })
      .catch((err) => {
        setError(err.message || 'Failed to load community map feed.');
      })
      .finally(() => {
        setLoading(false);
      });
  }, [activeStatus, activeCategory]);

  useEffect(() => {
    fetchComplaints();
  }, [fetchComplaints]);

  // Filter complaints based on search and public criteria
  const filtered = complaints.filter((c) => {
    const q = searchQuery.toLowerCase();
    const matchesSearch =
      (c.category && c.category.toLowerCase().includes(q)) ||
      (c.ward && c.ward.toLowerCase().includes(q)) ||
      (c.id && String(c.id).toLowerCase().includes(q));

    const matchesCategory = activeCategory === 'ALL' || c.category === activeCategory || c.categoryId === activeCategory;
    const matchesStatus = activeStatus === 'ALL' || c.status === activeStatus;
    const matchesDistrict = activeDistrict === 'ALL' ||
      (c.ward && c.ward.toLowerCase().includes(activeDistrict.toLowerCase())) ||
      (c.location?.address && c.location.address.toLowerCase().includes(activeDistrict.toLowerCase())) ||
      (c.location?.ward && c.location.ward.toLowerCase().includes(activeDistrict.toLowerCase()));

    return matchesSearch && matchesCategory && matchesStatus && matchesDistrict;
  });

  // Filter valid geographic coordinates
  const validCoordinatesCount = filtered.filter(
    (c) => c.location?.coordinates?.lat && c.location?.coordinates?.lng
  ).length;

  return (
    <div className="w-full flex flex-col gap-4">
      <Breadcrumb
        items={[
          { label: t('citizenMap.breadcrumbPortal'), path: '/citizen/dashboard', icon: 'home' },
          { label: t('citizenMap.breadcrumbTitle') }
        ]}
      />

      {/* Top Header & Public Controls */}
      {error && (
        <div className="p-4 bg-red-50 border border-red-200 rounded-2xl flex items-center justify-between text-red-800 text-xs">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-red-600 text-[20px]">error</span>
            <span>{translateErrorMessage(error, t, 'citizenMap.unableToLoadMap')}</span>
          </div>
          <button
            onClick={fetchComplaints}
            className="px-3 py-1.5 bg-red-600 hover:bg-red-700 text-white font-semibold rounded-lg shrink-0 transition-colors flex items-center gap-1"
          >
            <span className="material-symbols-outlined text-[16px]">refresh</span>
            <span>{t('common.retry')}</span>
          </button>
        </div>
      )}
      <div className="bg-surface-container-lowest rounded-2xl p-4 sm:p-5 shadow-sm border border-outline-variant/30 space-y-4">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3">
          <div>
            <div className="flex items-center gap-2">
              <h1 className="font-headline-sm text-xl font-bold text-on-surface">{t('citizenMap.title')}</h1>
              <span className="font-code text-xs px-2.5 py-0.5 rounded-full bg-primary/10 text-primary font-bold">
                {t('citizenMap.gridBadge')}
              </span>
            </div>
            <p className="text-xs text-on-surface-variant mt-0.5">
              {t('citizenMap.subtitle')}
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {/* View Mode Switcher */}
            <div className="flex items-center p-1 rounded-xl bg-surface-container-low text-xs">
              <button
                type="button"
                onClick={() => setViewMode('split')}
                className={`px-3 py-1.5 rounded-lg font-semibold transition-all flex items-center gap-1.5 ${
                  viewMode === 'split' ? 'bg-surface-container-lowest text-primary shadow-sm' : 'text-tertiary'
                }`}
              >
                <span className="material-symbols-outlined text-[16px]">splitscreen</span>
                <span>{t('citizenMap.listAndMap')}</span>
              </button>
              <button
                type="button"
                onClick={() => setViewMode('map')}
                className={`px-3 py-1.5 rounded-lg font-semibold transition-all flex items-center gap-1.5 ${
                  viewMode === 'map' ? 'bg-surface-container-lowest text-primary shadow-sm' : 'text-tertiary'
                }`}
              >
                <span className="material-symbols-outlined text-[16px]">map</span>
                <span>{t('citizenMap.fullMap')}</span>
              </button>
            </div>

            <Link
              to="/citizen/complaints/new"
              className="px-4 py-2 bg-primary-container hover:bg-primary text-white text-xs font-semibold rounded-xl shadow-sm transition-all flex items-center gap-1.5"
            >
              <span className="material-symbols-outlined text-[16px]">add_location_alt</span>
              <span>{t('citizenMap.reportIssue')}</span>
            </Link>
          </div>
        </div>

        {/* Filter Bar */}
        <div className="flex flex-wrap items-center gap-3 pt-2 border-t border-outline-variant/30 text-xs">
          <div className="relative flex-1 min-w-[200px] max-w-xs">
            <span className="material-symbols-outlined absolute left-3 top-2 text-tertiary text-[18px]">
              search
            </span>
            <input
              type="text"
              placeholder={t('citizenMap.searchPlaceholder')}
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-3 py-1.5 bg-surface-container-low rounded-xl text-xs text-on-surface placeholder:text-outline border border-transparent focus:border-primary-container focus:outline-none"
            />
          </div>

          <div className="flex items-center gap-2">
            <label className="text-tertiary text-[11px] font-semibold uppercase">{t('citizenMap.categoryLabel')}</label>
            <select
              value={activeCategory}
              onChange={(e) => setActiveCategory(e.target.value)}
              className="px-2.5 py-1.5 rounded-lg bg-surface-container-low border border-outline-variant/40 text-xs text-on-surface"
            >
              <option value="ALL">{t('citizenMap.allCategories')}</option>
              {CATEGORIES.map((cat) => (
                <option key={cat.id} value={cat.id}>
                  {translateCategory(cat.name, t)}
                </option>
              ))}
            </select>
          </div>

          <div className="flex items-center gap-2">
            <label className="text-tertiary text-[11px] font-semibold uppercase">{t('citizenMap.statusLabel')}</label>
            <select
              value={activeStatus}
              onChange={(e) => setActiveStatus(e.target.value)}
              className="px-2.5 py-1.5 rounded-lg bg-surface-container-low border border-outline-variant/40 text-xs text-on-surface"
            >
              <option value="ALL">{t('citizenMap.allStatuses')}</option>
              <option value="SUBMITTED">{t('status.SUBMITTED')}</option>
              <option value="UNDER_REVIEW">{t('status.UNDER_REVIEW')}</option>
              <option value="ASSIGNED">{t('status.ASSIGNED')}</option>
              <option value="IN_PROGRESS">{t('status.IN_PROGRESS')}</option>
              <option value="RESOLUTION_SUBMITTED">{t('status.RESOLUTION_SUBMITTED')}</option>
              <option value="RESOLVED">{t('status.RESOLVED')}</option>
            </select>
          </div>

          <div className="flex items-center gap-2">
            <label className="text-tertiary text-[11px] font-semibold uppercase">{t('citizenMap.districtLabel')}</label>
            <select
              value={activeDistrict}
              onChange={(e) => setActiveDistrict(e.target.value)}
              className="px-2.5 py-1.5 rounded-lg bg-surface-container-low border border-outline-variant/40 text-xs text-on-surface"
            >
              <option value="ALL">{t('citizenMap.allDistricts')}</option>
              {TAMIL_NADU_DISTRICTS.map((d) => (
                <option key={d.name} value={d.name}>
                  {d.name} ({d.region})
                </option>
              ))}
            </select>
          </div>

          <span className="text-tertiary text-xs ml-auto font-mono">
            {t('citizenMap.pinpointsMapped', { mapped: validCoordinatesCount, total: filtered.length })}
          </span>
        </div>
      </div>

      {/* Main Map + Inspection Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4 h-[650px]">
        {/* Split List (if not in map-only mode) (4 cols) */}
        {viewMode !== 'map' && (
          <div className="lg:col-span-4 bg-surface-container-lowest rounded-2xl p-4 shadow-sm border border-outline-variant/30 flex flex-col h-full overflow-hidden">
            <div className="flex items-center justify-between pb-2 border-b border-outline-variant/30">
              <span className="text-xs font-bold text-tertiary uppercase tracking-wider">
                {t('citizenMap.publicIssuesList', { count: filtered.length })}
              </span>
              <span className="text-[10px] text-primary font-mono">{t('citizenMap.liveSync')}</span>
            </div>

            <div className="flex-1 overflow-y-auto space-y-2.5 pr-1 mt-3">
              {loading ? (
                <div className="p-8 text-center text-xs text-tertiary space-y-2">
                  <span className="material-symbols-outlined text-2xl animate-spin text-primary">progress_activity</span>
                  <p>{t('citizenMap.loadingReports')}</p>
                </div>
              ) : error ? (
                <div className="p-8 text-center text-xs space-y-3">
                  <span className="material-symbols-outlined text-3xl text-red-600">cloud_off</span>
                  <p className="font-semibold text-on-surface">{t('citizenMap.unableToLoadMap')}</p>
                  <p className="text-on-surface-variant">{translateErrorMessage(error, t, 'citizenMap.unableToLoadMap')}</p>
                  <button
                    onClick={fetchComplaints}
                    className="px-3 py-1.5 bg-primary-container text-white rounded-lg font-semibold hover:bg-primary transition-colors inline-flex items-center gap-1"
                  >
                    <span className="material-symbols-outlined text-[14px]">refresh</span>
                    <span>{t('common.retry')}</span>
                  </button>
                </div>
              ) : filtered.length > 0 ? (
                filtered.map((c) => {
                  const isSelected = selectedComplaint?.id === c.id;
                  const hasCoordinates = c.location?.coordinates?.lat && c.location?.coordinates?.lng;

                  return (
                    <div
                      key={c.id}
                      onClick={() => setSelectedComplaint(c)}
                      className={`p-3.5 rounded-xl border cursor-pointer transition-all ${
                        isSelected
                          ? 'border-primary bg-surface-container-low ring-1 ring-primary shadow-sm'
                          : 'border-outline-variant/40 bg-surface-container-lowest hover:bg-surface-container-low/40'
                      }`}
                    >
                      <div className="flex items-center justify-between text-xs mb-1">
                        <span className="font-mono font-bold text-on-surface">{t('citizenMap.issueNumber', { id: c.id })}</span>
                        <PriorityBadge priority={c.priority} />
                      </div>
                      <h4 className="font-title text-xs font-bold text-on-surface line-clamp-1">{translateCategory(c.category, t)}</h4>
                      <p className="text-[11px] text-on-surface-variant flex items-center gap-1 mt-1 truncate">
                        <span className="material-symbols-outlined text-[13px] text-tertiary">location_on</span>
                        <span>{c.ward || t('citizenMap.defaultRegion')}</span>
                      </p>
                      <div className="flex items-center justify-between pt-2 mt-1 border-t border-outline-variant/20 text-[10px]">
                        <StatusBadge status={c.status} size="sm" showIcon={false} />
                        <span className="text-tertiary font-medium">{c.ward || t('citizenMap.defaultRegion')}</span>
                      </div>

                      {!hasCoordinates && (
                        <div className="text-[10px] text-amber-700 bg-amber-50 px-2 py-0.5 rounded mt-1.5 font-medium flex items-center gap-1">
                          <span className="material-symbols-outlined text-[12px]">warning</span>
                          <span>{t('citizenMap.noGps')}</span>
                        </div>
                      )}
                    </div>
                  );
                })
              ) : (
                <div className="p-8 text-center text-xs text-tertiary space-y-1">
                  <span className="material-symbols-outlined text-3xl text-slate-300">search_off</span>
                  <p className="font-semibold">{t('citizenMap.noComplaintsFound')}</p>
                  <p className="text-[11px]">{t('citizenMap.tryClearing')}</p>
                </div>
              )}
            </div>
          </div>
        )}

        {/* Real Leaflet Map (8 or 12 cols) */}
        <div
          className={`${
            viewMode === 'map' ? 'lg:col-span-12' : 'lg:col-span-8'
          } rounded-2xl overflow-hidden shadow-sm border border-outline-variant/30 h-full min-h-[500px] flex flex-col flex-1`}
        >
          {filtered.length === 0 ? (
            <div className="w-full h-full min-h-[480px] bg-slate-50 flex flex-col items-center justify-center p-6 text-center space-y-2">
              <span className="material-symbols-outlined text-4xl text-slate-300">location_off</span>
              <p className="text-sm font-semibold text-on-surface">{t('citizenMap.noMarkersForFilters')}</p>
              <button
                type="button"
                onClick={() => {
                  setActiveCategory('ALL');
                  setActiveStatus('ALL');
                  setActiveDistrict('ALL');
                  setSearchQuery('');
                }}
                className="px-3.5 py-1.5 bg-primary-container text-white text-xs font-semibold rounded-lg shadow-sm"
              >
                {t('citizenMap.resetFilters')}
              </button>
            </div>
          ) : (
            <TamilNaduOverviewMap
              complaints={filtered}
              selectedComplaintId={selectedComplaint?.id}
              onSelectComplaint={(c) => setSelectedComplaint(c)}
              height="100%"
            />
          )}
        </div>
      </div>
    </div>
  );
};

export default CitizenIssueMapPage;
