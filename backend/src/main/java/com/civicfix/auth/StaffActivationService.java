package com.civicfix.auth;

import com.civicfix.audit.AuditService;
import com.civicfix.auth.dto.ActivateStaffRequest;
import com.civicfix.auth.dto.VerifyActivationTokenResponse;
import com.civicfix.auth.firebase.FirebaseTokenClaims;
import com.civicfix.auth.firebase.FirebaseTokenVerifier;
import com.civicfix.common.exception.BadRequestException;
import com.civicfix.mail.EmailOutboxService;
import com.civicfix.mail.EmailService;
import com.civicfix.user.Role;
import com.civicfix.user.User;
import com.civicfix.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StaffActivationService {

    private static final Logger log = LoggerFactory.getLogger(StaffActivationService.class);

    private final StaffActivationTokenRepository staffActivationTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final EmailOutboxService emailOutboxService;
    private final EmailService emailService;
    private final FirebaseTokenVerifier firebaseTokenVerifier;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${auth.staff-activation.token-validity-hours:72}")
    private int tokenValidityHours = 72;

    @Value("${auth.password-reset.app-base-url:http://localhost:5173}")
    private String appBaseUrl = "http://localhost:5173";

    @Autowired
    public StaffActivationService(
            StaffActivationTokenRepository staffActivationTokenRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            @Autowired(required = false) EmailOutboxService emailOutboxService,
            @Autowired(required = false) EmailService emailService,
            @Autowired(required = false) FirebaseTokenVerifier firebaseTokenVerifier
    ) {
        this.staffActivationTokenRepository = staffActivationTokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.emailOutboxService = emailOutboxService;
        this.emailService = emailService;
        this.firebaseTokenVerifier = firebaseTokenVerifier;
    }

    @Transactional
    public String createActivationToken(User staffUser, User adminUser) {
        if (staffUser == null) {
            throw new IllegalArgumentException("Staff user cannot be null.");
        }

        // Invalidate any existing outstanding activation tokens for this user
        staffActivationTokenRepository.invalidateOutstandingTokensForUser(staffUser, OffsetDateTime.now());

        // Generate 32 bytes cryptographically secure entropy
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = HexFormat.of().formatHex(randomBytes);
        String tokenHash = hashToken(rawToken);

        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(tokenValidityHours > 0 ? tokenValidityHours : 48);

        StaffActivationToken token = new StaffActivationToken(staffUser, tokenHash, expiresAt, adminUser);
        staffActivationTokenRepository.save(token);

        String activationUrl = appBaseUrl + "/activate-staff?token=" + rawToken;

        // Dispatch via Transactional Email Outbox
        if (staffUser.getEmail() != null && !staffUser.getEmail().isBlank()) {
            try {
                if (emailOutboxService != null) {
                    String idemKey = "staff_act_" + token.getId();
                    emailOutboxService.enqueueEmail(
                            idemKey,
                            staffUser.getEmail(),
                            "Activate Your CivicFix Municipal Staff Account",
                            "STAFF_ACTIVATION_INVITE",
                            Map.of(
                                    "name", staffUser.getFullName(),
                                    "activationUrl", activationUrl,
                                    "department", staffUser.getDepartment() != null ? staffUser.getDepartment() : "Municipal Operations",
                                    "role", staffUser.getRoles().stream().map(r -> r.getName().name()).collect(Collectors.joining(", ")),
                                    "expiresInHours", String.valueOf(tokenValidityHours)
                            )
                    );
                } else if (emailService != null) {
                    emailService.sendWelcomeEmail(staffUser.getEmail(), staffUser.getFullName());
                }
            } catch (Exception ex) {
                log.warn("Failed to enqueue staff activation email for user {}: {}", staffUser.getId(), ex.getMessage());
            }
        }

        return rawToken;
    }

    @Transactional(readOnly = true)
    public VerifyActivationTokenResponse verifyToken(String rawToken) {
        if (rawToken == null || rawToken.trim().isBlank()) {
            throw new BadRequestException("Activation token is required.");
        }

        String tokenHash = hashToken(rawToken.trim());
        StaffActivationToken token = staffActivationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadRequestException("Invalid or unrecognized activation token."));

        if (token.isConsumed()) {
            throw new BadRequestException("This activation link has already been used. Please log in or contact your administrator.");
        }

        if (token.isExpired()) {
            throw new BadRequestException("This activation link has expired. Please contact your administrator to request a new invitation.");
        }

        User u = token.getUser();
        String roleStr = u.getRoles().stream()
                .map(r -> r.getName().name())
                .findFirst()
                .orElse("STAFF");

        return new VerifyActivationTokenResponse(
                true,
                u.getEmail(),
                u.getFullName(),
                roleStr,
                u.getDepartment(),
                token.getExpiresAt()
        );
    }

    @Transactional
    public void activateStaff(ActivateStaffRequest req, String clientIp) {
        if (req == null || req.getToken() == null || req.getToken().trim().isBlank()) {
            throw new BadRequestException("Activation token is required.");
        }
        boolean hasPassword = req.getNewPassword() != null && !req.getNewPassword().isBlank();
        boolean hasIdToken = req.getIdToken() != null && !req.getIdToken().isBlank();

        if (!hasPassword && !hasIdToken) {
            throw new BadRequestException("Either a permanent password (min 8 characters) or Firebase authentication token is required.");
        }
        if (hasPassword && req.getNewPassword().length() < 8) {
            throw new BadRequestException("Permanent password must be at least 8 characters long.");
        }

        String rawToken = req.getToken().trim();
        String tokenHash = hashToken(rawToken);

        StaffActivationToken token = staffActivationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadRequestException("Invalid or unrecognized activation token."));

        if (token.isConsumed()) {
            throw new BadRequestException("This activation link has already been used. Please log in.");
        }
        if (token.isExpired()) {
            throw new BadRequestException("This activation link has expired. Please request a new invitation.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        int updated = staffActivationTokenRepository.consumeTokenAtomic(tokenHash, now);
        if (updated == 0) {
            throw new BadRequestException("Activation link was already used or expired.");
        }

        User user = token.getUser();

        // Bind Firebase identity if provided
        if (hasIdToken) {
            if (firebaseTokenVerifier != null) {
                FirebaseTokenClaims claims = firebaseTokenVerifier.verifyIdToken(req.getIdToken());
                if (claims == null || claims.getUid() == null) {
                    throw new BadRequestException("Invalid Firebase authentication token.");
                }
                if (claims.getEmail() != null && !claims.getEmail().equalsIgnoreCase(user.getEmail())) {
                    throw new BadRequestException("The authenticated Firebase email (" + claims.getEmail() + ") does not match the invitation email (" + user.getEmail() + ").");
                }
                user.setFirebaseUid(claims.getUid());
                user.setEmailVerified(true);
            } else {
                log.warn("FirebaseTokenVerifier not available; skipping Firebase ID token verification during staff activation.");
            }
        }

        if (hasPassword) {
            user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        }

        user.setAccountStatus("ACTIVE");
        userRepository.save(user);

        // Invalidate any other tokens for this user
        staffActivationTokenRepository.invalidateOutstandingTokensForUser(user, now);

        // Audit log
        auditService.logAction(
                user.getId(),
                user.getFullName(),
                user.getRoles().isEmpty() ? "STAFF" : user.getRoles().iterator().next().getName().name(),
                "STAFF_ACTIVATED",
                "USER",
                user.getId().toString(),
                "Staff member successfully activated their account and set permanent password.",
                clientIp,
                "SUCCESS"
        );

        log.info("Staff member {} ({}) successfully completed onboarding activation.", user.getFullName(), user.getId());
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
