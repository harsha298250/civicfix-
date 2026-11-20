package com.civicfix.auth;

import com.civicfix.audit.AuditService;
import com.civicfix.auth.dto.AuthUserResponse;
import com.civicfix.auth.dto.ForgotPasswordRequest;
import com.civicfix.auth.dto.LoginRequest;
import com.civicfix.auth.dto.RegisterCitizenRequest;
import com.civicfix.auth.dto.ResetPasswordRequest;
import com.civicfix.common.exception.BadRequestException;
import com.civicfix.common.exception.ConflictException;
import com.civicfix.common.exception.RateLimitExceededException;
import com.civicfix.common.exception.ResourceNotFoundException;
import com.civicfix.mail.EmailService;
import com.civicfix.security.UserPrincipal;
import com.civicfix.security.ratelimit.ClientIpResolver;
import com.civicfix.security.ratelimit.LoginLockoutService;
import com.civicfix.user.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final AuditService auditService;
    private final LoginLockoutService loginLockoutService;
    private final ClientIpResolver clientIpResolver;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private SessionRegistry sessionRegistry;
    private final SecureRandom secureRandom = new SecureRandom();
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    @org.springframework.beans.factory.annotation.Value("${auth.password-reset.token-validity-minutes:15}")
    private int passwordResetValidityMinutes = 15;

    @org.springframework.beans.factory.annotation.Value("${auth.password-reset.account-cooldown-minutes:5}")
    private int passwordResetAccountCooldownMinutes = 5;

    @org.springframework.beans.factory.annotation.Value("${auth.password-reset.app-base-url:http://localhost:5173}")
    private String appBaseUrl = "http://localhost:5173";

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       AuditService auditService,
                       LoginLockoutService loginLockoutService,
                       ClientIpResolver clientIpResolver) {
        this(userRepository, roleRepository, passwordEncoder, authenticationManager, auditService,
             loginLockoutService, clientIpResolver, null, null, null);
    }

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       AuditService auditService,
                       LoginLockoutService loginLockoutService,
                       ClientIpResolver clientIpResolver,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       EmailService emailService) {
        this(userRepository, roleRepository, passwordEncoder, authenticationManager, auditService,
             loginLockoutService, clientIpResolver, passwordResetTokenRepository, emailService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       AuditService auditService,
                       LoginLockoutService loginLockoutService,
                       ClientIpResolver clientIpResolver,
                       @org.springframework.beans.factory.annotation.Autowired(required = false) PasswordResetTokenRepository passwordResetTokenRepository,
                       @org.springframework.beans.factory.annotation.Autowired(required = false) EmailService emailService,
                       @org.springframework.beans.factory.annotation.Autowired(required = false) SessionRegistry sessionRegistry) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.auditService = auditService;
        this.loginLockoutService = loginLockoutService;
        this.clientIpResolver = clientIpResolver;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailService = emailService;
        this.sessionRegistry = sessionRegistry;
    }

    @Transactional
    public AuthUserResponse registerCitizen(RegisterCitizenRequest request, String ipAddress) {
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : null;
        String phone = request.getPhone() != null ? request.getPhone().trim() : null;

        if ((email == null || email.isEmpty()) && (phone == null || phone.isEmpty())) {
            throw new BadRequestException("At least one contact method (email or phone number) must be provided.");
        }

        // Duplicate checks
        if (email != null && !email.isEmpty() && userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account is already registered with this email address.");
        }

        if (phone != null && !phone.isEmpty() && userRepository.existsByPhone(phone)) {
            throw new ConflictException("An account is already registered with this phone number.");
        }

        // Strictly assign CITIZEN role
        Role citizenRole = roleRepository.findByName(RoleName.CITIZEN)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.CITIZEN)));

        User user = new User();
        user.setFullName(request.getName().trim());
        user.setEmail(email != null && !email.isEmpty() ? email : null);
        user.setPhone(phone != null && !phone.isEmpty() ? phone : null);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setWard(request.getWard() != null ? request.getWard().trim() : "Ward 08, Anna Nagar, Chennai");
        user.setDepartment("Resident Community Member");
        user.setAccountStatus("ACTIVE");
        user.setTrustScore(90);
        user.setRoles(Set.of(citizenRole));

        User savedUser = userRepository.save(user);

        auditService.logAction(
                savedUser.getId(),
                savedUser.getFullName(),
                "CITIZEN",
                "AUTH_REGISTER",
                "USER",
                savedUser.getId().toString(),
                "Citizen self-registration completed.",
                ipAddress,
                "SUCCESS"
        );

        return AuthUserResponse.fromEntity(savedUser);
    }

    @Transactional(noRollbackFor = {BadCredentialsException.class, RateLimitExceededException.class})
    public AuthUserResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String identifier = request.getIdentifier().trim();
        String ipAddress = clientIpResolver.resolveClientIp(httpRequest);

        // 1. Acquire PostgreSQL-backed coordination advisory locks in strict order (Account first, IP second).
        // Coordinates lockout check, authentication, and counter recording across multiple backend instances.
        loginLockoutService.acquireCoordinationLocks(identifier, ipAddress);

        // 2. Enforce account / IP lockout before attempting authentication
        loginLockoutService.checkLockout(identifier, ipAddress);

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(identifier, request.getPassword())
            );
        } catch (AuthenticationException ex) {
            loginLockoutService.recordFailedAttempt(identifier, ipAddress);
            throw new BadCredentialsException("Invalid credentials. Please verify your email or phone and password.");
        }

        // On successful authentication, clear failed login attempts for this identifier + IP
        loginLockoutService.recordSuccessfulLogin(identifier, ipAddress);

        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        auditService.logAction(
                user.getId(),
                user.getFullName(),
                principal.getAuthorities().iterator().next().getAuthority(),
                "AUTH_LOGIN",
                "SESSION",
                httpRequest.getSession().getId(),
                "Successful user authentication.",
                ipAddress,
                "SUCCESS"
        );

        return AuthUserResponse.fromEntity(user);
    }

    @Transactional(readOnly = true)
    public AuthUserResponse getCurrentUser(UserPrincipal principal) {
        if (principal == null) {
            throw new ResourceNotFoundException("No active session found.");
        }
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found in system."));
        return AuthUserResponse.fromEntity(user);
    }

    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse, UserPrincipal principal) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        if (principal != null) {
            auditService.logAction(
                    principal.getId(),
                    principal.getFullName(),
                    principal.getAuthorities().isEmpty() ? "ANONYMOUS" : principal.getAuthorities().iterator().next().getAuthority(),
                    "AUTH_LOGOUT",
                    "SESSION",
                    session != null ? session.getId() : "NONE",
                    "User signed out of session.",
                    httpRequest.getRemoteAddr(),
                    "SUCCESS"
            );
        }
    }

    @Transactional
    public String requestPasswordReset(ForgotPasswordRequest request, String clientIp) {
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        if (!email.isEmpty()) {
            try {
                // Serialized lookup prevents concurrent race conditions bypassing rate limit
                java.util.Optional<User> userOpt = userRepository.findByEmailIgnoreCaseWithLock(email);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    // Suspended, deactivated, or non-active accounts must not receive password reset tokens
                    if (!"ACTIVE".equalsIgnoreCase(user.getAccountStatus())) {
                        log.warn("Password reset requested for non-active account. Suppressing token dispatch.");
                        return "If an account with that email exists, password reset instructions have been sent.";
                    }

                    if (passwordResetTokenRepository != null && emailService != null) {
                        // Persistent Account-Level Rate Limiting: Max 1 reset email per account every 5 minutes
                        int cooldownMinutes = passwordResetAccountCooldownMinutes > 0 ? passwordResetAccountCooldownMinutes : 5;
                        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(cooldownMinutes);
                        long recentTokens = passwordResetTokenRepository.countRecentTokensForUser(user, cutoff);
                        if (recentTokens > 0) {
                            log.warn("Password reset throttled for account. Cooldown period of {} minutes active.", cooldownMinutes);
                            // Return uniform generic response - prevent account enumeration and timing leakage
                            return "If an account with that email exists, password reset instructions have been sent.";
                        }

                        // Invalidate existing outstanding reset tokens for this user
                        passwordResetTokenRepository.invalidateOutstandingTokensForUser(user, OffsetDateTime.now());

                        // Generate cryptographically secure random token (32 bytes entropy)
                        byte[] randomBytes = new byte[32];
                        secureRandom.nextBytes(randomBytes);
                        String rawToken = HexFormat.of().formatHex(randomBytes);

                        String tokenHash = hashToken(rawToken);
                        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(
                                passwordResetValidityMinutes > 0 ? passwordResetValidityMinutes : 15);

                        PasswordResetToken token = new PasswordResetToken(user, tokenHash, expiresAt, clientIp);
                        passwordResetTokenRepository.save(token);

                        String resetUrl = appBaseUrl + "/reset-password?token=" + rawToken;
                        emailService.sendPasswordResetEmail(user.getEmail(), resetUrl);

                        auditService.logAction(
                                user.getId(),
                                user.getFullName(),
                                "CITIZEN",
                                "PASSWORD_RESET_REQUESTED",
                                "USER",
                                user.getId().toString(),
                                "Password recovery token generated and sent to email",
                                clientIp,
                                "SUCCESS"
                        );
                    }
                }
            } catch (Exception ex) {
                log.error("Safe fallback in password reset request handling: {}", ex.getMessage());
            }
        }

        // Generic response strictly prevents account enumeration attacks
        return "If an account with that email exists, password reset instructions have been sent.";
    }

    @Transactional
    public String resetPassword(ResetPasswordRequest request) {
        if (request.getToken() == null || request.getToken().trim().isEmpty()) {
            throw new BadRequestException("Password reset token is required.");
        }
        if (request.getNewPassword() == null || request.getNewPassword().length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters long.");
        }

        if (passwordResetTokenRepository == null) {
            throw new IllegalStateException("Password reset service is not configured.");
        }

        String rawToken = request.getToken().trim();
        String tokenHash = hashToken(rawToken);

        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadRequestException("Invalid or expired password reset token."));

        if (token.isConsumed()) {
            throw new BadRequestException("Password reset token has already been used. Please request a new one.");
        }

        if (token.isExpired()) {
            throw new BadRequestException("Password reset token has expired. Please request a new one.");
        }

        User user = token.getUser();
        if (user == null) {
            throw new BadRequestException("User associated with token not found.");
        }

        // Atomic single-use token consumption preventing concurrent redemption race conditions
        OffsetDateTime now = OffsetDateTime.now();
        int consumedCount = passwordResetTokenRepository.consumeTokenAtomic(tokenHash, now);
        if (consumedCount == 0) {
            if (token.isConsumed()) {
                throw new BadRequestException("Password reset token has already been used. Please request a new one.");
            }
            if (token.isExpired()) {
                throw new BadRequestException("Password reset token has expired. Please request a new one.");
            }
            throw new BadRequestException("Invalid or expired password reset token.");
        }
        token.setConsumedAt(now);

        // Invalidate any other outstanding tokens for this user
        passwordResetTokenRepository.invalidateOutstandingTokensForUser(user, now);

        // Update password hash using BCrypt password encoder
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Invalidate all active sessions for this user across all devices
        invalidateUserSessions(user);

        // Reset lockout state if present
        if (loginLockoutService != null && token.getCreatedByIp() != null) {
            try {
                loginLockoutService.recordSuccessfulLogin(user.getEmail(), token.getCreatedByIp());
            } catch (Exception ignored) {}
        }

        auditService.logAction(
                user.getId(),
                user.getFullName(),
                "CITIZEN",
                "PASSWORD_RESET_COMPLETED",
                "USER",
                user.getId().toString(),
                "User password reset successfully",
                token.getCreatedByIp(),
                "SUCCESS"
        );

        return "Password has been reset successfully. You can now log in with your new credentials.";
    }

    public void invalidateUserSessions(User user) {
        if (sessionRegistry == null || user == null) {
            return;
        }
        try {
            for (Object principal : sessionRegistry.getAllPrincipals()) {
                boolean match = false;
                if (principal instanceof com.civicfix.security.UserPrincipal up && user.getId().equals(up.getId())) {
                    match = true;
                } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails ud
                        && (user.getEmail().equalsIgnoreCase(ud.getUsername()) || (user.getPhone() != null && user.getPhone().equals(ud.getUsername())))) {
                    match = true;
                } else if (principal instanceof String principalStr
                        && (user.getEmail().equalsIgnoreCase(principalStr) || user.getId().toString().equals(principalStr))) {
                    match = true;
                }
                if (match) {
                    List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
                    for (SessionInformation sessionInfo : sessions) {
                        sessionInfo.expireNow();
                        log.info("Invalidated active session [{}] for user [{}] after password reset.", sessionInfo.getSessionId(), user.getEmail());
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to invalidate sessions for user [{}]: {}", user.getEmail(), ex.getMessage());
        }
    }

    public void setSessionRegistry(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    public SessionRegistry getSessionRegistry() {
        return sessionRegistry;
    }

    public void setPasswordResetAccountCooldownMinutes(int minutes) {
        this.passwordResetAccountCooldownMinutes = minutes;
    }

    public int getPasswordResetAccountCooldownMinutes() {
        return passwordResetAccountCooldownMinutes;
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public void setPasswordResetValidityMinutes(int minutes) {
        this.passwordResetValidityMinutes = minutes;
    }

    public int getPasswordResetValidityMinutes() {
        return this.passwordResetValidityMinutes;
    }

    public void setAppBaseUrl(String appBaseUrl) {
        this.appBaseUrl = appBaseUrl;
    }

    public String getAppBaseUrl() {
        return this.appBaseUrl;
    }
}
