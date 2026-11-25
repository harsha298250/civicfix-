package com.civicfix.auth;

import com.civicfix.user.Role;
import com.civicfix.user.RoleName;
import com.civicfix.user.RoleRepository;
import com.civicfix.user.User;
import com.civicfix.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * AdminBootstrapRunner
 *
 * Dynamically and securely provisions the initial platform administrator in production
 * using the configured environment variable INITIAL_ADMIN_EMAIL without hardcoding
 * personal email addresses in Git or reusing compromised passwords.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final StaffActivationService staffActivationService;

    @Value("${civicfix.admin.initial-email:${INITIAL_ADMIN_EMAIL:}}")
    private String initialAdminEmail;

    @Value("${civicfix.admin.initial-password:${INITIAL_ADMIN_PASSWORD:}}")
    private String initialAdminPassword;

    @Autowired
    public AdminBootstrapRunner(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            @Autowired(required = false) StaffActivationService staffActivationService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.staffActivationService = staffActivationService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (initialAdminEmail != null && !initialAdminEmail.isBlank()) {
            String email = initialAdminEmail.trim().toLowerCase();
            String masked = maskEmail(email);

            Role adminRole = roleRepository.findByName(RoleName.ADMIN)
                    .orElseGet(() -> roleRepository.save(new Role(RoleName.ADMIN)));

            User existing = userRepository.findByEmailIgnoreCase(email).orElse(null);
            if (existing != null) {
                if (!"ACTIVE".equalsIgnoreCase(existing.getAccountStatus())) {
                    log.warn("[ADMIN_BOOTSTRAP] Cannot grant administrator role to user {} because account status is {}.", masked, existing.getAccountStatus());
                    return;
                }
                boolean hasAdminRole = existing.getRoles().stream().anyMatch(r -> r.getName() == RoleName.ADMIN);
                if (!hasAdminRole) {
                    Set<Role> roles = new HashSet<>(existing.getRoles());
                    roles.add(adminRole);
                    existing.setRoles(roles);
                    existing.setEmailVerified(true);
                    existing.setCrossWardAccess(true);
                    existing.setCrossDepartmentAccess(true);
                    userRepository.save(existing);
                    log.info("[ADMIN_BOOTSTRAP] Granted ROLE_ADMIN to existing user {}", masked);
                } else {
                    log.info("[ADMIN_BOOTSTRAP] Administrator account {} already exists with active role.", masked);
                }
            } else {
                User admin = new User();
                admin.setFullName("Platform Administrator");
                admin.setEmail(email);
                admin.setAccountStatus("ACTIVE");
                admin.setEmailVerified(true);
                admin.setTrustScore(100);
                admin.setCrossWardAccess(true);
                admin.setCrossDepartmentAccess(true);
                admin.setDepartment("Platform Security & Governance");
                admin.setWard("Greater Chennai Corporation Headquarters");
                admin.setRoles(Set.of(adminRole));

                if (initialAdminPassword != null && !initialAdminPassword.isBlank()) {
                    admin.setPasswordHash(passwordEncoder.encode(initialAdminPassword.trim()));
                    userRepository.save(admin);
                    log.info("[ADMIN_BOOTSTRAP] Successfully provisioned administrator account for {} with configured initial password.", masked);
                } else {
                    admin.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
                    User savedAdmin = userRepository.save(admin);
                    if (staffActivationService != null) {
                        try {
                            staffActivationService.createActivationToken(savedAdmin, null);
                            log.info("[ADMIN_BOOTSTRAP] Provisioned administrator {} and dispatched activation email.", masked);
                        } catch (Exception ex) {
                            log.warn("[ADMIN_BOOTSTRAP] Created administrator {} but activation email dispatch encountered non-fatal error: {}", masked, ex.getMessage());
                        }
                    } else {
                        log.info("[ADMIN_BOOTSTRAP] Provisioned administrator account for {}.", masked);
                    }
                }
            }
        } else {
            long adminCount = userRepository.countByRoleName(RoleName.ADMIN);
            if (adminCount == 0) {
                log.warn("[ADMIN_BOOTSTRAP] No platform administrator exists in the database. Set INITIAL_ADMIN_EMAIL in your environment (e.g. Render Dashboard) to activate your production administrator.");
            }
        }
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@", 2);
        String name = parts[0];
        String domain = parts[1];
        if (name.length() <= 2) return name.charAt(0) + "***@" + domain;
        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + "@" + domain;
    }
}
