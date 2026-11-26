package com.civicfix.auth.firebase;

import com.civicfix.audit.AuditService;
import com.civicfix.auth.dto.AuthUserResponse;
import com.civicfix.auth.dto.FirebaseSessionRequest;
import com.civicfix.common.exception.BadRequestException;
import com.civicfix.common.exception.ConflictException;
import com.civicfix.common.exception.ForbiddenException;
import com.civicfix.common.exception.UnauthorizedException;
import com.civicfix.mail.EmailOutboxService;
import com.civicfix.security.UserPrincipal;
import com.civicfix.security.ratelimit.ClientIpResolver;
import com.civicfix.user.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class FirebaseAuthService {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final FirebaseTokenVerifier tokenVerifier;
    private final ClientIpResolver clientIpResolver;
    private final EmailOutboxService emailOutboxService;

    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    @Autowired
    public FirebaseAuthService(UserRepository userRepository,
                               RoleRepository roleRepository,
                               PasswordEncoder passwordEncoder,
                               AuditService auditService,
                               FirebaseTokenVerifier tokenVerifier,
                               ClientIpResolver clientIpResolver,
                               @Autowired(required = false) EmailOutboxService emailOutboxService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.tokenVerifier = tokenVerifier;
        this.clientIpResolver = clientIpResolver;
        this.emailOutboxService = emailOutboxService;
    }

    public FirebaseAuthService(UserRepository userRepository,
                               RoleRepository roleRepository,
                               PasswordEncoder passwordEncoder,
                               AuditService auditService,
                               FirebaseTokenVerifier tokenVerifier,
                               ClientIpResolver clientIpResolver) {
        this(userRepository, roleRepository, passwordEncoder, auditService, tokenVerifier, clientIpResolver, null);
    }

    @Transactional
    public AuthUserResponse authenticateFirebaseUser(FirebaseSessionRequest request,
                                                    HttpServletRequest httpRequest,
                                                    HttpServletResponse httpResponse) {
        if (request == null || request.getIdToken() == null || request.getIdToken().isBlank()) {
            throw new BadRequestException("Firebase ID token must not be null or blank.");
        }

        String clientIp = httpRequest != null ? clientIpResolver.resolveClientIp(httpRequest) : "unknown";

        // 1. Verify token server-side via Firebase Admin SDK
        FirebaseTokenClaims claims = tokenVerifier.verifyIdToken(request.getIdToken().trim());
        if (claims == null || claims.getUid() == null || claims.getUid().isBlank()) {
            throw new UnauthorizedException("Invalid Firebase authentication: missing user identifier.");
        }

        String uid = claims.getUid().trim();
        String email = claims.getEmail() != null ? claims.getEmail().trim().toLowerCase() : null;
        boolean emailVerified = claims.isEmailVerified();

        // 2. Lookup existing user by Firebase UID mapping
        Optional<User> userByUid = userRepository.findByFirebaseUid(uid);
        if (userByUid.isPresent()) {
            User user = userByUid.get();
            if (!emailVerified) {
                throw new BadRequestException("Your email address is not verified. Please verify your email with Firebase before signing in.");
            }
            boolean changed = false;
            if (!user.isEmailVerified()) {
                user.setEmailVerified(true);
                changed = true;
            }
            if ("PENDING_VERIFICATION".equalsIgnoreCase(user.getAccountStatus())) {
                user.setAccountStatus("ACTIVE");
                changed = true;
            }
            if (changed) {
                userRepository.save(user);
            }
            if (!"ACTIVE".equalsIgnoreCase(user.getAccountStatus())) {
                throw new BadRequestException("Your account is pending activation or disabled. Please contact your municipal administrator.");
            }

            establishSession(user, httpRequest, httpResponse, Boolean.TRUE.equals(request.getRememberMe()));

            auditService.logAction(
                    user.getId(),
                    user.getFullName(),
                    resolvePrimaryRole(user),
                    "AUTH_FIREBASE_LOGIN",
                    "SESSION",
                    httpRequest != null && httpRequest.getSession(false) != null ? httpRequest.getSession(false).getId() : "NONE",
                    "Successful authentication via existing Firebase identity.",
                    clientIp,
                    "SUCCESS"
            );

            return AuthUserResponse.fromEntity(user);
        }

        // 3. If not matched by Firebase UID, check for email-based account linking or registration
        if (email == null || email.isBlank()) {
            throw new BadRequestException("A valid email address is required from Firebase authentication.");
        }

        Optional<User> existingUserByEmail = userRepository.findByEmailIgnoreCase(email);
        if (existingUserByEmail.isPresent()) {
            User user = existingUserByEmail.get();

            // Security Rule: Reject unverified emails from linking existing accounts (prevention of account takeover)
            if (!emailVerified) {
                throw new BadRequestException("Cannot link existing account: Firebase email is not verified. Please verify your email with Firebase before signing in.");
            }

            // Security Rule: Duplicate identity prevention
            if (user.getFirebaseUid() != null && !user.getFirebaseUid().equals(uid)) {
                throw new ConflictException("This account is already linked to a different Firebase identity.");
            }

            // Account status validation
            if (!"ACTIVE".equalsIgnoreCase(user.getAccountStatus())) {
                throw new BadRequestException("Your account is pending activation or disabled. Please contact your municipal administrator.");
            }

            // Link existing account while strictly preserving existing roles (ADMIN, OFFICER, SUPERVISOR, WORKER, CITIZEN)
            user.setFirebaseUid(uid);
            user.setEmailVerified(true);
            User savedUser = userRepository.save(user);

            establishSession(savedUser, httpRequest, httpResponse, Boolean.TRUE.equals(request.getRememberMe()));

            String primaryRole = resolvePrimaryRole(savedUser);
            auditService.logAction(
                    savedUser.getId(),
                    savedUser.getFullName(),
                    primaryRole,
                    "AUTH_FIREBASE_LINK",
                    "SESSION",
                    httpRequest != null && httpRequest.getSession(false) != null ? httpRequest.getSession(false).getId() : "NONE",
                    primaryRole + " account linked to verified Firebase identity.",
                    clientIp,
                    "SUCCESS"
            );

            return AuthUserResponse.fromEntity(savedUser);
        }

        // 4. Create new Citizen account in Neon PostgreSQL
        Role citizenRole = roleRepository.findByName(RoleName.CITIZEN)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.CITIZEN)));

        String name = request.getName() != null && !request.getName().isBlank()
                ? request.getName().trim()
                : (claims.getName() != null && !claims.getName().isBlank() ? claims.getName().trim() : email.split("@")[0]);

        String ward = request.getWard() != null && !request.getWard().isBlank()
                ? request.getWard().trim()
                : null;

        String phone = request.getPhone() != null && !request.getPhone().isBlank()
                ? request.getPhone().trim()
                : null;

        if (phone != null && userRepository.existsByPhone(phone)) {
            throw new ConflictException("An account with these contact details already exists.");
        }

        User newUser = new User();
        newUser.setFullName(name);
        newUser.setEmail(email);
        newUser.setPhone(phone);
        newUser.setWard(ward);
        newUser.setDepartment("Resident Community Member");
        newUser.setAccountStatus(emailVerified ? "ACTIVE" : "PENDING_VERIFICATION");
        newUser.setEmailVerified(emailVerified);
        newUser.setTrustScore(90);
        newUser.setFirebaseUid(uid);
        // Generate cryptographically secure unguessable random password hash so legacy password login is disabled until reset
        newUser.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        newUser.setRoles(Set.of(citizenRole));

        User savedUser = userRepository.save(newUser);

        if (emailOutboxService != null && savedUser.getEmail() != null) {
            try {
                emailOutboxService.enqueueEmail(
                        "welcome_" + savedUser.getId(),
                        savedUser.getEmail(),
                        "Welcome to CivicFix — Citizen Incident Platform",
                        "WELCOME",
                        Map.of("name", savedUser.getFullName() != null ? savedUser.getFullName() : "Citizen")
                );
            } catch (Exception ex) {
                log.warn("[FIREBASE_AUTH] Non-fatal error enqueuing welcome notification: {}", ex.getMessage());
            }
        }

        if (emailVerified) {
            establishSession(savedUser, httpRequest, httpResponse, Boolean.TRUE.equals(request.getRememberMe()));
        }

        auditService.logAction(
                savedUser.getId(),
                savedUser.getFullName(),
                "CITIZEN",
                "AUTH_FIREBASE_REGISTER",
                "SESSION",
                httpRequest != null && httpRequest.getSession(false) != null ? httpRequest.getSession(false).getId() : "NONE",
                "Citizen registered via Firebase authentication.",
                clientIp,
                "SUCCESS"
        );

        return AuthUserResponse.fromEntity(savedUser);
    }

    private void establishSession(User user, HttpServletRequest httpRequest, HttpServletResponse httpResponse, boolean rememberMe) {
        if (httpRequest == null || httpResponse == null) {
            return;
        }
        UserPrincipal principal = new UserPrincipal(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
        if (httpRequest.getSession(false) != null) {
            httpRequest.getSession(false).setMaxInactiveInterval(rememberMe ? 30 * 24 * 3600 : 3600);
        }
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
