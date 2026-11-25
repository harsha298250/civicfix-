package com.civicfix.auth;

import com.civicfix.audit.AuditService;
import com.civicfix.auth.dto.AuthUserResponse;
import com.civicfix.auth.dto.ForgotPasswordRequest;
import com.civicfix.auth.dto.GoogleAuthRequest;
import com.civicfix.auth.dto.LoginRequest;
import com.civicfix.auth.dto.RegisterCitizenRequest;
import com.civicfix.auth.dto.ResendVerificationRequest;
import com.civicfix.auth.dto.ResetPasswordRequest;
import com.civicfix.auth.dto.VerifyEmailRequest;
import com.civicfix.auth.google.GoogleTokenClaims;
import com.civicfix.auth.google.GoogleTokenVerifier;
import com.civicfix.common.exception.BadRequestException;
import com.civicfix.common.exception.ConflictException;
import com.civicfix.common.exception.RateLimitExceededException;
import com.civicfix.common.exception.ResourceNotFoundException;
import com.civicfix.mail.EmailOutboxService;
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
import org.springframework.security.authentication.DisabledException;
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
import java.util.Map;
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
    private final EmailOutboxService emailOutboxService;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final GoogleTokenVerifier googleTokenVerifier;
    private SessionRegistry sessionRegistry;
    private final SecureRandom secureRandom = new SecureRandom();
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    @org.springframework.beans.factory.annotation.Value("${auth.password-reset.token-validity-minutes:15}")
    private int passwordResetValidityMinutes = 15;

    @org.springframework.beans.factory.annotation.Value("${auth.password-reset.account-cooldown-minutes:5}")
    private int passwordResetAccountCooldownMinutes = 5;

    @org.springframework.beans.factory.annotation.Value("${civicfix.frontend.base-url:${FRONTEND_BASE_URL:${auth.password-reset.app-base-url:http://localhost:5173}}}")
    private String appBaseUrl = "http://localhost:5173";

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       AuditService auditService,
                       LoginLockoutService loginLockoutService,
                       ClientIpResolver clientIpResolver) {
        this(userRepository, roleRepository, passwordEncoder, authenticationManager, auditService,
             loginLockoutService, clientIpResolver, null, null, null, null, null, null);
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
             loginLockoutService, clientIpResolver, passwordResetTokenRepository, emailService, null, null, null, null);
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
                       @org.springframework.beans.factory.annotation.Autowired(required = false) EmailOutboxService emailOutboxService,
                       @org.springframework.beans.factory.annotation.Autowired(required = false) SessionRegistry sessionRegistry,
                       @org.springframework.beans.factory.annotation.Autowired(required = false) EmailVerificationTokenRepository emailVerificationTokenRepository,
                       @org.springframework.beans.factory.annotation.Autowired(required = false) GoogleTokenVerifier googleTokenVerifier) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.auditService = auditService;
        this.loginLockoutService = loginLockoutService;
        this.clientIpResolver = clientIpResolver;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailService = emailService;
        this.emailOutboxService = emailOutboxService;
        this.sessionRegistry = sessionRegistry;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.googleTokenVerifier = googleTokenVerifier;
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

        boolean requiresVerification = email != null && !email.isBlank();

        User user = new User();
        user.setFullName(request.getName().trim());
        user.setEmail(email != null && !email.isEmpty() ? email : null);
        user.setPhone(phone != null && !phone.isEmpty() ? phone : null);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setWard(request.getWard() != null ? request.getWard().trim() : "Ward 08, Anna Nagar, Chennai");
        user.setDepartment("Resident Community Member");
        user.setAccountStatus(requiresVerification ? "PENDING_VERIFICATION" : "ACTIVE");
        user.setEmailVerified(!requiresVerification);
        user.setTrustScore(90);
        user.setRoles(Set.of(citizenRole));

        User savedUser = userRepository.save(user);

        // Generate email verification token if email is present
        if (requiresVerification) {
            byte[] randomBytes = new byte[32];
            secureRandom.nextBytes(randomBytes);
            String rawToken = HexFormat.of().formatHex(randomBytes);
            String tokenHash = hashToken(rawToken);
            OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(24);

            if (emailVerificationTokenRepository != null) {
                EmailVerificationToken verificationToken = new EmailVerificationToken(savedUser, tokenHash, expiresAt, ipAddress);
                emailVerificationTokenRepository.save(verificationToken);
            }

            String verificationUrl = appBaseUrl + "/verify-email?token=" + rawToken;
            try {
                if (emailOutboxService != null) {
                    String idemKey = "verify_" + savedUser.getId();
                    emailOutboxService.enqueueEmail(
                            idemKey,
                            savedUser.getEmail(),
                            "Verify Your CivicFix Account",
                            "EMAIL_VERIFICATION",
                            Map.of(
                                    "name", savedUser.getFullName() != null ? savedUser.getFullName() : "Citizen",
                                    "verificationUrl", verificationUrl,
                                    "rawToken", rawToken
                            )
                    );
                } else if (emailService != null) {
                    emailService.sendEmailVerificationEmail(savedUser.getEmail(), savedUser.getFullName(), verificationUrl);
                }
            } catch (Exception ex) {
                log.warn("Non-fatal verification email delivery failure for user {}: {}", savedUser.getId(), ex.getMessage());
            }
        }

        auditService.logAction(
                savedUser.getId(),
                savedUser.getFullName(),
                "CITIZEN",
                "AUTH_REGISTER",
                "USER",
                savedUser.getId().toString(),
                requiresVerification
                        ? "Citizen self-registration initiated. Verification email dispatched."
                        : "Citizen self-registration completed.",
                ipAddress,
                "SUCCESS"
        );

        return AuthUserResponse.fromEntity(savedUser);
    }

    @Transactional
    public AuthUserResponse registerCitizen(RegisterCitizenRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String clientIp = httpRequest != null ? clientIpResolver.resolveClientIp(httpRequest) : "unknown";
        AuthUserResponse response = registerCitizen(request, clientIp);

        // Only automatically authenticate the citizen if no email verification is pending
        if (response != null && "ACTIVE".equalsIgnoreCase(response.getAccountStatus())) {
            User user = userRepository.findById(response.getId()).orElse(null);
            if (user != null && httpRequest != null && httpResponse != null) {
                UserPrincipal principal = new UserPrincipal(user);
                Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                SecurityContext context = securityContextHolderStrategy.createEmptyContext();
                context.setAuthentication(auth);
                securityContextHolderStrategy.setContext(context);
                securityContextRepository.saveContext(context, httpRequest, httpResponse);
            }
        }
        return response;
    }

    @Transactional
    public String verifyEmail(VerifyEmailRequest request, String ipAddress) {
        if (request == null || request.getToken() == null || request.getToken().trim().isEmpty()) {
            throw new BadRequestException("Verification token is required.");
        }
        if (emailVerificationTokenRepository == null) {
            throw new IllegalStateException("Email verification service is not configured.");
        }

        String rawToken = request.getToken().trim();
        String tokenHash = hashToken(rawToken);

        EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadRequestException("Invalid or expired email verification link."));

        if (token.isConsumed()) {
            throw new BadRequestException("Verification link has already been used. Please sign in.");
        }

        if (token.isExpired()) {
            throw new BadRequestException("Verification link has expired. Please request a new verification email.");
        }

        User user = token.getUser();
        user.setAccountStatus("ACTIVE");
        user.setEmailVerified(true);
        userRepository.save(user);

        token.setConsumedAt(OffsetDateTime.now());
        emailVerificationTokenRepository.save(token);

        auditService.logAction(
                user.getId(),
                user.getFullName(),
                "CITIZEN",
                "EMAIL_VERIFIED",
                "USER",
                user.getId().toString(),
                "Citizen email address verified successfully.",
                ipAddress,
                "SUCCESS"
        );

        return "Your email address has been successfully verified. You can now sign in.";
    }

    @Transactional
    public String resendVerification(ResendVerificationRequest request, String ipAddress) {
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        if (!email.isEmpty()) {
            try {
                User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
                if (user != null && "PENDING_VERIFICATION".equalsIgnoreCase(user.getAccountStatus())) {
                    if (emailVerificationTokenRepository != null) {
                        int cooldownMinutes = 5;
                        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(cooldownMinutes);
                        long recentTokens = emailVerificationTokenRepository.countRecentTokensForUser(user, cutoff);
                        if (recentTokens > 0) {
                            log.warn("Resend verification throttled for user {}. Cooldown active.", user.getId());
                            return "If an account with that email exists and is pending verification, a new verification link has been sent.";
                        }

                        emailVerificationTokenRepository.invalidateOutstandingTokensForUser(user, OffsetDateTime.now());

                        byte[] randomBytes = new byte[32];
                        secureRandom.nextBytes(randomBytes);
                        String rawToken = HexFormat.of().formatHex(randomBytes);
                        String tokenHash = hashToken(rawToken);

                        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(24);
                        EmailVerificationToken token = new EmailVerificationToken(user, tokenHash, expiresAt, ipAddress);
                        emailVerificationTokenRepository.save(token);

                        String verificationUrl = appBaseUrl + "/verify-email?token=" + rawToken;
                        if (emailOutboxService != null) {
                            String idemKey = "verify_resend_" + token.getId();
                            emailOutboxService.enqueueEmail(
                                    idemKey,
                                    user.getEmail(),
                                    "Verify Your CivicFix Account",
                                    "EMAIL_VERIFICATION",
                                    Map.of(
                                            "name", user.getFullName() != null ? user.getFullName() : "Citizen",
                                            "verificationUrl", verificationUrl,
                                            "rawToken", rawToken
                                    )
                            );
                        } else if (emailService != null) {
                            emailService.sendEmailVerificationEmail(user.getEmail(), user.getFullName(), verificationUrl);
                        }
                    }
                }
            } catch (Exception ex) {
                log.error("Safe fallback in resend verification: {}", ex.getMessage());
            }
        }
        return "If an account with that email exists and is pending verification, a new verification link has been sent.";
    }

    @Transactional(noRollbackFor = {BadCredentialsException.class, RateLimitExceededException.class})
    public AuthUserResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String identifier = request.getIdentifier().trim();
        String ipAddress = clientIpResolver.resolveClientIp(httpRequest);

        // 1. Acquire PostgreSQL-backed coordination advisory locks in strict order (Account first, IP second).
        loginLockoutService.acquireCoordinationLocks(identifier, ipAddress);

        // 2. Enforce account / IP lockout before attempting authentication
        loginLockoutService.checkLockout(identifier, ipAddress);

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(identifier, request.getPassword())
            );
        } catch (DisabledException ex) {
            loginLockoutService.recordFailedAttempt(identifier, ipAddress);
            User candidate = userRepository.findByEmailIgnoreCase(identifier)
                    .or(() -> userRepository.findByPhone(identifier))
                    .orElse(null);
            if (candidate != null && "PENDING_VERIFICATION".equalsIgnoreCase(candidate.getAccountStatus())) {
                throw new BadRequestException("Your account is pending email verification. Please check your inbox or request a new verification email.");
            }
            throw new BadRequestException("Your account is pending activation or disabled. Please use the activation link sent to your email or contact your administrator.");
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

        // Server-managed Remember Me session duration
        if (Boolean.TRUE.equals(request.getRememberMe())) {
            httpRequest.getSession().setMaxInactiveInterval(30 * 24 * 3600); // 30 days
        } else {
            httpRequest.getSession().setMaxInactiveInterval(3600); // 1 hour
        }

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
                Boolean.TRUE.equals(request.getRememberMe())
                        ? "Successful user authentication with Remember Me (30-day session)."
                        : "Successful user authentication (standard session).",
                ipAddress,
                "SUCCESS"
        );

        return AuthUserResponse.fromEntity(user);
    }

    @Transactional
    public AuthUserResponse loginWithGoogle(GoogleAuthRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        if (googleTokenVerifier == null) {
            throw new IllegalStateException("Google OAuth service is not configured on this server.");
        }

        GoogleTokenClaims claims = googleTokenVerifier.verify(request.getIdToken());
        String email = claims.getEmail();
        String ipAddress = httpRequest != null ? clientIpResolver.resolveClientIp(httpRequest) : "unknown";

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user != null) {
            if ("SUSPENDED".equalsIgnoreCase(user.getAccountStatus()) || "DEACTIVATED".equalsIgnoreCase(user.getAccountStatus())) {
                throw new BadRequestException("Your account is suspended or deactivated. Please contact municipal administration.");
            }
            // Auto-verify email if was pending verification, since Google proved identity
            if ("PENDING_VERIFICATION".equalsIgnoreCase(user.getAccountStatus())) {
                user.setAccountStatus("ACTIVE");
            }
            user.setEmailVerified(true);
            if (user.getGoogleId() == null || user.getGoogleId().isBlank()) {
                user.setGoogleId(claims.getSubject());
            }
            user = userRepository.save(user);
        } else {
            // Create brand new citizen account - strictly CITIZEN role, NEVER elevated roles
            Role citizenRole = roleRepository.findByName(RoleName.CITIZEN)
                    .orElseGet(() -> roleRepository.save(new Role(RoleName.CITIZEN)));

            user = new User();
            user.setFullName(claims.getName());
            user.setEmail(email);
            user.setGoogleId(claims.getSubject());
            user.setEmailVerified(true);
            user.setAccountStatus("ACTIVE");
            user.setTrustScore(90);
            user.setDepartment("Resident Community Member");
            user.setWard("Ward 08, Anna Nagar, Chennai");
            user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setRoles(Set.of(citizenRole));
            user = userRepository.save(user);

            auditService.logAction(
                    user.getId(),
                    user.getFullName(),
                    "CITIZEN",
                    "AUTH_GOOGLE_REGISTER",
                    "USER",
                    user.getId().toString(),
                    "Citizen account created via Google OAuth.",
                    ipAddress,
                    "SUCCESS"
            );
        }

        // Establish server-side HTTP session
        UserPrincipal principal = new UserPrincipal(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        if (Boolean.TRUE.equals(request.getRememberMe())) {
            httpRequest.getSession().setMaxInactiveInterval(30 * 24 * 3600); // 30 days
        } else {
            httpRequest.getSession().setMaxInactiveInterval(3600); // 1 hour
        }

        auditService.logAction(
                user.getId(),
                user.getFullName(),
                principal.getAuthorities().iterator().next().getAuthority(),
                "AUTH_GOOGLE_LOGIN",
                "SESSION",
                httpRequest.getSession().getId(),
                Boolean.TRUE.equals(request.getRememberMe())
                        ? "Successful Google OAuth authentication with Remember Me (30-day session)."
                        : "Successful Google OAuth authentication (standard session).",
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
                        if (emailOutboxService != null) {
                            String idemKey = "pwd_reset_" + token.getId();
                            emailOutboxService.enqueueEmail(
                                    idemKey,
                                    user.getEmail(),
                                    "CivicFix Password Reset Request",
                                    "PASSWORD_RESET",
                                    java.util.Map.of("resetUrl", resetUrl, "rawToken", rawToken)
                            );
                        } else if (emailService != null) {
                            emailService.sendPasswordResetEmail(user.getEmail(), resetUrl);
                        }

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

        OffsetDateTime now = OffsetDateTime.now();
        int consumedCount = passwordResetTokenRepository.consumeTokenAtomic(tokenHash, now);
        if (consumedCount == 0) {
            throw new BadRequestException("Invalid, expired, or already consumed password reset token.");
        }
        token.setConsumedAt(now);

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        passwordResetTokenRepository.invalidateOutstandingTokensForUser(user, now);

        // Security requirement: Invalidate active sessions for this user upon password reset
        if (sessionRegistry != null) {
            try {
                List<Object> allPrincipals = sessionRegistry.getAllPrincipals();
                for (Object p : allPrincipals) {
                    if (p instanceof UserPrincipal up && up.getId().equals(user.getId())) {
                        List<SessionInformation> sessions = sessionRegistry.getAllSessions(p, false);
                        for (SessionInformation si : sessions) {
                            si.expireNow();
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Session invalidation during password reset encountered non-fatal error: {}", e.getMessage());
            }
        }

        if (emailOutboxService != null && user.getEmail() != null) {
            String idemKey = "pwd_confirm_" + user.getId() + "_" + System.currentTimeMillis();
            emailOutboxService.enqueueEmail(
                    idemKey,
                    user.getEmail(),
                    "CivicFix Account Security: Password Changed",
                    "PASSWORD_RESET_CONFIRMATION",
                    Map.of("name", user.getFullName())
            );
        } else if (emailService != null && user.getEmail() != null) {
            emailService.sendPasswordResetConfirmationEmail(user.getEmail(), user.getFullName());
        }

        auditService.logAction(
                user.getId(),
                user.getFullName(),
                "CITIZEN",
                "PASSWORD_RESET_COMPLETED",
                "USER",
                user.getId().toString(),
                "User successfully changed password via reset token.",
                token.getCreatedByIp(),
                "SUCCESS"
        );

        return "Password has been reset successfully. You may now log in with your new password.";
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

    public void setSessionRegistry(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
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
