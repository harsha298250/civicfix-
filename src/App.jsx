import React, { useState } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useApp } from './context/AppContext';
import { useTranslation } from './context/LanguageContext';
import { authService } from './services/authService';
import DemoRoleSwitcher from './components/common/DemoRoleSwitcher';
import OfflineScreen from './components/common/OfflineScreen';
import ReloadPrompt from './components/common/ReloadPrompt';

// Layouts
import { PublicLayout } from './components/layout/PublicLayout';
import { CitizenLayout } from './components/layout/CitizenLayout';
import { OfficerLayout } from './components/layout/OfficerLayout';
import { WorkerLayout } from './components/layout/WorkerLayout';
import { AdminLayout } from './components/layout/AdminLayout';
import { SupervisorLayout } from './components/layout/SupervisorLayout';

// Public Pages
import { LandingPage } from './pages/public/LandingPage';
import { LoginPage } from './pages/public/LoginPage';
import { RegisterPage } from './pages/public/RegisterPage';
import { ForgotPasswordPage } from './pages/public/ForgotPasswordPage';
import { ResetPasswordPage } from './pages/public/ResetPasswordPage';

// Citizen Pages
import { CitizenDashboard } from './pages/citizen/CitizenDashboard';
import { ReportComplaintPage } from './pages/citizen/ReportComplaintPage';
import { MyComplaintsPage } from './pages/citizen/MyComplaintsPage';
import { ComplaintDetailsPage } from './pages/citizen/ComplaintDetailsPage';
import { CitizenIssueMapPage } from './pages/citizen/CitizenIssueMapPage';
import { CitizenNotificationsPage } from './pages/citizen/CitizenNotificationsPage';
import { CitizenProfilePage } from './pages/citizen/CitizenProfilePage';

// Municipal Officer Pages
import { OfficerDashboard } from './pages/officer/OfficerDashboard';
import { ComplaintQueuePage } from './pages/officer/ComplaintQueuePage';
import { ComplaintReviewPage } from './pages/officer/ComplaintReviewPage';
import { AssignmentsPage } from './pages/officer/AssignmentsPage';
import { VerificationQueuePage } from './pages/officer/VerificationQueuePage';
import { ResolutionVerificationPage } from './pages/officer/ResolutionVerificationPage';
import { CivicIssueMapPage } from './pages/officer/CivicIssueMapPage';
import { OfficerAnalyticsPage } from './pages/officer/OfficerAnalyticsPage';
import { OfficerNotificationsPage } from './pages/officer/OfficerNotificationsPage';
import { OfficerProfilePage } from './pages/officer/OfficerProfilePage';

// Field Worker Pages
import { WorkerDashboard } from './pages/worker/WorkerDashboard';
import { AssignedTasksPage } from './pages/worker/AssignedTasksPage';
import { TaskDetailsPage } from './pages/worker/TaskDetailsPage';
import { TaskProgressPage } from './pages/worker/TaskProgressPage';
import { SubmitEvidencePage } from './pages/worker/SubmitEvidencePage';
import { WorkerNotificationsPage } from './pages/worker/WorkerNotificationsPage';
import { WorkerProfilePage } from './pages/worker/WorkerProfilePage';

// Administrator Pages
import { AdminDashboard } from './pages/admin/AdminDashboard';
import UserManagementPage from './pages/admin/UserManagementPage';
import UserDetailsPage from './pages/admin/UserDetailsPage';
import CreateStaffPage from './pages/admin/CreateStaffPage';
import RolesPermissionsPage from './pages/admin/RolesPermissionsPage';
import PlatformActivityPage from './pages/admin/PlatformActivityPage';
import AuditLogsPage from './pages/admin/AuditLogsPage';
import SystemSettingsPage from './pages/admin/SystemSettingsPage';
import AdminNotificationsPage from './pages/admin/AdminNotificationsPage';
import AdminProfilePage from './pages/admin/AdminProfilePage';

// Supervisor Pages
import { SupervisorEscalationsPage } from './pages/supervisor/SupervisorEscalationsPage';

// Legal & Public Utilities
import { TermsOfServicePage } from './pages/public/TermsOfServicePage';
import { PrivacyPolicyPage } from './pages/public/PrivacyPolicyPage';
import { PublicMapPage } from './pages/public/PublicMapPage';
import { RoleRoute } from './components/common/RoleRoute';

// 404
import NotFoundPage from './pages/NotFoundPage';

function App() {
  const { serverUnreachable, setServerUnreachable, refreshComplaints, setCurrentUser } = useApp();
  const { t } = useTranslation();
  const [retrying, setRetrying] = useState(false);

  const handleRetryConnection = async () => {
    setRetrying(true);
    try {
      const liveUser = await authService.checkSession();
      setServerUnreachable(false);
      if (liveUser) setCurrentUser(liveUser);
      await refreshComplaints();
    } catch {
      setServerUnreachable(true);
    } finally {
      setRetrying(false);
    }
  };

  return (
    <>
      {serverUnreachable && (
        <div
          role="alert"
          aria-live="assertive"
          className="bg-[#C63F48] text-white px-4 py-2.5 text-center text-xs sm:text-sm font-semibold flex flex-wrap items-center justify-center gap-3 sticky top-0 z-50 shadow-md"
        >
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-[18px]">cloud_off</span>
            <span>{t('accessibility.offlineNotice')}</span>
          </div>
          <button
            type="button"
            onClick={handleRetryConnection}
            disabled={retrying}
            className="px-3 py-1 bg-white/20 hover:bg-white/30 text-white text-xs font-semibold rounded-lg transition-colors flex items-center gap-1.5 focus:outline-none focus-visible:ring-2 focus-visible:ring-white disabled:opacity-50"
          >
            <span className={`material-symbols-outlined text-[14px] ${retrying ? 'animate-spin' : ''}`}>
              refresh
            </span>
            <span>{retrying ? t('common.loading') : t('accessibility.retryConnection')}</span>
          </button>
        </div>
      )}
      <Routes>
        {/* Public Routes */}
        <Route element={<PublicLayout />}>
          <Route path="/" element={<LandingPage />} />
          <Route path="/map" element={<PublicMapPage />} />
          <Route path="/legal/terms" element={<TermsOfServicePage />} />
          <Route path="/legal/privacy" element={<PrivacyPolicyPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
        </Route>

        {/* Citizen Routes — Protected against unauthorized role traversal */}
        <Route
          path="/citizen"
          element={
            <RoleRoute allowedRoles={['CITIZEN', 'ADMIN']}>
              <CitizenLayout />
            </RoleRoute>
          }
        >
          <Route index element={<Navigate to="/citizen/dashboard" replace />} />
          <Route path="dashboard" element={<CitizenDashboard />} />
          <Route path="complaints" element={<MyComplaintsPage />} />
          <Route path="complaints/new" element={<ReportComplaintPage />} />
          <Route path="complaints/:complaintId" element={<ComplaintDetailsPage />} />
          {/* Phase 3: Citizen community map browsing enabled via sanitized public feed */}
          <Route path="map" element={<CitizenIssueMapPage />} />
          <Route path="notifications" element={<CitizenNotificationsPage />} />
          <Route path="profile" element={<CitizenProfilePage />} />
        </Route>

        {/* Municipal Officer Routes — Protected against citizen access */}
        <Route
          path="/officer"
          element={
            <RoleRoute allowedRoles={['OFFICER', 'ADMIN']}>
              <OfficerLayout />
            </RoleRoute>
          }
        >
          <Route index element={<Navigate to="/officer/dashboard" replace />} />
          <Route path="dashboard" element={<OfficerDashboard />} />
          <Route path="complaints" element={<ComplaintQueuePage />} />
          <Route path="complaints/:complaintId/review" element={<ComplaintReviewPage />} />
          <Route path="assignments" element={<AssignmentsPage />} />
          <Route path="verifications" element={<VerificationQueuePage />} />
          <Route path="verifications/:complaintId" element={<ResolutionVerificationPage />} />
          <Route path="map" element={<CivicIssueMapPage />} />
          <Route path="analytics" element={<OfficerAnalyticsPage />} />
          <Route path="notifications" element={<OfficerNotificationsPage />} />
          <Route path="profile" element={<OfficerProfilePage />} />
        </Route>

        {/* Field Worker Routes — Protected against citizen/unauthorized access */}
        <Route
          path="/worker"
          element={
            <RoleRoute allowedRoles={['WORKER', 'ADMIN']}>
              <WorkerLayout />
            </RoleRoute>
          }
        >
          <Route index element={<Navigate to="/worker/dashboard" replace />} />
          <Route path="dashboard" element={<WorkerDashboard />} />
          <Route path="tasks" element={<AssignedTasksPage />} />
          <Route path="tasks/:taskId" element={<TaskDetailsPage />} />
          <Route path="tasks/:taskId/progress" element={<TaskProgressPage />} />
          <Route path="tasks/:taskId/evidence" element={<SubmitEvidencePage />} />
          <Route path="notifications" element={<WorkerNotificationsPage />} />
          <Route path="profile" element={<WorkerProfilePage />} />
        </Route>

        {/* Supervisor Routes — Protected for SLA & Escalation Management */}
        <Route
          path="/supervisor"
          element={
            <RoleRoute allowedRoles={['SUPERVISOR', 'ADMIN']}>
              <SupervisorLayout />
            </RoleRoute>
          }
        >
          <Route index element={<Navigate to="/supervisor/escalations" replace />} />
          <Route path="escalations" element={<SupervisorEscalationsPage />} />
        </Route>

        {/* Administrator Routes — Protected for Tier-0 platform governance */}
        <Route
          path="/admin"
          element={
            <RoleRoute allowedRoles={['ADMIN']}>
              <AdminLayout />
            </RoleRoute>
          }
        >
          <Route index element={<Navigate to="/admin/dashboard" replace />} />
          <Route path="dashboard" element={<AdminDashboard />} />
          <Route path="users" element={<UserManagementPage />} />
          <Route path="users/:userId" element={<UserDetailsPage />} />
          <Route path="users/new" element={<CreateStaffPage />} />
          <Route path="roles" element={<RolesPermissionsPage />} />
          <Route path="activity" element={<PlatformActivityPage />} />
          <Route path="audit-logs" element={<AuditLogsPage />} />
          <Route path="settings" element={<SystemSettingsPage />} />
          <Route path="notifications" element={<AdminNotificationsPage />} />
          <Route path="profile" element={<AdminProfilePage />} />
        </Route>

        {/* Catch-all 404 Route */}
        <Route path="*" element={<NotFoundPage />} />
      </Routes>

      {/* PWA Update Prompt and Offline Protection */}
      <ReloadPrompt />
      <OfflineScreen />

      {/* Floating Interactive Persona Role Switcher for seamless capstone presentation */}
      <DemoRoleSwitcher />
    </>
  );
}

export default App;
