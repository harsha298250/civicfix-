package com.civicfix.mail;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Secondary/Fallback Email Delivery Service using standard Spring Mail (SMTP).
 */
@Service
public class DefaultEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(DefaultEmailService.class);

    private final Optional<JavaMailSender> mailSender;

    @Value("${civicfix.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${civicfix.mail.from:noreply@civicfix.org}")
    private String fromAddress;

    @Autowired
    public DefaultEmailService(Optional<JavaMailSender> mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public boolean sendEmailVerificationEmail(String toEmail, String name, String verificationUrl) {
        String maskedRecipient = maskEmail(toEmail);
        if (!mailEnabled || mailSender.isEmpty()) {
            log.warn("[SMTP EMAIL] Verification email aborted: SMTP is disabled. Recipient: {}", maskedRecipient);
            return false;
        }
        try {
            JavaMailSender sender = mailSender.get();
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("Verify Your CivicFix Account");
            String htmlBody = "<p>Hello " + (name != null ? name : "Citizen") + ",</p>"
                    + "<p>Please verify your email address to activate your CivicFix account:</p>"
                    + "<p><a href=\"" + verificationUrl + "\">Verify Email Address</a></p>";
            helper.setText(htmlBody, true);
            sender.send(message);
            return true;
        } catch (Exception ex) {
            log.error("[SMTP EMAIL] Failed to send verification email to {}: {}", maskedRecipient, ex.getMessage());
            return false;
        }
    }

    @Override
    public boolean sendPasswordResetEmail(String toEmail, String resetUrl) {
        String maskedRecipient = maskEmail(toEmail);

        if (!mailEnabled || mailSender.isEmpty()) {
            log.warn("[SMTP EMAIL] Outbound email delivery aborted: SMTP is disabled (civicfix.mail.enabled=false). Recipient: {}",
                    maskedRecipient);
            return false;
        }

        try {
            JavaMailSender sender = mailSender.get();
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("CivicFix Password Reset Request");

            String htmlBody = "<!DOCTYPE html><html><body style=\"font-family: sans-serif; color: #18232F;\">"
                    + "<div style=\"max-width: 540px; margin: 0 auto; padding: 24px; border: 1px solid #E2E7ED; border-radius: 12px;\">"
                    + "<h2 style=\"color: #137A70;\">CivicFix Account Security</h2>"
                    + "<p>A password reset was requested for your CivicFix account.</p>"
                    + "<p>Click the link below to set a new password. This link is single-use and expires in 15 minutes:</p>"
                    + "<p style=\"margin: 24px 0;\"><a href=\"" + resetUrl + "\" style=\"display: inline-block; background-color: #137A70; color: #ffffff; padding: 12px 24px; text-decoration: none; border-radius: 8px; font-weight: bold;\">Reset Password</a></p>"
                    + "<p style=\"font-size: 12px; color: #667384;\">If you did not request this reset, you can safely ignore this email.</p>"
                    + "</div></body></html>";

            helper.setText(htmlBody, true);
            sender.send(message);

            log.info("[SMTP EMAIL] Password reset email successfully dispatched to: {}", maskedRecipient);
            return true;
        } catch (Exception ex) {
            log.warn("[SMTP EMAIL] Failed to deliver password reset email to: {}. Error: {}",
                    maskedRecipient, ex.getMessage());
            return false;
        }
    }

    @Override
    public boolean sendPasswordResetConfirmationEmail(String toEmail, String name) {
        log.warn("[SMTP EMAIL] Outbound email skipped: SMTP fallback disabled. Recipient: {}", maskEmail(toEmail));
        return false;
    }

    @Override
    public boolean sendWelcomeEmail(String toEmail, String name) {
        log.warn("[SMTP EMAIL] Outbound email skipped: SMTP fallback disabled. Recipient: {}", maskEmail(toEmail));
        return false;
    }

    @Override
    public boolean sendComplaintSubmittedEmail(String toEmail, String citizenName, String referenceId, String title, String category, String ward) {
        log.warn("[SMTP EMAIL] Outbound email skipped for #{} (SMTP fallback disabled). Recipient: {}", referenceId, maskEmail(toEmail));
        return false;
    }

    @Override
    public boolean sendComplaintStatusUpdateEmail(String toEmail, String citizenName, String referenceId, String title, String oldStatus, String newStatus, String notes) {
        log.warn("[SMTP EMAIL] Outbound email skipped for #{} (SMTP fallback disabled). Recipient: {}", referenceId, maskEmail(toEmail));
        return false;
    }

    @Override
    public boolean sendComplaintAssignmentEmail(String toEmail, String recipientName, String role, String referenceId, String title, String priority, String ward) {
        log.warn("[SMTP EMAIL] Outbound email skipped for #{} (SMTP fallback disabled). Recipient: {}", referenceId, maskEmail(toEmail));
        return false;
    }

    @Override
    public boolean sendNewComplaintAlertEmail(String toEmail, String officerName, String referenceId, String title, String priority, String ward) {
        log.warn("[SMTP EMAIL] Outbound email skipped for #{} (SMTP fallback disabled). Recipient: {}", referenceId, maskEmail(toEmail));
        return false;
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@", 2);
        String name = parts[0];
        String domain = parts[1];
        if (name.length() <= 2) {
            return name.charAt(0) + "***@" + domain;
        }
        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + "@" + domain;
    }

    public void setMailEnabled(boolean mailEnabled) {
        this.mailEnabled = mailEnabled;
    }

    public boolean isMailEnabled() {
        return mailEnabled;
    }
}
