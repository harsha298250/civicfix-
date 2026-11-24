package com.civicfix.supervisor;

import com.civicfix.admin.dto.AdminUserResponse;
import com.civicfix.audit.AuditService;
import com.civicfix.common.PagedResponse;
import com.civicfix.common.exception.BadRequestException;
import com.civicfix.common.exception.ConflictException;
import com.civicfix.common.exception.ForbiddenException;
import com.civicfix.common.exception.ResourceNotFoundException;
import com.civicfix.complaint.Complaint;
import com.civicfix.complaint.ComplaintRepository;
import com.civicfix.complaint.dto.ComplaintResponse;
import com.civicfix.notification.NotificationService;
import com.civicfix.security.UserPrincipal;
import com.civicfix.supervisor.dto.DeEscalateRequest;
import com.civicfix.supervisor.dto.ExtendSlaRequest;
import com.civicfix.supervisor.dto.ReassignWorkerRequest;
import com.civicfix.supervisor.dto.SupervisorWorkerResponse;
import com.civicfix.user.RoleName;
import com.civicfix.user.User;
import com.civicfix.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class SupervisorService {

    private static final Logger log = LoggerFactory.getLogger(SupervisorService.class);

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public SupervisorService(
            ComplaintRepository complaintRepository,
            UserRepository userRepository,
            AuditService auditService,
            NotificationService notificationService) {
        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public PagedResponse<ComplaintResponse> getEscalatedComplaints(String ward, String category, Pageable pageable) {
        return getEscalatedComplaints(ward, category, pageable, null);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ComplaintResponse> getEscalatedComplaints(String ward, String category, Pageable pageable, UserPrincipal principal) {
        String cleanWard = (ward != null && !ward.isBlank() && !ward.equalsIgnoreCase("ALL")) ? ward.trim() : null;
        String cleanCategory = (category != null && !category.isBlank() && !category.equalsIgnoreCase("ALL")) ? category.trim() : null;

        if (principal != null) {
            boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (!isAdmin && !principal.hasActiveCrossDepartmentAccess()) {
                String dept = principal.getDepartment();
                if (dept == null || dept.isBlank()) {
                    throw new ForbiddenException("Supervisor has no assigned department. Access denied.");
                }
                if (cleanCategory != null) {
                    if (!isCategoryAllowedForDepartment(cleanCategory, dept, principal)) {
                        throw new ForbiddenException("Supervisors cannot view escalations outside their authorized department (" + dept + ").");
                    }
                } else {
                    cleanCategory = getDefaultCategoryForDepartment(dept);
                    if (cleanCategory == null) {
                        throw new ForbiddenException("Supervisors cannot view escalations for unauthorized department: " + dept);
                    }
                }
            }
        }

        Page<Complaint> page = complaintRepository.findEscalatedComplaints(cleanWard, cleanCategory, pageable);
        List<ComplaintResponse> content = page.getContent().stream()
                .map(c -> principal != null ? ComplaintResponse.fromEntity(c, principal, false) : ComplaintResponse.fromEntity(c))
                .toList();

        return new PagedResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    @Transactional
    public ComplaintResponse reassignWorker(String idOrRef, ReassignWorkerRequest req, UserPrincipal principal, String ipAddress) {
        boolean isSupervisor = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPERVISOR"));
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isSupervisor && !isAdmin) {
            throw new ForbiddenException("Only supervisors and administrators can reassign complaint field technicians.");
        }

        if (req == null || req.getNewWorkerId() == null) {
            throw new BadRequestException("New worker ID is required.");
        }
        if (req.getReason() == null || req.getReason().trim().length() < 5 || req.getReason().trim().length() > 500) {
            throw new BadRequestException("A reassignment reason between 5 and 500 characters is required.");
        }

        Complaint complaint = resolveComplaint(idOrRef);
        enforceSupervisorDepartmentAccess(complaint, principal);

        if (req.getVersion() != null && !req.getVersion().equals(complaint.getVersion())) {
            throw new ConflictException("Complaint has been modified by another transaction or user. Please refresh and try again.");
        }

        if (complaint.getStatus().isTerminal()) {
            throw new BadRequestException("Cannot reassign worker on a terminal complaint (status: " + complaint.getStatus() + ").");
        }

        if (complaint.getAssignedWorker() == null) {
            throw new BadRequestException("Cannot reassign a complaint that has not been initially assigned to a field worker.");
        }

        User newWorker = userRepository.findById(req.getNewWorkerId())
                .orElseThrow(() -> new BadRequestException("New worker account not found with ID: " + req.getNewWorkerId()));

        if (!"ACTIVE".equalsIgnoreCase(newWorker.getAccountStatus())) {
            throw new BadRequestException("Selected worker account is not active.");
        }

        boolean hasWorkerRole = newWorker.getRoles().stream()
                .anyMatch(r -> r.getName() == RoleName.WORKER);
        if (!hasWorkerRole) {
            throw new BadRequestException("Selected user does not have the WORKER role.");
        }

        if (complaint.getAssignedWorker().getId().equals(newWorker.getId())) {
            throw new BadRequestException("Selected worker is already assigned to this complaint.");
        }

        if (!isAdmin && !principal.hasActiveCrossDepartmentAccess()) {
            String dept = principal.getDepartment();
            if (dept == null || dept.isBlank()) {
                throw new ForbiddenException("Supervisor has no assigned department. Access denied.");
            }
            if (newWorker.getDepartment() != null && !newWorker.getDepartment().isBlank()) {
                if (!isCategoryAllowedForDepartment(complaint.getCategory(), newWorker.getDepartment(), principal)) {
                    throw new BadRequestException("Selected worker belongs to department '" + newWorker.getDepartment() +
                            "' which is not qualified for category " + complaint.getCategory() + ".");
                }
            }
        }

        User actor = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Supervisor account not found."));

        User oldWorker = complaint.getAssignedWorker();
        complaint.setAssignedWorker(newWorker);
        complaint.setUpdatedAt(OffsetDateTime.now());

        com.civicfix.complaint.ComplaintStatus previousStatus = complaint.getStatus();
        if (previousStatus == com.civicfix.complaint.ComplaintStatus.REOPENED) {
            complaint.setStatus(com.civicfix.complaint.ComplaintStatus.ASSIGNED);
        }

        String historyNote = "Worker reassigned from " + oldWorker.getFullName() +
                " to " + newWorker.getFullName() + ". Reason: " + req.getReason().trim();
        complaint.addStatusHistory(previousStatus, complaint.getStatus(), actor, historyNote);

        Complaint saved = complaintRepository.save(complaint);
        complaintRepository.flush();

        auditService.logAction(
                actor.getId(),
                actor.getFullName(),
                principal.getAuthorities().iterator().next().getAuthority(),
                "REASSIGN_WORKER",
                "COMPLAINT",
                saved.getId().toString(),
                "Reassigned worker on complaint #" + saved.getReferenceId() +
                        " from " + oldWorker.getFullName() + " to " + newWorker.getFullName() +
                        ". Reason: " + req.getReason().trim(),
                ipAddress,
                "SUCCESS"
        );

        notificationService.notifyWorkerReassigned(saved, oldWorker, newWorker, actor, req.getReason().trim());

        return ComplaintResponse.fromEntity(saved, principal);
    }

    @Transactional
    public ComplaintResponse extendSla(String idOrRef, ExtendSlaRequest req, UserPrincipal principal, String ipAddress) {
        boolean isSupervisor = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPERVISOR"));
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isSupervisor && !isAdmin) {
            throw new ForbiddenException("Only supervisors and administrators can extend complaint SLA deadlines.");
        }

        if (req == null || req.getAdditionalHours() == null || req.getAdditionalHours() < 1 || req.getAdditionalHours() > 720) {
            throw new BadRequestException("Additional hours between 1 and 720 (30 days) is required.");
        }
        if (req.getReason() == null || req.getReason().trim().length() < 5 || req.getReason().trim().length() > 500) {
            throw new BadRequestException("An extension reason between 5 and 500 characters is required.");
        }

        Complaint complaint = resolveComplaint(idOrRef);
        enforceSupervisorDepartmentAccess(complaint, principal);

        if (req.getVersion() != null && !req.getVersion().equals(complaint.getVersion())) {
            throw new ConflictException("Complaint has been modified by another transaction or user. Please refresh and try again.");
        }

        if (complaint.getStatus().isTerminal()) {
            throw new BadRequestException("Cannot extend SLA on a terminal complaint (status: " + complaint.getStatus() + ").");
        }

        User actor = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Supervisor account not found."));

        OffsetDateTime oldDeadline = complaint.getSlaDueAt();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime baseTime = (oldDeadline != null && oldDeadline.isAfter(now)) ? oldDeadline : now;
        OffsetDateTime newDeadline = baseTime.plusHours(req.getAdditionalHours());

        complaint.setSlaDueAt(newDeadline);
        complaint.setSlaWarningSentAt(null);
        complaint.setUpdatedAt(now);

        String historyNote = "SLA deadline extended by " + req.getAdditionalHours() + "h from " +
                (oldDeadline != null ? oldDeadline.toString() : "None") + " to " + newDeadline +
                ". Reason: " + req.getReason().trim();
        complaint.addStatusHistory(complaint.getStatus(), complaint.getStatus(), actor, historyNote);

        Complaint saved = complaintRepository.save(complaint);
        complaintRepository.flush();

        auditService.logAction(
                actor.getId(),
                actor.getFullName(),
                principal.getAuthorities().iterator().next().getAuthority(),
                "EXTEND_SLA",
                "COMPLAINT",
                saved.getId().toString(),
                "Extended SLA for complaint #" + saved.getReferenceId() + " by " + req.getAdditionalHours() + "h. Reason: " + req.getReason().trim(),
                ipAddress,
                "SUCCESS"
        );

        log.info("Supervisor {} extended SLA for complaint #{} by {} hours to {}",
                actor.getEmail(), saved.getReferenceId(), req.getAdditionalHours(), newDeadline);

        return ComplaintResponse.fromEntity(saved, principal, false);
    }

    @Transactional
    public ComplaintResponse deEscalate(String idOrRef, DeEscalateRequest req, UserPrincipal principal, String ipAddress) {
        boolean isSupervisor = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPERVISOR"));
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isSupervisor && !isAdmin) {
            throw new ForbiddenException("Only supervisors and administrators can de-escalate complaints.");
        }

        if (req == null || req.getReason() == null || req.getReason().trim().length() < 5 || req.getReason().trim().length() > 500) {
            throw new BadRequestException("A de-escalation explanation between 5 and 500 characters is required.");
        }

        Complaint complaint = resolveComplaint(idOrRef);
        enforceSupervisorDepartmentAccess(complaint, principal);

        if (req.getVersion() != null && !req.getVersion().equals(complaint.getVersion())) {
            throw new ConflictException("Complaint has been modified by another transaction or user. Please refresh and try again.");
        }

        if (complaint.getEscalatedAt() == null) {
            throw new BadRequestException("Complaint is not currently in an escalated state.");
        }

        User actor = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Supervisor account not found."));

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime oldEscalatedAt = complaint.getEscalatedAt();
        complaint.setEscalatedAt(null);

        // Safe suppression to prevent immediate re-escalation by scheduler
        int graceHours = (req.getAdditionalHours() != null && req.getAdditionalHours() > 0)
                ? req.getAdditionalHours() : 24;
        OffsetDateTime minimumSafeDeadline = now.plusHours(graceHours);
        if (complaint.getSlaDueAt() == null || complaint.getSlaDueAt().isBefore(minimumSafeDeadline)) {
            complaint.setSlaDueAt(minimumSafeDeadline);
            complaint.setSlaWarningSentAt(null);
        }
        complaint.setUpdatedAt(now);

        String historyNote = "Complaint de-escalated (previously escalated at " + oldEscalatedAt +
                "). Grace period extended to " + complaint.getSlaDueAt() + ". Explanation: " + req.getReason().trim();
        complaint.addStatusHistory(complaint.getStatus(), complaint.getStatus(), actor, historyNote);

        Complaint saved = complaintRepository.save(complaint);
        complaintRepository.flush();

        auditService.logAction(
                actor.getId(),
                actor.getFullName(),
                principal.getAuthorities().iterator().next().getAuthority(),
                "DE_ESCALATE",
                "COMPLAINT",
                saved.getId().toString(),
                "De-escalated complaint #" + saved.getReferenceId() + ". Explanation: " + req.getReason().trim(),
                ipAddress,
                "SUCCESS"
        );

        log.info("Supervisor {} de-escalated complaint #{} with grace period to {}",
                actor.getEmail(), saved.getReferenceId(), complaint.getSlaDueAt());

        return ComplaintResponse.fromEntity(saved, principal, false);
    }

    @Transactional(readOnly = true)
    public List<SupervisorWorkerResponse> getActiveWorkers() {
        return userRepository.findActiveUsersByRoleName(RoleName.WORKER).stream()
                .map(this::toWorkerResponseWithWorkload)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SupervisorWorkerResponse> getActiveWorkers(UserPrincipal principal, String departmentFilter) {
        if (principal == null) {
            throw new ForbiddenException("Authentication required.");
        }
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean hasCrossDept = principal.hasActiveCrossDepartmentAccess();

        String effectiveDept = null;
        if (isAdmin || hasCrossDept) {
            effectiveDept = (departmentFilter != null && !departmentFilter.isBlank() && !departmentFilter.equalsIgnoreCase("ALL"))
                    ? departmentFilter.trim() : null;
        } else {
            String supervisorDept = principal.getDepartment();
            if (supervisorDept == null || supervisorDept.isBlank()) {
                throw new ForbiddenException("Supervisor has no assigned department. Access denied.");
            }
            if (departmentFilter != null && !departmentFilter.isBlank() && !departmentFilter.equalsIgnoreCase("ALL")) {
                if (!supervisorDept.trim().equalsIgnoreCase(departmentFilter.trim())) {
                    throw new ForbiddenException("Supervisors cannot query workers outside their authorized department (" + supervisorDept + ").");
                }
            }
            effectiveDept = supervisorDept.trim();
        }

        List<User> workers;
        if (effectiveDept != null) {
            workers = userRepository.findActiveUsersByRoleNameAndDepartment(RoleName.WORKER, effectiveDept);
        } else {
            workers = userRepository.findActiveUsersByRoleName(RoleName.WORKER);
        }

        return workers.stream()
                .map(this::toWorkerResponseWithWorkload)
                .toList();
    }

    private SupervisorWorkerResponse toWorkerResponseWithWorkload(User u) {
        SupervisorWorkerResponse resp = SupervisorWorkerResponse.fromEntity(u);
        if (resp == null || u == null) return resp;

        if (complaintRepository != null) {
            OffsetDateTime now = OffsetDateTime.now();
            long active = complaintRepository.countByAssignedWorkerIdAndStatusIn(
                    u.getId(),
                    List.of(
                            com.civicfix.complaint.ComplaintStatus.ASSIGNED,
                            com.civicfix.complaint.ComplaintStatus.IN_PROGRESS,
                            com.civicfix.complaint.ComplaintStatus.REWORK_REQUESTED
                    )
            );
            long pending = complaintRepository.countByAssignedWorkerIdAndStatus(
                    u.getId(), com.civicfix.complaint.ComplaintStatus.ASSIGNED);
            long inProgress = complaintRepository.countByAssignedWorkerIdAndStatus(
                    u.getId(), com.civicfix.complaint.ComplaintStatus.IN_PROGRESS);
            long completed = complaintRepository.countByAssignedWorkerIdAndStatusIn(
                    u.getId(),
                    List.of(
                            com.civicfix.complaint.ComplaintStatus.RESOLUTION_SUBMITTED,
                            com.civicfix.complaint.ComplaintStatus.PENDING_CITIZEN_CONFIRMATION,
                            com.civicfix.complaint.ComplaintStatus.RESOLVED
                    )
            );
            long overdue = complaintRepository.countOverdueByAssignedWorkerId(u.getId(), now);

            resp.setActiveOrdersCount(active);
            resp.setPendingOrdersCount(pending);
            resp.setInProgressOrdersCount(inProgress);
            resp.setCompletedOrdersCount(completed);
            resp.setOverdueOrdersCount(overdue);
        }

        return resp;
    }

    private Complaint resolveComplaint(String idOrRef) {
        UUID uuid = null;
        try {
            uuid = UUID.fromString(idOrRef);
        } catch (IllegalArgumentException ignored) {}

        if (uuid != null) {
            return complaintRepository.findById(uuid)
                    .orElseThrow(() -> new ResourceNotFoundException("Complaint not found with id: " + idOrRef));
        } else {
            return complaintRepository.findByReferenceIdIgnoreCase(idOrRef)
                    .orElseThrow(() -> new ResourceNotFoundException("Complaint not found with reference: " + idOrRef));
        }
    }

    /**
     * Canonical department-to-category whitelist.
     * Uses exact, normalized department strings rather than loose substring checks.
     */
    private static final java.util.Map<String, java.util.Set<String>> DEPARTMENT_CATEGORY_MAP = java.util.Map.ofEntries(
            java.util.Map.entry("sdot road maintenance", java.util.Set.of("ROADS")),
            java.util.Map.entry("sdot rapid asphalt maintenance", java.util.Set.of("ROADS")),
            java.util.Map.entry("sdot road crew", java.util.Set.of("ROADS")),
            java.util.Map.entry("road infrastructure", java.util.Set.of("ROADS")),
            java.util.Map.entry("water supply & sewerage board", java.util.Set.of("WATER_SEWERAGE", "DRAINAGE")),
            java.util.Map.entry("metro water & sewerage board", java.util.Set.of("WATER_SEWERAGE", "DRAINAGE")),
            java.util.Map.entry("water & sewerage board", java.util.Set.of("WATER_SEWERAGE", "DRAINAGE")),
            java.util.Map.entry("metro water and drainage", java.util.Set.of("WATER_SEWERAGE", "DRAINAGE")),
            java.util.Map.entry("stormwater drainage department", java.util.Set.of("DRAINAGE")),
            java.util.Map.entry("drainage department", java.util.Set.of("DRAINAGE")),
            java.util.Map.entry("electrical & street lighting", java.util.Set.of("STREET_LIGHT")),
            java.util.Map.entry("street lighting & electrical", java.util.Set.of("STREET_LIGHT")),
            java.util.Map.entry("solid waste management & sanitation", java.util.Set.of("GARBAGE")),
            java.util.Map.entry("solid waste management", java.util.Set.of("GARBAGE")),
            java.util.Map.entry("parks & recreation department", java.util.Set.of("PARKS")),
            java.util.Map.entry("public transportation authority", java.util.Set.of("PUBLIC_TRANSPORT"))
    );

    public static boolean isCrossDepartmentSupervisor(String department) {
        // Deprecated substring-based check: callers should use principal.hasActiveCrossDepartmentAccess()
        return false;
    }

    public static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) return "";
        String upper = category.toUpperCase().trim();
        if (upper.equals("ROADS") || upper.contains("ROAD") || upper.contains("POTHOLE")) {
            return "ROADS";
        }
        if (upper.equals("WATER_SEWERAGE") || upper.contains("WATER") || upper.contains("SEWER")) {
            return "WATER_SEWERAGE";
        }
        if (upper.equals("DRAINAGE") || upper.contains("DRAIN")) {
            return "DRAINAGE";
        }
        if (upper.equals("STREET_LIGHT") || upper.contains("LIGHT") || upper.contains("ELECTRIC")) {
            return "STREET_LIGHT";
        }
        if (upper.equals("GARBAGE") || upper.contains("GARBAGE") || upper.contains("WASTE") || upper.contains("SANITATION")) {
            return "GARBAGE";
        }
        if (upper.equals("PARKS") || upper.contains("PARK")) {
            return "PARKS";
        }
        if (upper.equals("PUBLIC_TRANSPORT") || upper.contains("TRANSPORT")) {
            return "PUBLIC_TRANSPORT";
        }
        return upper;
    }

    public static boolean isCategoryAllowedForDepartment(String category, String department) {
        return isCategoryAllowedForDepartment(category, department, null);
    }

    public static boolean isCategoryAllowedForDepartment(String category, String department, UserPrincipal principal) {
        if (principal != null && (principal.hasActiveCrossDepartmentAccess() ||
                principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")))) {
            return true;
        }
        if (category == null || category.isBlank() || department == null || department.isBlank()) {
            return false; // Fail closed!
        }
        String deptKey = department.trim().toLowerCase();
        java.util.Set<String> allowedCategories = DEPARTMENT_CATEGORY_MAP.get(deptKey);
        if (allowedCategories == null) {
            return false; // Unknown or unauthorized department -> fail closed!
        }
        String normCategory = normalizeCategory(category);
        return allowedCategories.contains(normCategory);
    }

    public static String getDefaultCategoryForDepartment(String department) {
        if (department == null || department.isBlank()) return null;
        String deptKey = department.trim().toLowerCase();
        java.util.Set<String> allowed = DEPARTMENT_CATEGORY_MAP.get(deptKey);
        if (allowed != null && !allowed.isEmpty()) {
            return allowed.iterator().next();
        }
        return null;
    }

    public void enforceSupervisorDepartmentAccess(Complaint complaint, UserPrincipal principal) {
        if (complaint == null) return;
        if (principal == null) {
            throw new ForbiddenException("Authentication required.");
        }
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin || principal.hasActiveCrossDepartmentAccess()) {
            return;
        }

        String dept = principal.getDepartment();
        if (dept == null || dept.isBlank()) {
            throw new ForbiddenException("Supervisor has no assigned department. Access denied.");
        }

        if (!isCategoryAllowedForDepartment(complaint.getCategory(), dept, principal)) {
            throw new ForbiddenException("Supervisors cannot manage complaints outside their authorized department (" + dept + ").");
        }
    }
}
