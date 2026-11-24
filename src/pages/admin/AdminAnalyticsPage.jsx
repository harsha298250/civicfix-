import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from '../../context/LanguageContext';
import { useApp } from '../../context/AppContext';
import { apiClient } from '../../services/apiClient';
import Breadcrumb from '../../components/common/Breadcrumb';

export default function AdminAnalyticsPage() {
  const { t } = useTranslation();
  const { showToast } = useApp();

  const [activeTab, setActiveTab] = useState('staff'); // 'staff' | 'area' | 'category' | 'monthly' | 'yearly'
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState(null);

  // Filters
  const [departmentFilter, setDepartmentFilter] = useState('ALL');
  const [wardFilter, setWardFilter] = useState('');
  const [yearFilter, setYearFilter] = useState(new Date().getFullYear());
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');

  // Data states
  const [staffData, setStaffData] = useState([]);
  const [areaData, setAreaData] = useState([]);
  const [categoryData, setCategoryData] = useState([]);
  const [monthlyData, setMonthlyData] = useState([]);
  const [yearlyData, setYearlyData] = useState([]);

  const loadData = async () => {
    setLoading(true);
    setError(null);
    try {
      const params = {};
      if (startDate) params.startDate = new Date(startDate).toISOString();
      if (endDate) params.endDate = new Date(endDate).toISOString();

      if (activeTab === 'staff') {
        if (departmentFilter !== 'ALL') params.department = departmentFilter;
        const res = await apiClient.admin.getStaffPerformance(params);
        setStaffData(res?.data || []);
      } else if (activeTab === 'area') {
        if (wardFilter.trim()) params.ward = wardFilter.trim();
        const res = await apiClient.admin.getAreaPerformance(params);
        setAreaData(res?.data || []);
      } else if (activeTab === 'category') {
        const res = await apiClient.admin.getCategoryPerformance(params);
        setCategoryData(res?.data || []);
      } else if (activeTab === 'monthly') {
        params.year = yearFilter;
        const res = await apiClient.admin.getMonthlyAnalytics(params);
        setMonthlyData(res?.data || []);
      } else if (activeTab === 'yearly') {
        const res = await apiClient.admin.getYearlyAnalytics();
        setYearlyData(res?.data || []);
      }
    } catch (err) {
      console.error('Failed to load administrative analytics:', err);
      setError(err?.message || 'Unable to retrieve analytics data from server.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [activeTab, departmentFilter, yearFilter]);

  const handleApplyDateFilter = (e) => {
    e.preventDefault();
    loadData();
  };

  const handleExportCsv = async () => {
    setExporting(true);
    try {
      const params = { reportType: activeTab };
      if (departmentFilter !== 'ALL') params.department = departmentFilter;
      if (wardFilter.trim()) params.ward = wardFilter.trim();
      if (yearFilter) params.year = yearFilter;
      if (startDate) params.startDate = new Date(startDate).toISOString();
      if (endDate) params.endDate = new Date(endDate).toISOString();

      const blob = await apiClient.admin.downloadAnalyticsCsv(params);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `civicfix_${activeTab}_analytics_${Date.now()}.csv`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
      showToast(`Exported ${activeTab.toUpperCase()} report as CSV`, 'success');
    } catch (err) {
      console.error('CSV export failed:', err);
      showToast(err?.message || 'Failed to generate CSV export', 'error');
    } finally {
      setExporting(false);
    }
  };

  return (
    <div className="space-y-6 max-w-7xl mx-auto pb-12">
      <Breadcrumb
        items={[
          { label: 'Admin Console', to: '/admin/dashboard' },
          { label: 'Administrative Analytics', to: '/admin/analytics' },
        ]}
      />

      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-[#18232F] flex items-center gap-2">
            <span className="material-symbols-outlined text-[#137A70] text-3xl">monitoring</span>
            Administrative Performance & SLA Analytics
          </h1>
          <p className="text-sm text-[#667384] mt-1">
            Server-aggregated telemetry, workload balance, resolution efficiency, and municipal SLA compliance.
          </p>
        </div>

        <button
          onClick={handleExportCsv}
          disabled={exporting || loading}
          className="inline-flex items-center gap-2 px-4 py-2.5 bg-[#137A70] hover:bg-[#0F625B] text-white text-xs font-semibold rounded-xl shadow-sm transition-all disabled:opacity-50"
        >
          <span className={`material-symbols-outlined text-[18px] ${exporting ? 'animate-spin' : ''}`}>
            {exporting ? 'progress_activity' : 'download'}
          </span>
          <span>{exporting ? 'Exporting CSV...' : 'Export Current Report (.csv)'}</span>
        </button>
      </div>

      {/* Navigation Tabs */}
      <div className="flex flex-wrap items-center gap-2 border-b border-[#E2E7ED] pb-px">
        {[
          { id: 'staff', label: 'Staff Workload & Performance', icon: 'badge' },
          { id: 'area', label: 'Area & Ward Breakdown', icon: 'map' },
          { id: 'category', label: 'Category & SLA Breaches', icon: 'category' },
          { id: 'monthly', label: 'Monthly Trends', icon: 'calendar_month' },
          { id: 'yearly', label: 'Yearly Overview', icon: 'history' },
        ].map(tab => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`flex items-center gap-2 px-4 py-3 text-xs font-semibold rounded-t-xl transition-all border-b-2 ${
              activeTab === tab.id
                ? 'border-[#137A70] text-[#137A70] bg-[#E8F4F1]/40'
                : 'border-transparent text-[#667384] hover:text-[#18232F] hover:bg-slate-50'
            }`}
          >
            <span className="material-symbols-outlined text-[18px]">{tab.icon}</span>
            <span>{tab.label}</span>
          </button>
        ))}
      </div>

      {/* Filter Bar */}
      <div className="bg-white p-4 rounded-xl border border-[#E2E7ED] shadow-sm flex flex-wrap items-center justify-between gap-3 text-xs">
        <div className="flex flex-wrap items-center gap-3">
          {activeTab === 'staff' && (
            <div className="flex items-center gap-2">
              <span className="font-semibold text-slate-700">Department:</span>
              <select
                value={departmentFilter}
                onChange={(e) => setDepartmentFilter(e.target.value)}
                className="border border-[#E2E7ED] rounded-lg px-2.5 py-1.5 bg-white text-slate-900 focus:outline-none focus:ring-2 focus:ring-[#137A70]"
              >
                <option value="ALL">All Departments</option>
                <option value="Roads & Public Infrastructure">Roads & Public Infrastructure</option>
                <option value="Public Works & Sanitation">Public Works & Sanitation</option>
                <option value="Water Supply & Sewage Management">Water Supply & Sewage Management</option>
                <option value="Municipal Traffic & Electrical">Municipal Traffic & Electrical</option>
                <option value="Parks & Urban Environment">Parks & Urban Environment</option>
                <option value="SDOT Rapid Asphalt Maintenance">SDOT Rapid Asphalt Maintenance</option>
              </select>
            </div>
          )}

          {activeTab === 'area' && (
            <div className="flex items-center gap-2">
              <span className="font-semibold text-slate-700">Ward Filter:</span>
              <input
                type="text"
                placeholder="Search ward name (e.g. Ward 08)..."
                value={wardFilter}
                onChange={(e) => setWardFilter(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && loadData()}
                className="border border-[#E2E7ED] rounded-lg px-2.5 py-1.5 bg-white text-slate-900 w-48 focus:outline-none focus:ring-2 focus:ring-[#137A70]"
              />
              <button
                type="button"
                onClick={loadData}
                className="px-2.5 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-lg font-semibold"
              >
                Filter
              </button>
            </div>
          )}

          {activeTab === 'monthly' && (
            <div className="flex items-center gap-2">
              <span className="font-semibold text-slate-700">Year:</span>
              <select
                value={yearFilter}
                onChange={(e) => setYearFilter(Number(e.target.value))}
                className="border border-[#E2E7ED] rounded-lg px-2.5 py-1.5 bg-white text-slate-900 focus:outline-none focus:ring-2 focus:ring-[#137A70]"
              >
                {[2026, 2025, 2024].map(y => (
                  <option key={y} value={y}>{y}</option>
                ))}
              </select>
            </div>
          )}

          {/* Date range inputs */}
          <form onSubmit={handleApplyDateFilter} className="flex flex-wrap items-center gap-2">
            <span className="font-semibold text-slate-700">From:</span>
            <input
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              className="border border-[#E2E7ED] rounded-lg px-2.5 py-1 bg-white text-slate-900 text-xs"
            />
            <span className="font-semibold text-slate-700">To:</span>
            <input
              type="date"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
              className="border border-[#E2E7ED] rounded-lg px-2.5 py-1 bg-white text-slate-900 text-xs"
            />
            <button
              type="submit"
              className="px-2.5 py-1 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-lg font-semibold transition-colors"
            >
              Apply Dates
            </button>
            {(startDate || endDate) && (
              <button
                type="button"
                onClick={() => { setStartDate(''); setEndDate(''); loadData(); }}
                className="text-xs text-rose-600 hover:underline font-semibold"
              >
                Clear
              </button>
            )}
          </form>
        </div>

        <button
          onClick={loadData}
          disabled={loading}
          className="p-1.5 border border-[#E2E7ED] rounded-lg text-slate-600 hover:text-slate-900 hover:bg-slate-50 transition-colors"
          title="Refresh Data"
        >
          <span className={`material-symbols-outlined text-[18px] ${loading ? 'animate-spin' : ''}`}>refresh</span>
        </button>
      </div>

      {error && (
        <div className="p-4 bg-red-50 border border-red-200 rounded-xl text-red-700 text-xs flex items-center gap-2">
          <span className="material-symbols-outlined text-lg">error</span>
          <span>{error}</span>
        </div>
      )}

      {/* Main Table Content */}
      <div className="bg-white rounded-xl border border-[#E2E7ED] shadow-sm overflow-hidden">
        {loading ? (
          <div className="p-16 text-center text-slate-500 flex flex-col items-center justify-center gap-3">
            <span className="material-symbols-outlined text-4xl animate-spin text-[#137A70]">progress_activity</span>
            <p className="text-sm font-medium">Computing server-side SQL aggregations across municipal records...</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            {activeTab === 'staff' && (
              <table className="w-full text-left text-sm text-[#18232F]">
                <thead className="bg-[#F5F7FA] border-b border-[#E2E7ED] text-xs font-semibold text-[#667384] uppercase tracking-wider">
                  <tr>
                    <th className="px-6 py-3.5">Staff Member</th>
                    <th className="px-6 py-3.5">Role</th>
                    <th className="px-6 py-3.5">Department</th>
                    <th className="px-6 py-3.5 text-center">Assigned</th>
                    <th className="px-6 py-3.5 text-center">Completed</th>
                    <th className="px-6 py-3.5 text-center">Pending</th>
                    <th className="px-6 py-3.5 text-center">Overdue</th>
                    <th className="px-6 py-3.5 text-center">Reworks</th>
                    <th className="px-6 py-3.5 text-right">Avg Resolution</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#E2E7ED]">
                  {staffData.length === 0 ? (
                    <tr>
                      <td colSpan="9" className="text-center py-10 text-xs text-slate-400">
                        No staff member activity recorded in this period.
                      </td>
                    </tr>
                  ) : (
                    staffData.map((item) => (
                      <tr key={item.staffId} className="hover:bg-slate-50/70 transition-colors">
                        <td className="px-6 py-4">
                          <div className="font-semibold text-slate-900">{item.fullName}</div>
                          <div className="text-xs text-slate-500">{item.email}</div>
                        </td>
                        <td className="px-6 py-4">
                          <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-slate-100 text-slate-700">
                            {item.role}
                          </span>
                        </td>
                        <td className="px-6 py-4 text-xs text-slate-600 font-medium">
                          {item.department}
                        </td>
                        <td className="px-6 py-4 text-center font-semibold text-slate-800">
                          {item.assignedCount}
                        </td>
                        <td className="px-6 py-4 text-center">
                          <span className="text-emerald-700 font-semibold">{item.completedCount}</span>
                        </td>
                        <td className="px-6 py-4 text-center">
                          <span className="text-blue-700 font-semibold">{item.pendingCount}</span>
                        </td>
                        <td className="px-6 py-4 text-center">
                          {item.overdueCount > 0 ? (
                            <span className="px-2 py-0.5 rounded-full bg-rose-100 text-rose-800 text-xs font-bold">
                              {item.overdueCount}
                            </span>
                          ) : (
                            <span className="text-slate-400 text-xs">0</span>
                          )}
                        </td>
                        <td className="px-6 py-4 text-center">
                          {item.reworkCount > 0 ? (
                            <span className="px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 text-xs font-bold">
                              {item.reworkCount}
                            </span>
                          ) : (
                            <span className="text-slate-400 text-xs">0</span>
                          )}
                        </td>
                        <td className="px-6 py-4 text-right font-mono text-xs text-slate-700">
                          {item.avgResolutionHours > 0 ? `${item.avgResolutionHours}h` : '—'}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            )}

            {activeTab === 'area' && (
              <table className="w-full text-left text-sm text-[#18232F]">
                <thead className="bg-[#F5F7FA] border-b border-[#E2E7ED] text-xs font-semibold text-[#667384] uppercase tracking-wider">
                  <tr>
                    <th className="px-6 py-3.5">District</th>
                    <th className="px-6 py-3.5">Ward</th>
                    <th className="px-6 py-3.5 text-center">Total Received</th>
                    <th className="px-6 py-3.5 text-center">Resolved</th>
                    <th className="px-6 py-3.5 text-center">In Flight</th>
                    <th className="px-6 py-3.5 text-center">Overdue</th>
                    <th className="px-6 py-3.5 text-right">Resolution Rate</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#E2E7ED]">
                  {areaData.length === 0 ? (
                    <tr>
                      <td colSpan="7" className="text-center py-10 text-xs text-slate-400">
                        No geographic issue records found matching filter.
                      </td>
                    </tr>
                  ) : (
                    areaData.map((item, idx) => (
                      <tr key={idx} className="hover:bg-slate-50/70 transition-colors">
                        <td className="px-6 py-4 text-xs font-semibold text-slate-700">
                          {item.district}
                        </td>
                        <td className="px-6 py-4 font-semibold text-slate-900">
                          {item.ward}
                        </td>
                        <td className="px-6 py-4 text-center font-bold text-slate-800">
                          {item.totalReceived}
                        </td>
                        <td className="px-6 py-4 text-center text-emerald-700 font-semibold">
                          {item.resolvedCount}
                        </td>
                        <td className="px-6 py-4 text-center text-blue-700 font-semibold">
                          {item.pendingCount}
                        </td>
                        <td className="px-6 py-4 text-center">
                          {item.overdueCount > 0 ? (
                            <span className="px-2 py-0.5 rounded-full bg-rose-100 text-rose-800 text-xs font-bold">
                              {item.overdueCount}
                            </span>
                          ) : (
                            <span className="text-slate-400 text-xs">0</span>
                          )}
                        </td>
                        <td className="px-6 py-4 text-right">
                          <span className={`px-2.5 py-1 rounded-full text-xs font-bold ${
                            item.resolutionRatePercent >= 80
                              ? 'bg-emerald-100 text-emerald-800'
                              : item.resolutionRatePercent >= 50
                              ? 'bg-amber-100 text-amber-800'
                              : 'bg-rose-100 text-rose-800'
                          }`}>
                            {item.resolutionRatePercent}%
                          </span>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            )}

            {activeTab === 'category' && (
              <table className="w-full text-left text-sm text-[#18232F]">
                <thead className="bg-[#F5F7FA] border-b border-[#E2E7ED] text-xs font-semibold text-[#667384] uppercase tracking-wider">
                  <tr>
                    <th className="px-6 py-3.5">Category</th>
                    <th className="px-6 py-3.5 text-center">Total Issues</th>
                    <th className="px-6 py-3.5 text-center">Resolved</th>
                    <th className="px-6 py-3.5 text-center">In Flight</th>
                    <th className="px-6 py-3.5 text-center">Overdue</th>
                    <th className="px-6 py-3.5 text-center">Avg Resolution Time</th>
                    <th className="px-6 py-3.5 text-right">SLA Breach Rate</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#E2E7ED]">
                  {categoryData.length === 0 ? (
                    <tr>
                      <td colSpan="7" className="text-center py-10 text-xs text-slate-400">
                        No categorized issues found for the given timeframe.
                      </td>
                    </tr>
                  ) : (
                    categoryData.map((item, idx) => (
                      <tr key={idx} className="hover:bg-slate-50/70 transition-colors">
                        <td className="px-6 py-4 font-semibold text-slate-900">
                          {item.category}
                        </td>
                        <td className="px-6 py-4 text-center font-bold text-slate-800">
                          {item.totalReceived}
                        </td>
                        <td className="px-6 py-4 text-center text-emerald-700 font-semibold">
                          {item.resolvedCount}
                        </td>
                        <td className="px-6 py-4 text-center text-blue-700 font-semibold">
                          {item.pendingCount}
                        </td>
                        <td className="px-6 py-4 text-center">
                          {item.overdueCount > 0 ? (
                            <span className="px-2 py-0.5 rounded-full bg-rose-100 text-rose-800 text-xs font-bold">
                              {item.overdueCount}
                            </span>
                          ) : (
                            <span className="text-slate-400 text-xs">0</span>
                          )}
                        </td>
                        <td className="px-6 py-4 text-center font-mono text-xs text-slate-700">
                          {item.avgResolutionHours > 0 ? `${item.avgResolutionHours}h` : '—'}
                        </td>
                        <td className="px-6 py-4 text-right">
                          <span className={`px-2.5 py-1 rounded-full text-xs font-bold ${
                            item.slaBreachRatePercent <= 10
                              ? 'bg-emerald-100 text-emerald-800'
                              : item.slaBreachRatePercent <= 25
                              ? 'bg-amber-100 text-amber-800'
                              : 'bg-rose-100 text-rose-800'
                          }`}>
                            {item.slaBreachRatePercent}%
                          </span>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            )}

            {activeTab === 'monthly' && (
              <table className="w-full text-left text-sm text-[#18232F]">
                <thead className="bg-[#F5F7FA] border-b border-[#E2E7ED] text-xs font-semibold text-[#667384] uppercase tracking-wider">
                  <tr>
                    <th className="px-6 py-3.5">Month</th>
                    <th className="px-6 py-3.5 text-center">Total Received</th>
                    <th className="px-6 py-3.5 text-center">Resolved</th>
                    <th className="px-6 py-3.5 text-center">Pending</th>
                    <th className="px-6 py-3.5 text-center">Overdue</th>
                    <th className="px-6 py-3.5 text-center">Resolution Rate</th>
                    <th className="px-6 py-3.5 text-right">SLA Compliance</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#E2E7ED]">
                  {monthlyData.length === 0 ? (
                    <tr>
                      <td colSpan="7" className="text-center py-10 text-xs text-slate-400">
                        No monthly trend data recorded for year {yearFilter}.
                      </td>
                    </tr>
                  ) : (
                    monthlyData.map((item, idx) => (
                      <tr key={idx} className="hover:bg-slate-50/70 transition-colors">
                        <td className="px-6 py-4 font-semibold text-slate-900 font-mono">
                          {item.yearMonth}
                        </td>
                        <td className="px-6 py-4 text-center font-bold text-slate-800">
                          {item.receivedCount}
                        </td>
                        <td className="px-6 py-4 text-center text-emerald-700 font-semibold">
                          {item.resolvedCount}
                        </td>
                        <td className="px-6 py-4 text-center text-blue-700 font-semibold">
                          {item.pendingCount}
                        </td>
                        <td className="px-6 py-4 text-center">
                          {item.overdueCount > 0 ? (
                            <span className="px-2 py-0.5 rounded-full bg-rose-100 text-rose-800 text-xs font-bold">
                              {item.overdueCount}
                            </span>
                          ) : (
                            <span className="text-slate-400 text-xs">0</span>
                          )}
                        </td>
                        <td className="px-6 py-4 text-center font-semibold text-slate-700">
                          {item.resolutionRatePercent}%
                        </td>
                        <td className="px-6 py-4 text-right">
                          <span className={`px-2.5 py-1 rounded-full text-xs font-bold ${
                            item.slaCompliancePercent >= 80
                              ? 'bg-emerald-100 text-emerald-800'
                              : item.slaCompliancePercent >= 50
                              ? 'bg-amber-100 text-amber-800'
                              : 'bg-rose-100 text-rose-800'
                          }`}>
                            {item.slaCompliancePercent}%
                          </span>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            )}

            {activeTab === 'yearly' && (
              <table className="w-full text-left text-sm text-[#18232F]">
                <thead className="bg-[#F5F7FA] border-b border-[#E2E7ED] text-xs font-semibold text-[#667384] uppercase tracking-wider">
                  <tr>
                    <th className="px-6 py-3.5">Year</th>
                    <th className="px-6 py-3.5 text-center">Total Received</th>
                    <th className="px-6 py-3.5 text-center">Resolved</th>
                    <th className="px-6 py-3.5 text-center">Pending</th>
                    <th className="px-6 py-3.5 text-center">Overdue</th>
                    <th className="px-6 py-3.5 text-center">Resolution Rate</th>
                    <th className="px-6 py-3.5 text-right">SLA Compliance</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#E2E7ED]">
                  {yearlyData.length === 0 ? (
                    <tr>
                      <td colSpan="7" className="text-center py-10 text-xs text-slate-400">
                        No historical yearly data available.
                      </td>
                    </tr>
                  ) : (
                    yearlyData.map((item, idx) => (
                      <tr key={idx} className="hover:bg-slate-50/70 transition-colors">
                        <td className="px-6 py-4 font-bold text-slate-900 font-mono text-base">
                          {item.year}
                        </td>
                        <td className="px-6 py-4 text-center font-bold text-slate-800">
                          {item.receivedCount}
                        </td>
                        <td className="px-6 py-4 text-center text-emerald-700 font-semibold">
                          {item.resolvedCount}
                        </td>
                        <td className="px-6 py-4 text-center text-blue-700 font-semibold">
                          {item.pendingCount}
                        </td>
                        <td className="px-6 py-4 text-center">
                          {item.overdueCount > 0 ? (
                            <span className="px-2 py-0.5 rounded-full bg-rose-100 text-rose-800 text-xs font-bold">
                              {item.overdueCount}
                            </span>
                          ) : (
                            <span className="text-slate-400 text-xs">0</span>
                          )}
                        </td>
                        <td className="px-6 py-4 text-center font-semibold text-slate-700">
                          {item.resolutionRatePercent}%
                        </td>
                        <td className="px-6 py-4 text-right">
                          <span className={`px-2.5 py-1 rounded-full text-xs font-bold ${
                            item.slaCompliancePercent >= 80
                              ? 'bg-emerald-100 text-emerald-800'
                              : item.slaCompliancePercent >= 50
                              ? 'bg-amber-100 text-amber-800'
                              : 'bg-rose-100 text-rose-800'
                          }`}>
                            {item.slaCompliancePercent}%
                          </span>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
