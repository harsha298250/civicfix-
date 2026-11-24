# CivicFix — Final Git Staging Safety Audit & Checklist

**Project:** CivicFix — Community Issue Reporting & Resolution Platform  
**Date:** November 24, 2026  
**Auditor:** Senior Java Spring Boot Architect & Application Security Engineer  
**Mode:** READ-ONLY Git Inventory (No files modified, zero Git state altered)  
**Total Tracked Modified Files:** 68  
**Total Untracked Files:** 140  

---

## 1. Raw Git Command Outputs

### 1.1 `git status --short`
```
M backend/pom.xml
 M backend/src/main/java/com/civicfix/admin/AdminService.java
 M backend/src/main/java/com/civicfix/admin/dto/AdminUserResponse.java
 M backend/src/main/java/com/civicfix/admin/dto/CreateStaffRequest.java
 M backend/src/main/java/com/civicfix/ai/AiTriageService.java
 M backend/src/main/java/com/civicfix/ai/dto/SuggestCategoryResponse.java
 M backend/src/main/java/com/civicfix/ai/vision/VisionTriageProperties.java
 M backend/src/main/java/com/civicfix/auth/AuthController.java
 M backend/src/main/java/com/civicfix/auth/AuthService.java
 M backend/src/main/java/com/civicfix/common/exception/GlobalExceptionHandler.java
 M backend/src/main/java/com/civicfix/complaint/Complaint.java
 M backend/src/main/java/com/civicfix/complaint/ComplaintController.java
 M backend/src/main/java/com/civicfix/complaint/ComplaintRepository.java
 M backend/src/main/java/com/civicfix/complaint/ComplaintService.java
 M backend/src/main/java/com/civicfix/complaint/dto/ComplaintResponse.java
 M backend/src/main/java/com/civicfix/complaint/sla/SlaEscalationScheduler.java
 M backend/src/main/java/com/civicfix/complaint/sla/SlaEscalationService.java
 M backend/src/main/java/com/civicfix/config/SecurityConfig.java
 M backend/src/main/java/com/civicfix/gis/GisService.java
 M backend/src/main/java/com/civicfix/mail/ResendEmailService.java
 M backend/src/main/java/com/civicfix/notification/NotificationController.java
 M backend/src/main/java/com/civicfix/notification/NotificationService.java
 M backend/src/main/java/com/civicfix/notification/NotificationType.java
 M backend/src/main/java/com/civicfix/security/ratelimit/AccountLoginAttemptRepository.java
 M backend/src/main/java/com/civicfix/security/ratelimit/IpLoginAttemptRepository.java
 M backend/src/main/java/com/civicfix/security/ratelimit/LoginAttemptRepository.java
 M backend/src/main/java/com/civicfix/security/ratelimit/LoginLockoutService.java
 M backend/src/main/java/com/civicfix/supervisor/SupervisorService.java
 M backend/src/main/java/com/civicfix/supervisor/dto/SupervisorWorkerResponse.java
 M backend/src/main/java/com/civicfix/user/User.java
 M backend/src/main/java/com/civicfix/user/UserRepository.java
 M backend/src/main/resources/application-prod.yml
 M backend/src/main/resources/application-test.yml
 M backend/src/main/resources/application.yml
 M backend/src/test/java/com/civicfix/ai/AiTriageServiceTest.java
 M backend/src/test/java/com/civicfix/notification/NotificationEventIntegrationTest.java
 M backend/src/test/java/com/civicfix/notification/NotificationPersistenceTest.java
 M backend/src/test/java/com/civicfix/supervisor/SupervisorServiceTest.java
 M docs/CIVICFIX_MASTER_ROADMAP.md
 M docs/DEPLOYMENT_CHECKLIST.md
 M docs/LIMITATIONS_AND_FUTURE_WORK.md
 M docs/evaluation/results.md
 M package-lock.json
 M package.json
 M src/App.jsx
 M src/components/common/TamilNaduLocationPicker.jsx
 M src/components/common/TamilNaduOverviewMap.jsx
 M src/components/layout/AdminLayout.jsx
 M src/main.jsx
 M src/pages/admin/CreateStaffPage.jsx
 M src/pages/admin/UserManagementPage.jsx
 M src/pages/citizen/CitizenIssueMapPage.jsx
 M src/pages/citizen/CitizenNotificationsPage.jsx
 M src/pages/citizen/ReportComplaintPage.jsx
 M src/pages/officer/AssignmentsPage.jsx
 M src/pages/officer/CivicIssueMapPage.jsx
 M src/pages/officer/ComplaintReviewPage.jsx
 M src/pages/officer/ResolutionVerificationPage.jsx
 M src/pages/public/LoginPage.jsx
 M src/pages/public/RegisterPage.jsx
 M src/pages/supervisor/SupervisorEscalationsPage.jsx
 M src/services/apiClient.js
 M src/services/authService.js
 M src/services/complaintService.js
 M tests/complaintService.test.js
 M tests/notificationService.test.js
 M vercel.json
 M vite.config.js
?? CIVICFIX_FINAL_DEPLOYMENT_REPORT.md
?? CIVICFIX_PHASE_1_AUDIT.md
?? CIVICFIX_RENDER_MAVEN_RECOVERY_REPORT.md
?? Dockerfile.backup
?? _backups_phase2_0/
?? _backups_phase2_1/
?? _backups_phase2_1_1/
?? backend/src/main/java/com/civicfix/admin/AdminAnalyticsController.java
?? backend/src/main/java/com/civicfix/admin/AdminAnalyticsService.java
?? backend/src/main/java/com/civicfix/admin/dto/StaffWorkloadSummary.java
?? backend/src/main/java/com/civicfix/admin/dto/analytics/
?? backend/src/main/java/com/civicfix/ai/summary/
?? backend/src/main/java/com/civicfix/ai/vision/pothole/
?? backend/src/main/java/com/civicfix/auth/StaffActivationService.java
?? backend/src/main/java/com/civicfix/auth/StaffActivationToken.java
?? backend/src/main/java/com/civicfix/auth/StaffActivationTokenRepository.java
?? backend/src/main/java/com/civicfix/auth/dto/ActivateStaffRequest.java
?? backend/src/main/java/com/civicfix/auth/dto/FirebaseSessionRequest.java
?? backend/src/main/java/com/civicfix/auth/dto/VerifyActivationTokenResponse.java
?? backend/src/main/java/com/civicfix/auth/firebase/
?? backend/src/main/java/com/civicfix/complaint/dto/NearbyComplaintResponse.java
?? backend/src/main/java/com/civicfix/config/FirebaseConfig.java
?? backend/src/main/java/com/civicfix/config/FirebaseProperties.java
?? backend/src/main/java/com/civicfix/config/filter/
?? backend/src/main/java/com/civicfix/mail/AesGcmEncryptionHelper.java
?? backend/src/main/java/com/civicfix/mail/EmailOutbox.java
?? backend/src/main/java/com/civicfix/mail/EmailOutboxRepository.java
?? backend/src/main/java/com/civicfix/mail/EmailOutboxScheduler.java
?? backend/src/main/java/com/civicfix/mail/EmailOutboxService.java
?? backend/src/main/java/com/civicfix/mail/ResendWebhookController.java
?? backend/src/main/java/com/civicfix/storage/FileTransactionHelper.java
?? backend/src/main/resources/db/migration/V21__create_email_outbox_table.sql
?? backend/src/main/resources/db/migration/V22__create_pothole_detections_table.sql
?? backend/src/main/resources/db/migration/V23__create_staff_activation_tokens_table.sql
?? backend/src/main/resources/db/migration/V24__add_firebase_uid_to_users.sql
?? backend/src/main/resources/db/migration/V25__add_performance_and_created_at_indexes.sql
?? backend/src/test/java/com/civicfix/admin/AdminAnalyticsServiceTest.java
?? backend/src/test/java/com/civicfix/ai/summary/
?? backend/src/test/java/com/civicfix/ai/vision/pothole/
?? backend/src/test/java/com/civicfix/auth/StaffActivationIntegrationTest.java
?? backend/src/test/java/com/civicfix/auth/firebase/
?? backend/src/test/java/com/civicfix/complaint/AssignmentHardeningAndWorkloadTest.java
?? backend/src/test/java/com/civicfix/complaint/ComplaintSummaryEndpointIntegrationTest.java
?? backend/src/test/java/com/civicfix/config/
?? backend/src/test/java/com/civicfix/gis/NearbyComplaintTest.java
?? backend/src/test/java/com/civicfix/mail/AesGcmEncryptionHelperTest.java
?? backend/src/test/java/com/civicfix/mail/EmailOutboxServiceTest.java
?? backend/src/test/java/com/civicfix/mail/ResendWebhookControllerTest.java
?? backend/src/test/java/com/civicfix/storage/FileTransactionHelperTest.java
?? docs/CIVICFIX_FINAL_IMPLEMENTATION_READINESS.md
?? docs/CIVICFIX_PHASE_8_FINAL_VERIFICATION_REPORT.md
?? docs/CIVICFIX_PHASE_8_PRODUCTION_READINESS_REPORT.md
?? docs/CIVICFIX_PRE_DEPLOYMENT_FINAL_REVIEW.md
?? docs/CIVICFIX_PRE_GIT_PUSH_CONSISTENCY_REPORT.md
?? src/components/common/ErrorBoundary.jsx
?? src/components/common/PotholeDetectionOverlay.jsx
?? src/constants/tamilNaduDistricts.js
?? src/pages/admin/AdminAnalyticsPage.jsx
?? src/pages/public/StaffActivationPage.jsx
?? src/services/firebaseAuth.js
?? src/services/firebaseConfig.js
?? src/utils/gisClustering.js
?? src/utils/gisHeatmap.js
?? tests/errorBoundary.test.js
?? tests/firebaseAuth.test.js
?? tests/gisEnhancements.test.js
?? tests/phase6StaffAdmin.test.js
?? tests/potholeDetection.test.js
```

### 1.2 `git diff --name-only`
```
backend/pom.xml
backend/src/main/java/com/civicfix/admin/AdminService.java
backend/src/main/java/com/civicfix/admin/dto/AdminUserResponse.java
backend/src/main/java/com/civicfix/admin/dto/CreateStaffRequest.java
backend/src/main/java/com/civicfix/ai/AiTriageService.java
backend/src/main/java/com/civicfix/ai/dto/SuggestCategoryResponse.java
backend/src/main/java/com/civicfix/ai/vision/VisionTriageProperties.java
backend/src/main/java/com/civicfix/auth/AuthController.java
backend/src/main/java/com/civicfix/auth/AuthService.java
backend/src/main/java/com/civicfix/common/exception/GlobalExceptionHandler.java
backend/src/main/java/com/civicfix/complaint/Complaint.java
backend/src/main/java/com/civicfix/complaint/ComplaintController.java
backend/src/main/java/com/civicfix/complaint/ComplaintRepository.java
backend/src/main/java/com/civicfix/complaint/ComplaintService.java
backend/src/main/java/com/civicfix/complaint/dto/ComplaintResponse.java
backend/src/main/java/com/civicfix/complaint/sla/SlaEscalationScheduler.java
backend/src/main/java/com/civicfix/complaint/sla/SlaEscalationService.java
backend/src/main/java/com/civicfix/config/SecurityConfig.java
backend/src/main/java/com/civicfix/gis/GisService.java
backend/src/main/java/com/civicfix/mail/ResendEmailService.java
backend/src/main/java/com/civicfix/notification/NotificationController.java
backend/src/main/java/com/civicfix/notification/NotificationService.java
backend/src/main/java/com/civicfix/notification/NotificationType.java
backend/src/main/java/com/civicfix/security/ratelimit/AccountLoginAttemptRepository.java
backend/src/main/java/com/civicfix/security/ratelimit/IpLoginAttemptRepository.java
backend/src/main/java/com/civicfix/security/ratelimit/LoginAttemptRepository.java
backend/src/main/java/com/civicfix/security/ratelimit/LoginLockoutService.java
backend/src/main/java/com/civicfix/supervisor/SupervisorService.java
backend/src/main/java/com/civicfix/supervisor/dto/SupervisorWorkerResponse.java
backend/src/main/java/com/civicfix/user/User.java
backend/src/main/java/com/civicfix/user/UserRepository.java
backend/src/main/resources/application-prod.yml
backend/src/main/resources/application-test.yml
backend/src/main/resources/application.yml
backend/src/test/java/com/civicfix/ai/AiTriageServiceTest.java
backend/src/test/java/com/civicfix/notification/NotificationEventIntegrationTest.java
backend/src/test/java/com/civicfix/notification/NotificationPersistenceTest.java
backend/src/test/java/com/civicfix/supervisor/SupervisorServiceTest.java
docs/CIVICFIX_MASTER_ROADMAP.md
docs/DEPLOYMENT_CHECKLIST.md
docs/LIMITATIONS_AND_FUTURE_WORK.md
docs/evaluation/results.md
package-lock.json
package.json
src/App.jsx
src/components/common/TamilNaduLocationPicker.jsx
src/components/common/TamilNaduOverviewMap.jsx
src/components/layout/AdminLayout.jsx
src/main.jsx
src/pages/admin/CreateStaffPage.jsx
src/pages/admin/UserManagementPage.jsx
src/pages/citizen/CitizenIssueMapPage.jsx
src/pages/citizen/CitizenNotificationsPage.jsx
src/pages/citizen/ReportComplaintPage.jsx
src/pages/officer/AssignmentsPage.jsx
src/pages/officer/CivicIssueMapPage.jsx
src/pages/officer/ComplaintReviewPage.jsx
src/pages/officer/ResolutionVerificationPage.jsx
src/pages/public/LoginPage.jsx
src/pages/public/RegisterPage.jsx
src/pages/supervisor/SupervisorEscalationsPage.jsx
src/services/apiClient.js
src/services/authService.js
src/services/complaintService.js
tests/complaintService.test.js
tests/notificationService.test.js
vercel.json
vite.config.js
```

### 1.3 `git diff --stat`
```
backend/pom.xml                                    |   14 +
 .../main/java/com/civicfix/admin/AdminService.java |   73 +-
 .../com/civicfix/admin/dto/AdminUserResponse.java  |   18 +
 .../com/civicfix/admin/dto/CreateStaffRequest.java |    3 +-
 .../main/java/com/civicfix/ai/AiTriageService.java |   71 +-
 .../civicfix/ai/dto/SuggestCategoryResponse.java   |   36 +
 .../civicfix/ai/vision/VisionTriageProperties.java |    2 +-
 .../java/com/civicfix/auth/AuthController.java     |   36 +-
 .../main/java/com/civicfix/auth/AuthService.java   |   55 +-
 .../common/exception/GlobalExceptionHandler.java   |   14 +
 .../java/com/civicfix/complaint/Complaint.java     |   11 +
 .../civicfix/complaint/ComplaintController.java    |   68 +
 .../civicfix/complaint/ComplaintRepository.java    |   38 +
 .../com/civicfix/complaint/ComplaintService.java   |  357 ++++-
 .../civicfix/complaint/dto/ComplaintResponse.java  |    9 +
 .../complaint/sla/SlaEscalationScheduler.java      |   42 +
 .../complaint/sla/SlaEscalationService.java        |   30 +
 .../java/com/civicfix/config/SecurityConfig.java   |   25 +-
 .../src/main/java/com/civicfix/gis/GisService.java |   16 +-
 .../java/com/civicfix/mail/ResendEmailService.java |  164 ++-
 .../notification/NotificationController.java       |   12 +
 .../civicfix/notification/NotificationService.java |  366 +++--
 .../civicfix/notification/NotificationType.java    |    6 +-
 .../ratelimit/AccountLoginAttemptRepository.java   |    2 +
 .../ratelimit/IpLoginAttemptRepository.java        |    2 +
 .../security/ratelimit/LoginAttemptRepository.java |    1 +
 .../security/ratelimit/LoginLockoutService.java    |    1 +
 .../com/civicfix/supervisor/SupervisorService.java |   44 +-
 .../supervisor/dto/SupervisorWorkerResponse.java   |   45 +
 backend/src/main/java/com/civicfix/user/User.java  |   11 +
 .../java/com/civicfix/user/UserRepository.java     |    2 +
 backend/src/main/resources/application-prod.yml    |   10 +
 backend/src/main/resources/application-test.yml    |    5 +
 backend/src/main/resources/application.yml         |   14 +-
 .../java/com/civicfix/ai/AiTriageServiceTest.java  |   30 +
 .../NotificationEventIntegrationTest.java          |   79 +
 .../notification/NotificationPersistenceTest.java  |    2 +
 .../civicfix/supervisor/SupervisorServiceTest.java |   15 +-
 docs/CIVICFIX_MASTER_ROADMAP.md                    |    2 +-
 docs/DEPLOYMENT_CHECKLIST.md                       |    8 +
 docs/LIMITATIONS_AND_FUTURE_WORK.md                |    2 +-
 docs/evaluation/results.md                         |    2 +-
 package-lock.json                                  | 1541 +++++++++++++++++++-
 package.json                                       |    1 +
 src/App.jsx                                        |  127 +-
 src/components/common/TamilNaduLocationPicker.jsx  |   50 +-
 src/components/common/TamilNaduOverviewMap.jsx     |  218 ++-
 src/components/layout/AdminLayout.jsx              |    1 +
 src/main.jsx                                       |   17 +-
 src/pages/admin/CreateStaffPage.jsx                |   25 +-
 src/pages/admin/UserManagementPage.jsx             |   26 +-
 src/pages/citizen/CitizenIssueMapPage.jsx          |   24 +-
 src/pages/citizen/CitizenNotificationsPage.jsx     |    2 +-
 src/pages/citizen/ReportComplaintPage.jsx          |  123 +-
 src/pages/officer/AssignmentsPage.jsx              |   71 +-
 src/pages/officer/CivicIssueMapPage.jsx            |   31 +-
 src/pages/officer/ComplaintReviewPage.jsx          |  285 ++++
 src/pages/officer/ResolutionVerificationPage.jsx   |    4 +-
 src/pages/public/LoginPage.jsx                     |   50 +
 src/pages/public/RegisterPage.jsx                  |   26 +-
 src/pages/supervisor/SupervisorEscalationsPage.jsx |    2 +-
 src/services/apiClient.js                          |   89 +-
 src/services/authService.js                        |   24 +-
 src/services/complaintService.js                   |   58 +
 tests/complaintService.test.js                     |   45 +
 tests/notificationService.test.js                  |   20 +
 vercel.json                                        |   58 +
 vite.config.js                                     |   11 +
 68 files changed, 4321 insertions(+), 351 deletions(-)
```

### 1.4 `git ls-files --others --exclude-standard`
```
CIVICFIX_FINAL_DEPLOYMENT_REPORT.md
CIVICFIX_PHASE_1_AUDIT.md
CIVICFIX_RENDER_MAVEN_RECOVERY_REPORT.md
Dockerfile.backup
_backups_phase2_0/CitizenConfirmationAndReopenTest.java.bak
_backups_phase2_0/ComplaintAdjudicationAndDuplicateTest.java.bak
_backups_phase2_0/ComplaintServiceOfficerTest.java.bak
_backups_phase2_0/EvidenceLocationVerificationTest.java.bak
_backups_phase2_0/GisService.java.bak
_backups_phase2_0/ProductionSafetyGuardTest.java.bak
_backups_phase2_0/application.yml.bak
_backups_phase2_1/.env.example.bak
_backups_phase2_1/AuthController.java.bak
_backups_phase2_1/AuthService.java.bak
_backups_phase2_1/AuthService.java.bak2
_backups_phase2_1/ComplaintController.java.bak
_backups_phase2_1/ComplaintDetailsPage.jsx.bak
_backups_phase2_1/ComplaintPdfReceiptSecurityTest.java.bak
_backups_phase2_1/ComplaintService.java.bak
_backups_phase2_1/ComplaintService.java.bak2
_backups_phase2_1/ForgotPasswordPage.jsx.bak
_backups_phase2_1/PasswordResetSecurityTest.java.bak
_backups_phase2_1/PasswordResetTokenRepository.java.bak
_backups_phase2_1/PublicMapComplaintResponse.java.bak
_backups_phase2_1/PublicMapPage.jsx.bak
_backups_phase2_1/PublicMapPrivacyTest.java.bak
_backups_phase2_1/RateLimitInterceptor.java.bak
_backups_phase2_1/RateLimitProperties.java.bak
_backups_phase2_1/ResetPasswordPage.jsx.bak
_backups_phase2_1/SecurityConfig.java.bak
_backups_phase2_1/TamilNaduOverviewMap.jsx.bak
_backups_phase2_1/apiClient.js.bak
_backups_phase2_1/application-test.yml.bak
_backups_phase2_1/application.yml.bak
_backups_phase2_1/application.yml.bak2
_backups_phase2_1/authService.js.bak
_backups_phase2_1/complaintService.js.bak
_backups_phase2_1/pom.xml.bak
_backups_phase2_1_1/.env.example
_backups_phase2_1_1/AuthService.java
_backups_phase2_1_1/ComplaintController.java
_backups_phase2_1_1/ComplaintService.java
_backups_phase2_1_1/PasswordResetTokenRepository.java
_backups_phase2_1_1/PdfReceiptService.java
_backups_phase2_1_1/ProductionSafetyGuard.java
_backups_phase2_1_1/PublicMapComplaintResponse.java
_backups_phase2_1_1/SecurityConfig.java
_backups_phase2_1_1/UserPrincipal.java
_backups_phase2_1_1/UserRepository.java
_backups_phase2_1_1/application-prod.yml
_backups_phase2_1_1/application-test.yml
_backups_phase2_1_1/application.yml
_backups_phase2_1_1/docker-compose.yml
backend/src/main/java/com/civicfix/admin/AdminAnalyticsController.java
backend/src/main/java/com/civicfix/admin/AdminAnalyticsService.java
backend/src/main/java/com/civicfix/admin/dto/StaffWorkloadSummary.java
backend/src/main/java/com/civicfix/admin/dto/analytics/AreaPerformanceItem.java
backend/src/main/java/com/civicfix/admin/dto/analytics/CategoryPerformanceItem.java
backend/src/main/java/com/civicfix/admin/dto/analytics/MonthlyAnalyticsItem.java
backend/src/main/java/com/civicfix/admin/dto/analytics/StaffPerformanceItem.java
backend/src/main/java/com/civicfix/admin/dto/analytics/YearlyAnalyticsItem.java
backend/src/main/java/com/civicfix/ai/summary/ComplaintSummaryResponse.java
backend/src/main/java/com/civicfix/ai/summary/ComplaintSummaryService.java
backend/src/main/java/com/civicfix/ai/vision/pothole/PotholeDetection.java
backend/src/main/java/com/civicfix/ai/vision/pothole/PotholeDetectionRepository.java
backend/src/main/java/com/civicfix/ai/vision/pothole/PotholeDetectionService.java
backend/src/main/java/com/civicfix/ai/vision/pothole/PotholeDetectionValidator.java
backend/src/main/java/com/civicfix/ai/vision/pothole/PotholeMeasurementEngine.java
backend/src/main/java/com/civicfix/ai/vision/pothole/PotholeSeverity.java
backend/src/main/java/com/civicfix/ai/vision/pothole/PotholeSeverityCalculator.java
backend/src/main/java/com/civicfix/ai/vision/pothole/dto/CalibrationRequest.java
backend/src/main/java/com/civicfix/ai/vision/pothole/dto/PotholeAnalysisResponse.java
backend/src/main/java/com/civicfix/ai/vision/pothole/dto/PotholeBox.java
backend/src/main/java/com/civicfix/ai/vision/pothole/dto/PotholeMeasurement.java
backend/src/main/java/com/civicfix/ai/vision/pothole/dto/PotholeSeverityResult.java
backend/src/main/java/com/civicfix/auth/StaffActivationService.java
backend/src/main/java/com/civicfix/auth/StaffActivationToken.java
backend/src/main/java/com/civicfix/auth/StaffActivationTokenRepository.java
backend/src/main/java/com/civicfix/auth/dto/ActivateStaffRequest.java
backend/src/main/java/com/civicfix/auth/dto/FirebaseSessionRequest.java
backend/src/main/java/com/civicfix/auth/dto/VerifyActivationTokenResponse.java
backend/src/main/java/com/civicfix/auth/firebase/FirebaseAdminTokenVerifier.java
backend/src/main/java/com/civicfix/auth/firebase/FirebaseAuthController.java
backend/src/main/java/com/civicfix/auth/firebase/FirebaseAuthService.java
backend/src/main/java/com/civicfix/auth/firebase/FirebaseTokenClaims.java
backend/src/main/java/com/civicfix/auth/firebase/FirebaseTokenVerifier.java
backend/src/main/java/com/civicfix/complaint/dto/NearbyComplaintResponse.java
backend/src/main/java/com/civicfix/config/FirebaseConfig.java
backend/src/main/java/com/civicfix/config/FirebaseProperties.java
backend/src/main/java/com/civicfix/config/filter/CorrelationIdFilter.java
backend/src/main/java/com/civicfix/mail/AesGcmEncryptionHelper.java
backend/src/main/java/com/civicfix/mail/EmailOutbox.java
backend/src/main/java/com/civicfix/mail/EmailOutboxRepository.java
backend/src/main/java/com/civicfix/mail/EmailOutboxScheduler.java
backend/src/main/java/com/civicfix/mail/EmailOutboxService.java
backend/src/main/java/com/civicfix/mail/ResendWebhookController.java
backend/src/main/java/com/civicfix/storage/FileTransactionHelper.java
backend/src/main/resources/db/migration/V21__create_email_outbox_table.sql
backend/src/main/resources/db/migration/V22__create_pothole_detections_table.sql
backend/src/main/resources/db/migration/V23__create_staff_activation_tokens_table.sql
backend/src/main/resources/db/migration/V24__add_firebase_uid_to_users.sql
backend/src/main/resources/db/migration/V25__add_performance_and_created_at_indexes.sql
backend/src/test/java/com/civicfix/admin/AdminAnalyticsServiceTest.java
backend/src/test/java/com/civicfix/ai/summary/ComplaintSummaryServiceTest.java
backend/src/test/java/com/civicfix/ai/vision/pothole/PotholeDetectionEndpointIntegrationTest.java
backend/src/test/java/com/civicfix/ai/vision/pothole/PotholeDetectionProviderContractTest.java
backend/src/test/java/com/civicfix/ai/vision/pothole/PotholeDetectionServiceTest.java
backend/src/test/java/com/civicfix/ai/vision/pothole/PotholeDetectionValidatorTest.java
backend/src/test/java/com/civicfix/ai/vision/pothole/PotholeMeasurementEngineTest.java
backend/src/test/java/com/civicfix/ai/vision/pothole/PotholeSeverityCalculatorTest.java
backend/src/test/java/com/civicfix/auth/StaffActivationIntegrationTest.java
backend/src/test/java/com/civicfix/auth/firebase/FirebaseAdminTokenVerifierTest.java
backend/src/test/java/com/civicfix/auth/firebase/FirebaseAuthIntegrationTest.java
backend/src/test/java/com/civicfix/complaint/AssignmentHardeningAndWorkloadTest.java
backend/src/test/java/com/civicfix/complaint/ComplaintSummaryEndpointIntegrationTest.java
backend/src/test/java/com/civicfix/config/SecurityHeadersAndCorrelationIntegrationTest.java
backend/src/test/java/com/civicfix/gis/NearbyComplaintTest.java
backend/src/test/java/com/civicfix/mail/AesGcmEncryptionHelperTest.java
backend/src/test/java/com/civicfix/mail/EmailOutboxServiceTest.java
backend/src/test/java/com/civicfix/mail/ResendWebhookControllerTest.java
backend/src/test/java/com/civicfix/storage/FileTransactionHelperTest.java
docs/CIVICFIX_FINAL_IMPLEMENTATION_READINESS.md
docs/CIVICFIX_PHASE_8_FINAL_VERIFICATION_REPORT.md
docs/CIVICFIX_PHASE_8_PRODUCTION_READINESS_REPORT.md
docs/CIVICFIX_PRE_DEPLOYMENT_FINAL_REVIEW.md
docs/CIVICFIX_PRE_GIT_PUSH_CONSISTENCY_REPORT.md
src/components/common/ErrorBoundary.jsx
src/components/common/PotholeDetectionOverlay.jsx
src/constants/tamilNaduDistricts.js
src/pages/admin/AdminAnalyticsPage.jsx
src/pages/public/StaffActivationPage.jsx
src/services/firebaseAuth.js
src/services/firebaseConfig.js
src/utils/gisClustering.js
src/utils/gisHeatmap.js
tests/errorBoundary.test.js
tests/firebaseAuth.test.js
tests/gisEnhancements.test.js
tests/phase6StaffAdmin.test.js
tests/potholeDetection.test.js
```

### 1.5 `git diff --check`
```
CLEAN (Exit Code 0) - Zero whitespace, conflict, or line-ending defects.
```

### 1.6 `git status --short backend/src/main/resources/db/migration/`
```
?? backend/src/main/resources/db/migration/V21__create_email_outbox_table.sql
?? backend/src/main/resources/db/migration/V22__create_pothole_detections_table.sql
?? backend/src/main/resources/db/migration/V23__create_staff_activation_tokens_table.sql
?? backend/src/main/resources/db/migration/V24__add_firebase_uid_to_users.sql
?? backend/src/main/resources/db/migration/V25__add_performance_and_created_at_indexes.sql
```

---

## 2. Complete Tracked Modified Files Inventory (68 Files)

*Every file listed below has been verified using its exact relative path without wildcards or directory summaries.*

### 2.1 Production Backend Source Code (30 Files)
- `backend/src/main/java/com/civicfix/admin/AdminService.java`
- `backend/src/main/java/com/civicfix/admin/dto/AdminUserResponse.java`
- `backend/src/main/java/com/civicfix/admin/dto/CreateStaffRequest.java`
- `backend/src/main/java/com/civicfix/ai/AiTriageService.java`
- `backend/src/main/java/com/civicfix/ai/dto/SuggestCategoryResponse.java`
- `backend/src/main/java/com/civicfix/ai/vision/VisionTriageProperties.java`
- `backend/src/main/java/com/civicfix/auth/AuthController.java`
- `backend/src/main/java/com/civicfix/auth/AuthService.java`
- `backend/src/main/java/com/civicfix/common/exception/GlobalExceptionHandler.java`
- `backend/src/main/java/com/civicfix/complaint/Complaint.java`
- `backend/src/main/java/com/civicfix/complaint/ComplaintController.java`
- `backend/src/main/java/com/civicfix/complaint/ComplaintRepository.java`
- `backend/src/main/java/com/civicfix/complaint/ComplaintService.java`
- `backend/src/main/java/com/civicfix/complaint/dto/ComplaintResponse.java`
- `backend/src/main/java/com/civicfix/complaint/sla/SlaEscalationScheduler.java`
- `backend/src/main/java/com/civicfix/complaint/sla/SlaEscalationService.java`
- `backend/src/main/java/com/civicfix/config/SecurityConfig.java`
- `backend/src/main/java/com/civicfix/gis/GisService.java`
- `backend/src/main/java/com/civicfix/mail/ResendEmailService.java`
- `backend/src/main/java/com/civicfix/notification/NotificationController.java`
- `backend/src/main/java/com/civicfix/notification/NotificationService.java`
- `backend/src/main/java/com/civicfix/notification/NotificationType.java`
- `backend/src/main/java/com/civicfix/security/ratelimit/AccountLoginAttemptRepository.java`
- `backend/src/main/java/com/civicfix/security/ratelimit/IpLoginAttemptRepository.java`
- `backend/src/main/java/com/civicfix/security/ratelimit/LoginAttemptRepository.java`
- `backend/src/main/java/com/civicfix/security/ratelimit/LoginLockoutService.java`
- `backend/src/main/java/com/civicfix/supervisor/SupervisorService.java`
- `backend/src/main/java/com/civicfix/supervisor/dto/SupervisorWorkerResponse.java`
- `backend/src/main/java/com/civicfix/user/User.java`
- `backend/src/main/java/com/civicfix/user/UserRepository.java`

### 2.2 Backend Resources & Configuration (3 Files)
- `backend/src/main/resources/application-prod.yml`
- `backend/src/main/resources/application-test.yml`
- `backend/src/main/resources/application.yml`

### 2.3 Production Frontend Source Code (20 Files)
- `src/App.jsx`
- `src/components/common/TamilNaduLocationPicker.jsx`
- `src/components/common/TamilNaduOverviewMap.jsx`
- `src/components/layout/AdminLayout.jsx`
- `src/main.jsx`
- `src/pages/admin/CreateStaffPage.jsx`
- `src/pages/admin/UserManagementPage.jsx`
- `src/pages/citizen/CitizenIssueMapPage.jsx`
- `src/pages/citizen/CitizenNotificationsPage.jsx`
- `src/pages/citizen/ReportComplaintPage.jsx`
- `src/pages/officer/AssignmentsPage.jsx`
- `src/pages/officer/CivicIssueMapPage.jsx`
- `src/pages/officer/ComplaintReviewPage.jsx`
- `src/pages/officer/ResolutionVerificationPage.jsx`
- `src/pages/public/LoginPage.jsx`
- `src/pages/public/RegisterPage.jsx`
- `src/pages/supervisor/SupervisorEscalationsPage.jsx`
- `src/services/apiClient.js`
- `src/services/authService.js`
- `src/services/complaintService.js`

### 2.4 Build, Package & Deployment Configuration (5 Files)
- `backend/pom.xml`
- `package-lock.json`
- `package.json`
- `vercel.json`
- `vite.config.js`

### 2.5 Tests (6 Files)
- `backend/src/test/java/com/civicfix/ai/AiTriageServiceTest.java`
- `backend/src/test/java/com/civicfix/notification/NotificationEventIntegrationTest.java`
- `backend/src/test/java/com/civicfix/notification/NotificationPersistenceTest.java`
- `backend/src/test/java/com/civicfix/supervisor/SupervisorServiceTest.java`
- `tests/complaintService.test.js`
- `tests/notificationService.test.js`

### 2.6 Documentation & Results (4 Files)
- `docs/CIVICFIX_MASTER_ROADMAP.md`
- `docs/DEPLOYMENT_CHECKLIST.md`
- `docs/LIMITATIONS_AND_FUTURE_WORK.md`
- `docs/evaluation/results.md`

---

## 3. Complete Untracked Files Inventory (140 Files)

*Every untracked file listed below has been verified using its exact relative path without wildcards or directory summaries.*

### 3.1 Database Migrations (5 Files) — **CONFIRMED FOR STAGING**
- `backend/src/main/resources/db/migration/V21__create_email_outbox_table.sql`
- `backend/src/main/resources/db/migration/V22__create_pothole_detections_table.sql`
- `backend/src/main/resources/db/migration/V23__create_staff_activation_tokens_table.sql`
- `backend/src/main/resources/db/migration/V24__add_firebase_uid_to_users.sql`
- `backend/src/main/resources/db/migration/V25__add_performance_and_created_at_indexes.sql`

### 3.2 Production Backend Source Code (44 Files) — **CONFIRMED FOR STAGING**
- `backend/src/main/java/com/civicfix/admin/AdminAnalyticsController.java`
- `backend/src/main/java/com/civicfix/admin/AdminAnalyticsService.java`
- `backend/src/main/java/com/civicfix/admin/dto/StaffWorkloadSummary.java`
- `backend/src/main/java/com/civicfix/admin/dto/analytics/AreaPerformanceItem.java`
- `backend/src/main/java/com/civicfix/admin/dto/analytics/CategoryPerformanceItem.java`
- `backend/src/main/java/com/civicfix/admin/dto/analytics/MonthlyAnalyticsItem.java`
- `backend/src/main/java/com/civicfix/admin/dto/analytics/StaffPerformanceItem.java`
- `backend/src/main/java/com/civicfix/admin/dto/analytics/YearlyAnalyticsItem.java`
- `backend/src/main/java/com/civicfix/ai/summary/ComplaintSummaryResponse.java`
- `backend/src/main/java/com/civicfix/ai/summary/ComplaintSummaryService.java`
- `backend/src/main/java/com/civicfix/ai/vision/pothole/PotholeDetection.java`
- `backend/src/main/java/com/civicfix/ai/vision/pothole/PotholeDetectionRepository.java`
- `backend/src/main/java/com/civicfix/ai/vision/pothole/PotholeDetectionService.java`
- `backend/src/main/java/com/civicfix/ai/vision/pothole/PotholeDetectionValidator.java`
- `backend/src/main/java/com/civicfix/ai/vision/pothole/PotholeMeasurementEngine.java`
- `backend/src/main/java/com/civicfix/ai/vision/pothole/PotholeSeverity.java`
- `backend/src/main/java/com/civicfix/ai/vision/pothole/PotholeSeverityCalculator.java`
- `backend/src/main/java/com/civicfix/ai/vision/pothole/dto/CalibrationRequest.java`
- `backend/src/main/java/com/civicfix/ai/vision/pothole/dto/PotholeAnalysisResponse.java`
- `backend/src/main/java/com/civicfix/ai/vision/pothole/dto/PotholeBox.java`
- `backend/src/main/java/com/civicfix/ai/vision/pothole/dto/PotholeMeasurement.java`
- `backend/src/main/java/com/civicfix/ai/vision/pothole/dto/PotholeSeverityResult.java`
- `backend/src/main/java/com/civicfix/auth/StaffActivationService.java`
- `backend/src/main/java/com/civicfix/auth/StaffActivationToken.java`
- `backend/src/main/java/com/civicfix/auth/StaffActivationTokenRepository.java`
- `backend/src/main/java/com/civicfix/auth/dto/ActivateStaffRequest.java`
- `backend/src/main/java/com/civicfix/auth/dto/FirebaseSessionRequest.java`
- `backend/src/main/java/com/civicfix/auth/dto/VerifyActivationTokenResponse.java`
- `backend/src/main/java/com/civicfix/auth/firebase/FirebaseAdminTokenVerifier.java`
- `backend/src/main/java/com/civicfix/auth/firebase/FirebaseAuthController.java`
- `backend/src/main/java/com/civicfix/auth/firebase/FirebaseAuthService.java`
- `backend/src/main/java/com/civicfix/auth/firebase/FirebaseTokenClaims.java`
- `backend/src/main/java/com/civicfix/auth/firebase/FirebaseTokenVerifier.java`
- `backend/src/main/java/com/civicfix/complaint/dto/NearbyComplaintResponse.java`
- `backend/src/main/java/com/civicfix/config/FirebaseConfig.java`
- `backend/src/main/java/com/civicfix/config/FirebaseProperties.java`
- `backend/src/main/java/com/civicfix/config/filter/CorrelationIdFilter.java`
- `backend/src/main/java/com/civicfix/mail/AesGcmEncryptionHelper.java`
- `backend/src/main/java/com/civicfix/mail/EmailOutbox.java`
- `backend/src/main/java/com/civicfix/mail/EmailOutboxRepository.java`
- `backend/src/main/java/com/civicfix/mail/EmailOutboxScheduler.java`
- `backend/src/main/java/com/civicfix/mail/EmailOutboxService.java`
- `backend/src/main/java/com/civicfix/mail/ResendWebhookController.java`
- `backend/src/main/java/com/civicfix/storage/FileTransactionHelper.java`

### 3.3 Production Frontend Source Code (9 Files) — **CONFIRMED FOR STAGING**
- `src/components/common/ErrorBoundary.jsx`
- `src/components/common/PotholeDetectionOverlay.jsx`
- `src/constants/tamilNaduDistricts.js`
- `src/pages/admin/AdminAnalyticsPage.jsx`
- `src/pages/public/StaffActivationPage.jsx`
- `src/services/firebaseAuth.js`
- `src/services/firebaseConfig.js`
- `src/utils/gisClustering.js`
- `src/utils/gisHeatmap.js`

### 3.4 Backend & Frontend Tests (24 Files) — **CONFIRMED FOR STAGING**
- `backend/src/test/java/com/civicfix/admin/AdminAnalyticsServiceTest.java`
- `backend/src/test/java/com/civicfix/ai/summary/ComplaintSummaryServiceTest.java`
- `backend/src/test/java/com/civicfix/ai/vision/pothole/PotholeDetectionEndpointIntegrationTest.java`
- `backend/src/test/java/com/civicfix/ai/vision/pothole/PotholeDetectionProviderContractTest.java`
- `backend/src/test/java/com/civicfix/ai/vision/pothole/PotholeDetectionServiceTest.java`
- `backend/src/test/java/com/civicfix/ai/vision/pothole/PotholeDetectionValidatorTest.java`
- `backend/src/test/java/com/civicfix/ai/vision/pothole/PotholeMeasurementEngineTest.java`
- `backend/src/test/java/com/civicfix/ai/vision/pothole/PotholeSeverityCalculatorTest.java`
- `backend/src/test/java/com/civicfix/auth/StaffActivationIntegrationTest.java`
- `backend/src/test/java/com/civicfix/auth/firebase/FirebaseAdminTokenVerifierTest.java`
- `backend/src/test/java/com/civicfix/auth/firebase/FirebaseAuthIntegrationTest.java`
- `backend/src/test/java/com/civicfix/complaint/AssignmentHardeningAndWorkloadTest.java`
- `backend/src/test/java/com/civicfix/complaint/ComplaintSummaryEndpointIntegrationTest.java`
- `backend/src/test/java/com/civicfix/config/SecurityHeadersAndCorrelationIntegrationTest.java`
- `backend/src/test/java/com/civicfix/gis/NearbyComplaintTest.java`
- `backend/src/test/java/com/civicfix/mail/AesGcmEncryptionHelperTest.java`
- `backend/src/test/java/com/civicfix/mail/EmailOutboxServiceTest.java`
- `backend/src/test/java/com/civicfix/mail/ResendWebhookControllerTest.java`
- `backend/src/test/java/com/civicfix/storage/FileTransactionHelperTest.java`
- `tests/errorBoundary.test.js`
- `tests/firebaseAuth.test.js`
- `tests/gisEnhancements.test.js`
- `tests/phase6StaffAdmin.test.js`
- `tests/potholeDetection.test.js`

### 3.5 Documentation & Audit Reports (8 Files) — **CONFIRMED FOR STAGING**
- `CIVICFIX_FINAL_DEPLOYMENT_REPORT.md`
- `CIVICFIX_PHASE_1_AUDIT.md`
- `CIVICFIX_RENDER_MAVEN_RECOVERY_REPORT.md`
- `docs/CIVICFIX_FINAL_IMPLEMENTATION_READINESS.md`
- `docs/CIVICFIX_PHASE_8_FINAL_VERIFICATION_REPORT.md`
- `docs/CIVICFIX_PHASE_8_PRODUCTION_READINESS_REPORT.md`
- `docs/CIVICFIX_PRE_DEPLOYMENT_FINAL_REVIEW.md`
- `docs/CIVICFIX_PRE_GIT_PUSH_CONSISTENCY_REPORT.md`

### 3.6 Backups & Temporary Files (50 Files) — **EXCLUDED FROM STAGING**
- `Dockerfile.backup`
- `_backups_phase2_0/CitizenConfirmationAndReopenTest.java.bak`
- `_backups_phase2_0/ComplaintAdjudicationAndDuplicateTest.java.bak`
- `_backups_phase2_0/ComplaintServiceOfficerTest.java.bak`
- `_backups_phase2_0/EvidenceLocationVerificationTest.java.bak`
- `_backups_phase2_0/GisService.java.bak`
- `_backups_phase2_0/ProductionSafetyGuardTest.java.bak`
- `_backups_phase2_0/application.yml.bak`
- `_backups_phase2_1/.env.example.bak`
- `_backups_phase2_1/AuthController.java.bak`
- `_backups_phase2_1/AuthService.java.bak`
- `_backups_phase2_1/AuthService.java.bak2`
- `_backups_phase2_1/ComplaintController.java.bak`
- `_backups_phase2_1/ComplaintDetailsPage.jsx.bak`
- `_backups_phase2_1/ComplaintPdfReceiptSecurityTest.java.bak`
- `_backups_phase2_1/ComplaintService.java.bak`
- `_backups_phase2_1/ComplaintService.java.bak2`
- `_backups_phase2_1/ForgotPasswordPage.jsx.bak`
- `_backups_phase2_1/PasswordResetSecurityTest.java.bak`
- `_backups_phase2_1/PasswordResetTokenRepository.java.bak`
- `_backups_phase2_1/PublicMapComplaintResponse.java.bak`
- `_backups_phase2_1/PublicMapPage.jsx.bak`
- `_backups_phase2_1/PublicMapPrivacyTest.java.bak`
- `_backups_phase2_1/RateLimitInterceptor.java.bak`
- `_backups_phase2_1/RateLimitProperties.java.bak`
- `_backups_phase2_1/ResetPasswordPage.jsx.bak`
- `_backups_phase2_1/SecurityConfig.java.bak`
- `_backups_phase2_1/TamilNaduOverviewMap.jsx.bak`
- `_backups_phase2_1/apiClient.js.bak`
- `_backups_phase2_1/application-test.yml.bak`
- `_backups_phase2_1/application.yml.bak`
- `_backups_phase2_1/application.yml.bak2`
- `_backups_phase2_1/authService.js.bak`
- `_backups_phase2_1/complaintService.js.bak`
- `_backups_phase2_1/pom.xml.bak`
- `_backups_phase2_1_1/.env.example`
- `_backups_phase2_1_1/AuthService.java`
- `_backups_phase2_1_1/ComplaintController.java`
- `_backups_phase2_1_1/ComplaintService.java`
- `_backups_phase2_1_1/PasswordResetTokenRepository.java`
- `_backups_phase2_1_1/PdfReceiptService.java`
- `_backups_phase2_1_1/ProductionSafetyGuard.java`
- `_backups_phase2_1_1/PublicMapComplaintResponse.java`
- `_backups_phase2_1_1/SecurityConfig.java`
- `_backups_phase2_1_1/UserPrincipal.java`
- `_backups_phase2_1_1/UserRepository.java`
- `_backups_phase2_1_1/application-prod.yml`
- `_backups_phase2_1_1/application-test.yml`
- `_backups_phase2_1_1/application.yml`
- `_backups_phase2_1_1/docker-compose.yml`

---

## 4. Secret & Sensitive Data Audit

A rigorous automated and manual scan of the working directory was performed to identify any accidental secret exposure.

### 4.1 Secrets Inspection Results
- **`.env` and Local Secret Files:** None untracked at root or in `backend/`. The root `.gitignore` properly excludes `.env`, `.env.local`, `.env.*.local`.
- **Candidate Template Files in Backups:**
  - `_backups_phase2_1/.env.example.bak`
  - `_backups_phase2_1_1/.env.example`
  - *Inspection Finding:* Both files are non-sensitive templates containing only placeholder values (`civicfix_dev_password_only`). Neither contains production secrets or live credentials.
  - *Staging Decision:* Both are located in `_backups_*` and are **EXCLUDED from staging**.
- **Service Account JSON & Private Keys:** Zero `.json` service account files, `.pem`, `.key`, or `.p12` certificates exist in untracked files.
- **Passwords & Live API Keys:** Verified zero hardcoded production API tokens (Resend, Firebase, Gemini) in source files or configuration. All secrets are dynamically referenced via environment variables (`${RESEND_API_KEY:}`, `${GOOGLE_AI_API_KEY:}`, `${FIREBASE_CREDENTIALS_JSON:}`).

---

## 5. Staging Safety & Exclusions Verification

### 5.1 Database Migrations Confirmation
- **Historical Migrations (V1–V24):** 100% untouched. No modifications or deletions.
- **Proposed Migrations (V21–V25):** Fully included in the proposed staging list:
  - `backend/src/main/resources/db/migration/V21__create_email_outbox_table.sql`
  - `backend/src/main/resources/db/migration/V22__create_pothole_detections_table.sql`
  - `backend/src/main/resources/db/migration/V23__create_staff_activation_tokens_table.sql`
  - `backend/src/main/resources/db/migration/V24__add_firebase_uid_to_users.sql`
  - `backend/src/main/resources/db/migration/V25__add_performance_and_created_at_indexes.sql`

### 5.2 Excluded Files Confirmation
The following files and directories are **strictly excluded** from Git staging:
- `_backups_phase2_0/` (7 backup files)
- `_backups_phase2_1/` (27 backup files)
- `_backups_phase2_1_1/` (15 backup files)
- `Dockerfile.backup` (1 temporary file)
- **Total Excluded:** 50 files.

### 5.3 `.gitignore` Adequacy Evaluation
The current root `.gitignore` covers:
- `node_modules/`, `dist/`, `dist-ssr/`
- `.env`, `.env.local`, `.env.*.local`
- `*.log`
- `backend/target/`, `target/`, `*.class`, `*.jar`
- `backend/uploads/`, `uploads/`
- `backend/.mvn/timing.properties`, `backend/.flattened-pom.xml`

**Recommendation for Operator:**
To prevent accidental staging of backup directories in future operations, add the following lines to `.gitignore`:
```gitignore
# Local Phase Backups
_backups_*/
*.backup
*.bak
```

---

## 6. Proposed Staging Command (For Operator Execution)

*Note: Per strict instructions, the agent did not execute any git staging or commit commands.*

When the operator is ready to stage the verified changes, the operator can safely run:

```bash
# 1. Stage all tracked modifications
git add -u

# 2. Stage new production features, migrations, tests, and documentation
git add backend/src/main/java/com/civicfix/
git add backend/src/main/resources/db/migration/
git add backend/src/test/java/com/civicfix/
git add src/
git add tests/
git add docs/
git add CIVICFIX_FINAL_DEPLOYMENT_REPORT.md CIVICFIX_PHASE_1_AUDIT.md CIVICFIX_RENDER_MAVEN_RECOVERY_REPORT.md

# 3. Verify that backups remain untracked
git status --short

```

---

*Audit completed with zero modifications to workspace files or Git repository state.*