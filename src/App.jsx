import React, { useState, Suspense, lazy } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useApp } from './context/AppContext';
import { useTranslation } from './context/LanguageContext';
import { authService } from './services/authService';
import DemoRoleSwitcher from './components/common/DemoRoleSwitcher';
import OfflineScreen from './components/common/OfflineScreen';
import ReloadPrompt from './components/common/ReloadPrompt';
import SkeletonLoader from './components/common/SkeletonLoader';

// Layouts
import { PublicLayout } from './components/layout/PublicLayout';
import { CitizenLayout } from './components/layout/CitizenLayout';
import { OfficerLayout } from './components/layout/OfficerLayout';
import { WorkerLayout } from './components/layout/WorkerLayout';
import { AdminLayout } from './components/layout/AdminLayout';
import { SupervisorLayout } from './components/layout/SupervisorLayout';

// Public Pages (Route Code-Split)
const LandingPage = lazy(() => import('./pages/public/LandingPage').then(m => ({ default: m.LandingPage })));
const LoginPage = lazy(() => import('./pages/public/LoginPage').then(m => ({ default: m.LoginPage })));
const RegisterPage = lazy(() => import('./pages/public/RegisterPage').then(m => ({ default: m.RegisterPage })));
const ForgotPasswordPage = lazy(() => import('./pages/public/ForgotPasswordPage').then(m => ({ default: m.ForgotPasswordPage })));
const ResetPasswordPage = lazy(() => import('./pages/public/ResetPasswordPage').then(m => ({ default: m.ResetPasswordPage })));
const VerifyEmailPage = lazy(() => import('./pages/public/VerifyEmailPage'));
const StaffActivationPage = lazy(() => import('./pages/public/StaffActivationPage'));
const PublicMapPage = lazy(() => import('./pages/public/PublicMapPage').then(m => ({ default: m.PublicMapPage })));
const TermsOfServicePage = lazy(() => import('./pages/public/TermsOfServicePage').then(m => ({ default: m.TermsOfServicePage })));
const PrivacyPolicyPage = lazy(() => import('./pages/public/PrivacyPolicyPage').then(m => ({ default: m.PrivacyPolicyPage })));

// Citizen Pages (Route Code-Split)
const CitizenDashboard = lazy(() => import('./pages/citizen/CitizenDashboard').then(m => ({ default: m.CitizenDashboard })));
const ReportComplaintPage = lazy(() => import('./pages/citizen/ReportComplaintPage').then(m => ({ default: m.ReportComplaintPage })));
const MyComplaintsPage = lazy(() => import('./pages/citizen/MyComplaintsPage').then(m => ({ default: m.MyComplaintsPage })));
const ComplaintDetailsPage = lazy(() => import('./pages/citizen/ComplaintDetailsPage').then(m => ({ default: m.ComplaintDetailsPage })));
const CitizenIssueMapPage = lazy(() => import('./pages/citizen/CitizenIssueMapPage').then(m => ({ default: m.CitizenIssueMapPage })));
const CitizenNotificationsPage = lazy(() => import('./pages/citizen/CitizenNotificationsPage').then(m => ({ default: m.CitizenNotificationsPage })));
const CitizenProfilePage = lazy(() => import('./pages/citizen/CitizenProfilePage').then(m => ({ default: m.CitizenProfilePage })));

// Municipal Officer Pages (Route Code-Split)
const OfficerDashboard = lazy(() => import('./pages/officer/OfficerDashboard').then(m => ({ default: m.OfficerDashboard })));
const ComplaintQueuePage = lazy(() => import('./pages/officer/ComplaintQueuePage').then(m => ({ default: m.ComplaintQueuePage })));
const ComplaintReviewPage = lazy(() => import('./pages/officer/ComplaintReviewPage').then(m => ({ default: m.ComplaintReviewPage })));
const AssignmentsPage = lazy(() => import('./pages/officer/AssignmentsPage').then(m => ({ default: m.AssignmentsPage })));
const VerificationQueuePage = lazy(() => import('./pages/officer/VerificationQueuePage').then(m => ({ default: m.VerificationQueuePage })));
const ResolutionVerificationPage = lazy(() => import('./pages/officer/ResolutionVerificationPage').then(m => ({ default: m.ResolutionVerificationPage })));
const CivicIssueMapPage = lazy(() => import('./pages/officer/CivicIssueMapPage').then(m => ({ default: m.CivicIssueMapPage })));
const OfficerAnalyticsPage = lazy(() => import('./pages/officer/OfficerAnalyticsPage').then(m => ({ default: m.OfficerAnalyticsPage })));
const OfficerNotificationsPage = lazy(() => import('./pages/officer/OfficerNotificationsPage').then(m => ({ default: m.OfficerNotificationsPage })));
const OfficerProfilePage = lazy(() => import('./pages/officer/OfficerProfilePage').then(m => ({ default: m.OfficerProfilePage })));

// Field Worker Pages (Route Code-Split)
const WorkerDashboard = lazy(() => import('./pages/worker/WorkerDashboard').then(m => ({ default: m.WorkerDashboard })));
const AssignedTasksPage = lazy(() => import('./pages/worker/AssignedTasksPage').then(m => ({ default: m.AssignedTasksPage })));
const TaskDetailsPage = lazy(() => import('./pages/worker/TaskDetailsPage').then(m => ({ default: m.TaskDetailsPage })));
const TaskProgressPage = lazy(() => import('./pages/worker/TaskProgressPage').then(m => ({ default: m.TaskProgressPage })));
const SubmitEvidencePage = lazy(() => import('./pages/worker/SubmitEvidencePage').then(m => ({ default: m.SubmitEvidencePage })));
const WorkerNotificationsPage = lazy(() => import('./pages/worker/WorkerNotificationsPage').then(m => ({ default: m.WorkerNotificationsPage })));
const WorkerProfilePage = lazy(() => import('./pages/worker/WorkerProfilePage').then(m => ({ default: m.WorkerProfilePage })));

// Administrator Pages (Route Code-Split)
const AdminDashboard = lazy(() => import('./pages/admin/AdminDashboard').then(m => ({ default: m.AdminDashboard })));
const UserManagementPage = lazy(() => import('./pages/admin/UserManagementPage'));
const UserDetailsPage = lazy(() => import('./pages/admin/UserDetailsPage'));
const CreateStaffPage = lazy(() => import('./pages/admin/CreateStaffPage'));
const RolesPermissionsPage = lazy(() => import('./pages/admin/RolesPermissionsPage'));
const PlatformActivityPage = lazy(() => import('./pages/admin/PlatformActivityPage'));
const AuditLogsPage = lazy(() => import('./pages/admin/AuditLogsPage'));
const SystemSettingsPage = lazy(() => import('./pages/admin/SystemSettingsPage'));
const AdminNotificationsPage = lazy(() => import('./pages/admin/AdminNotificationsPage'));
const AdminProfilePage = lazy(() => import('./pages/admin/AdminProfilePage'));
const AdminAnalyticsPage = lazy(() => import('./pages/admin/AdminAnalyticsPage'));

// Supervisor Pages (Route Code-Split)
const SupervisorEscalationsPage = lazy(() => import('./pages/supervisor/SupervisorEscalationsPage').then(m => ({ default: m.SupervisorEscalationsPage })));

// Legal & Public Utilities
import { RoleRoute } from './components/common/RoleRoute';

// 404
const NotFoundPage = lazy(() => import('./pages/NotFoundPage'));

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
      <Suspense fallback={
        <div className="min-h-screen bg-surface p-6 sm:p-10 flex items-center justify-center">
          <div className="w-full max-w-4xl space-y-6">
            <SkeletonLoader type="card" count={3} />
          </div>
        </div>
      }>
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
          <Route path="/verify-email" element={<VerifyEmailPage />} />
          <Route path="/activate-staff" element={<StaffActivationPage />} />
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
          <Route path="analytics" element={<AdminAnalyticsPage />} />
          <Route path="audit-logs" element={<AuditLogsPage />} />
          <Route path="settings" element={<SystemSettingsPage />} />
          <Route path="notifications" element={<AdminNotificationsPage />} />
          <Route path="profile" element={<AdminProfilePage />} />
        </Route>

        {/* Catch-all 404 Route */}
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
      </Suspense>

      {/* PWA Update Prompt and Offline Protection */}
      <ReloadPrompt />
      <OfflineScreen />

      {/* Floating Interactive Persona Role Switcher for seamless capstone presentation */}
      <DemoRoleSwitcher />
    </>
  );
}

export default App;
