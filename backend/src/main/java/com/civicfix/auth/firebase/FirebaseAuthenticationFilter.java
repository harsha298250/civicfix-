package com.civicfix.auth.firebase;

import com.civicfix.audit.AuditService;
import com.civicfix.common.exception.UnauthorizedException;
import com.civicfix.mail.EmailOutboxService;
import com.civicfix.security.UserPrincipal;
import com.civicfix.security.ratelimit.ClientIpResolver;
import com.civicfix.user.Role;
import com.civicfix.user.RoleName;
import com.civicfix.user.RoleRepository;
import com.civicfix.user.User;
import com.civicfix.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * FirebaseAuthenticationFilter
 *
 * Intercepts incoming HTTP requests, extracts the Firebase ID token from the Authorization header
 * ("Bearer <token>"), verifies signature, issuer, audience, and expiry via Firebase Admin SDK,
 * maps the verified Firebase UID to the authoritative CivicFix user record in Neon PostgreSQL,
 * and sets the authenticated UserPrincipal into Spring Security's SecurityContext.
 *
 * If no Bearer token is provided, the filter allows the request to proceed so that session-based
 * authentication and public endpoints function seamlessly without regression.
 */
@Component
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthenticationFilter.class);

    private final FirebaseTokenVerifier tokenVerifier;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ClientIpResolver clientIpResolver;
    private final AuditService auditService;
    private final EmailOutboxService emailOutboxService;

    @Autowired
    public FirebaseAuthenticationFilter(
            FirebaseTokenVerifier tokenVerifier,
            UserRepository userRepository,
            RoleRepository roleRepository,
            @Lazy PasswordEncoder passwordEncoder,
            ClientIpResolver clientIpResolver,
            AuditService auditService,
            @Autowired(required = false) EmailOutboxService emailOutboxService
    ) {
        this.tokenVerifier = tokenVerifier;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.clientIpResolver = clientIpResolver;
        this.auditService = auditService;
        this.emailOutboxService = emailOutboxService;
    }

    public FirebaseAuthenticationFilter(
            FirebaseTokenVerifier tokenVerifier,
            UserRepository userRepository,
            RoleRepository roleRepository,
            @Lazy PasswordEncoder passwordEncoder,
            ClientIpResolver clientIpResolver,
            AuditService auditService
    ) {
        this(tokenVerifier, userRepository, roleRepository, passwordEncoder, clientIpResolver, auditService, null);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (!token.isEmpty()) {
                try {
                    FirebaseTokenClaims claims = tokenVerifier.verifyIdToken(token);
                    if (claims != null && claims.getUid() != null && !claims.getUid().isBlank()) {
                        User user = resolveOrCreateUser(claims, request);
                        if (user != null && "ACTIVE".equalsIgnoreCase(user.getAccountStatus())) {
                            UserPrincipal principal = new UserPrincipal(user);
                            UsernamePasswordAuthenticationToken authToken =
                                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authToken);
                        } else if (user != null && !"ACTIVE".equalsIgnoreCase(user.getAccountStatus())) {
                            log.warn("[FIREBASE_FILTER] Inactive or disabled user attempted access: {}", user.getId());
                        }
                    }
                } catch (UnauthorizedException ex) {
                    log.warn("[FIREBASE_FILTER] Token validation rejected: {}", ex.getMessage());
                } catch (Exception ex) {
                    log.error("[FIREBASE_FILTER] Unexpected error during token validation: {}", ex.getMessage());
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private User resolveOrCreateUser(FirebaseTokenClaims claims, HttpServletRequest request) {
        String uid = claims.getUid().trim();
        String email = claims.getEmail() != null ? claims.getEmail().trim().toLowerCase() : null;
        boolean emailVerified = claims.isEmailVerified();
        String clientIp = clientIpResolver.resolveClientIp(request);

        // 1. Direct lookup by Firebase UID mapping
        Optional<User> byUid = userRepository.findByFirebaseUid(uid);
        if (byUid.isPresent()) {
            User existing = byUid.get();
            if (emailVerified && !existing.isEmailVerified()) {
                existing.setEmailVerified(true);
                userRepository.save(existing);
            }
            return existing;
        }

        // 2. Lookup by email for safe account linking
        if (email != null && !email.isBlank()) {
            Optional<User> byEmail = userRepository.findByEmailIgnoreCase(email);
            if (byEmail.isPresent()) {
                User existing = byEmail.get();

                // Strict Security Rule: Never link an account using an unverified Firebase email address
                if (!emailVerified) {
                    log.warn("[FIREBASE_FILTER] Rejected linking of account {} because Firebase email is unverified.", email);
                    return null;
                }

                // Prevent linking if already linked to a different Firebase identity
                if (existing.getFirebaseUid() != null && !existing.getFirebaseUid().equals(uid)) {
                    log.warn("[FIREBASE_FILTER] Account {} is already bound to a different Firebase UID.", email);
                    return null;
                }

                // Link identity while preserving all existing roles (ADMIN, OFFICER, etc.)
                existing.setFirebaseUid(uid);
                existing.setEmailVerified(true);
                User linkedUser = userRepository.save(existing);

                auditService.logAction(
                        linkedUser.getId(),
                        linkedUser.getFullName(),
                        resolvePrimaryRole(linkedUser),
                        "AUTH_FIREBASE_FILTER_LINK",
                        "USER",
                        linkedUser.getId().toString(),
                        "Linked Firebase UID to existing account via Bearer token verification.",
                        clientIp,
                        "SUCCESS"
                );
                return linkedUser;
            }

            // 3. Auto-provision new Citizen account in Neon PostgreSQL
            Role citizenRole = roleRepository.findByName(RoleName.CITIZEN)
                    .orElseGet(() -> roleRepository.save(new Role(RoleName.CITIZEN)));

            String name = claims.getName() != null && !claims.getName().isBlank()
                    ? claims.getName().trim()
                    : email.split("@")[0];

            User newUser = new User();
            newUser.setFullName(name);
            newUser.setEmail(email);
            newUser.setWard(null);
            newUser.setDepartment("Resident Community Member");
            newUser.setAccountStatus("ACTIVE");
            newUser.setEmailVerified(emailVerified);
            newUser.setTrustScore(90);
            newUser.setFirebaseUid(uid);
            newUser.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            newUser.setRoles(Set.of(citizenRole));

            User saved = userRepository.save(newUser);

            if (emailOutboxService != null && saved.getEmail() != null) {
                try {
                    emailOutboxService.enqueueEmail(
                            "welcome_" + saved.getId(),
                            saved.getEmail(),
                            "Welcome to CivicFix — Citizen Incident Platform",
                            "WELCOME",
                            Map.of("name", saved.getFullName() != null ? saved.getFullName() : "Citizen")
                    );
                } catch (Exception ex) {
                    log.warn("[FIREBASE_FILTER] Non-fatal error enqueuing welcome notification: {}", ex.getMessage());
                }
            }

            auditService.logAction(
                    saved.getId(),
                    saved.getFullName(),
                    "CITIZEN",
                    "AUTH_FIREBASE_FILTER_REGISTER",
                    "USER",
                    saved.getId().toString(),
                    "Provisioned new citizen account from verified Firebase Bearer token.",
                    clientIp,
                    "SUCCESS"
            );
            return saved;
        }

        return null;
    }

    private String resolvePrimaryRole(User user) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) return "CITIZEN";
        if (user.hasRole(RoleName.ADMIN)) return "ADMIN";
        if (user.hasRole(RoleName.SUPERVISOR)) return "SUPERVISOR";
        if (user.hasRole(RoleName.OFFICER)) return "OFFICER";
        if (user.hasRole(RoleName.WORKER)) return "WORKER";
        return "CITIZEN";
    }
}
