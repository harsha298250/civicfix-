package com.civicfix.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ResendEmailService — Enterprise Transactional Email Delivery via Resend HTTP API.
 *
 * Implements Resend's official REST API endpoint (POST https://api.resend.com/emails)
 * with robust connection and response timeouts (5000ms default), non-blocking error handling,
 * masked recipient logging, and provider delivery status tracking.
 *
 * Configured dynamically via RESEND_API_KEY and RESEND_FROM_EMAIL.
 * When RESEND_API_KEY is not configured (e.g. offline dev/test environments),
 * delivery is simulated and logged without failing the originating user transaction.
 */
@Service
@Primary
public class ResendEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailService.class);

    @Value("${resend.api-key:${RESEND_API_KEY:}}")
    private String apiKey;

    @Value("${resend.from-email:${RESEND_FROM_EMAIL:onboarding@resend.dev}}")
    private String fromEmail;

    @Value("${resend.api-url:${RESEND_API_URL:https://api.resend.com/emails}}")
    private String apiUrl = "https://api.resend.com/emails";

    @Value("${resend.timeout-ms:${RESEND_TIMEOUT_MS:5000}}")
    private int timeoutMs = 5000;

    private RestClient restClient;

    public ResendEmailService() {
        // Default constructor used by Spring container
    }

    /**
     * Testing constructor allowing custom dependency injection and mocked RestClient.
     */
    public ResendEmailService(String apiKey, String fromEmail, String apiUrl, int timeoutMs, RestClient restClient) {
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.apiUrl = apiUrl;
        this.timeoutMs = timeoutMs;
        this.restClient = restClient;
    }

    private synchronized RestClient getRestClient() {
        if (this.restClient == null) {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(timeoutMs > 0 ? timeoutMs : 5000);
            requestFactory.setReadTimeout(timeoutMs > 0 ? timeoutMs : 5000);

            this.restClient = RestClient.builder()
                    .requestFactory(requestFactory)
                    .baseUrl(apiUrl != null && !apiUrl.isBlank() ? apiUrl : "https://api.resend.com/emails")
                    .build();
        }
        return this.restClient;
    }

    /**
     * Core transactional email dispatch method to Resend's HTTP API.
     */
    public boolean sendEmail(String toEmail, String subject, String htmlContent) {
        if (toEmail == null || !toEmail.contains("@")) {
            log.warn("[RESEND] Delivery skipped: recipient email address is invalid or empty.");
            return false;
        }

        String maskedRecipient = maskEmail(toEmail);

        // Return false when API key is missing or blank, logging a clear warning without failing callers
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("[RESEND] Outbound email delivery aborted: RESEND_API_KEY is not configured. Recipient: {}, Subject: '{}'",
                    maskedRecipient, subject);
            return false;
        }

        String sender = (fromEmail != null && !fromEmail.isBlank())
                ? "CivicFix Platform <" + fromEmail.trim() + ">"
                : "CivicFix Platform <onboarding@resend.dev>";

        if (sender.contains("onboarding@resend.dev")) {
            log.warn("[RESEND] Using sandbox sender 'onboarding@resend.dev'. Resend restricts free delivery to the verified account owner email. Configure a verified domain via RESEND_FROM_EMAIL for public citizen delivery.");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("from", sender);
        payload.put("to", List.of(toEmail.trim()));
        payload.put("subject", subject != null ? subject : "CivicFix Notification");
        payload.put("html", htmlContent);

        try {
            ResponseEntity<String> response = getRestClient().post()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toEntity(String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[RESEND] Email accepted by provider for recipient: {}. Response: {}",
                        maskedRecipient, response.getBody());
                return true;
            } else {
                log.warn("[RESEND] Provider returned non-2xx status for recipient {}: {}",
                        maskedRecipient, response.getStatusCode());
                return false;
            }
        } catch (RestClientResponseException rex) {
            log.warn("[RESEND] Provider rejected email for recipient {}. HTTP {}: {}",
                    maskedRecipient, rex.getStatusCode(), rex.getResponseBodyAsString());
            return false;
        } catch (ResourceAccessException rae) {
            log.warn("[RESEND] Network or socket timeout connecting to Resend API for recipient {}: {}",
                    maskedRecipient, rae.getMessage());
            return false;
        } catch (Exception ex) {
            log.warn("[RESEND] Unexpected exception dispatching email to recipient {}: {}",
                    maskedRecipient, ex.getMessage());
            return false;
        }
    }

    @Override
    public boolean sendPasswordResetEmail(String toEmail, String resetUrl) {
        String htmlBody = wrapTemplate(
                "CivicFix Account Security",
                "Password Reset Instructions",
                "<p>A password reset was requested for your CivicFix account.</p>"
                        + "<p>Click the secure link below to create a new password. This link is single-use and expires in 15 minutes:</p>"
                        + "<p style=\"margin: 24px 0;\"><a href=\"" + resetUrl + "\" style=\"display: inline-block; background-color: #137A70; color: #ffffff; padding: 12px 24px; text-decoration: none; border-radius: 8px; font-weight: bold;\">Reset Password</a></p>"
                        + "<p style=\"font-size: 12px; color: #667384;\">If you did not request this reset, you can safely ignore this email. Your current password remains secure.</p>"
        );
        return sendEmail(toEmail, "CivicFix Password Reset Request", htmlBody);
    }

    @Override
    public boolean sendPasswordResetConfirmationEmail(String toEmail, String name) {
        String displayName = (name != null && !name.isBlank()) ? name : "Citizen";
        String htmlBody = wrapTemplate(
                "CivicFix Account Security",
                "Password Changed Successfully",
                "<p>Hello " + escapeHtml(displayName) + ",</p>"
                        + "<p>The password for your CivicFix account was successfully changed.</p>"
                        + "<p>All existing active sessions across other devices have been invalidated for your security.</p>"
                        + "<p style=\"font-size: 12px; color: #DC2626; margin-top: 16px;\"><strong>Notice:</strong> If you did not authorize this change, please contact municipal administrators immediately.</p>"
        );
        return sendEmail(toEmail, "CivicFix Account Security: Password Changed", htmlBody);
    }

    @Override
    public boolean sendWelcomeEmail(String toEmail, String name) {
        String displayName = (name != null && !name.isBlank()) ? name : "Citizen";
        String htmlBody = wrapTemplate(
                "CivicFix Citizen Portal",
                "Welcome to CivicFix",
                "<p>Hello " + escapeHtml(displayName) + ",</p>"
                        + "<p>Welcome to CivicFix — your municipal platform for reporting civic defects, tracking street repair operations, and ensuring accountable ward governance.</p>"
                        + "<p>Your citizen account is currently active. You can log in at any time to submit complaints, upload geotagged defect photos, and monitor repair status in real time.</p>"
                        + "<p style=\"margin: 24px 0;\"><a href=\"https://civicfix-red.vercel.app/login\" style=\"display: inline-block; background-color: #137A70; color: #ffffff; padding: 12px 24px; text-decoration: none; border-radius: 8px; font-weight: bold;\">Go to Citizen Dashboard</a></p>"
                        + "<p style=\"font-size: 12px; color: #667384;\">Note: Official email verification is not required for basic incident intake. When formal verification workflows are enabled for your municipal ward, a notification will be provided.</p>"
        );
        return sendEmail(toEmail, "Welcome to CivicFix Platform", htmlBody);
    }

    @Override
    public boolean sendComplaintSubmittedEmail(String toEmail, String citizenName, String referenceId, String title, String category, String ward) {
        String htmlBody = wrapTemplate(
                "Municipal Issue Intake",
                "Complaint Receipt #" + escapeHtml(referenceId),
                "<p>Hello " + escapeHtml(citizenName != null ? citizenName : "Resident") + ",</p>"
                        + "<p>Your civic complaint has been successfully registered in the municipal intake system.</p>"
                        + "<table style=\"width: 100%; border-collapse: collapse; margin: 16px 0; font-size: 13px;\">"
                        + "<tr><td style=\"padding: 8px; border-bottom: 1px solid #E2E7ED; color: #667384; width: 35%;\">Reference ID:</td><td style=\"padding: 8px; border-bottom: 1px solid #E2E7ED; font-weight: bold; color: #137A70;\">" + escapeHtml(referenceId) + "</td></tr>"
                        + "<tr><td style=\"padding: 8px; border-bottom: 1px solid #E2E7ED; color: #667384;\">Title:</td><td style=\"padding: 8px; border-bottom: 1px solid #E2E7ED;\">" + escapeHtml(title) + "</td></tr>"
                        + "<tr><td style=\"padding: 8px; border-bottom: 1px solid #E2E7ED; color: #667384;\">Category:</td><td style=\"padding: 8px; border-bottom: 1px solid #E2E7ED;\">" + escapeHtml(category) + "</td></tr>"
                        + "<tr><td style=\"padding: 8px; border-bottom: 1px solid #E2E7ED; color: #667384;\">Ward:</td><td style=\"padding: 8px; border-bottom: 1px solid #E2E7ED;\">" + escapeHtml(ward) + "</td></tr>"
                        + "</table>"
                        + "<p>Municipal officers have been notified and will review your ticket for field assignment.</p>"
        );
        return sendEmail(toEmail, "CivicFix Complaint Receipt #" + referenceId, htmlBody);
    }

    @Override
    public boolean sendComplaintStatusUpdateEmail(String toEmail, String citizenName, String referenceId, String title, String oldStatus, String newStatus, String notes) {
        String notesBlock = (notes != null && !notes.isBlank())
                ? "<div style=\"background: #F8FAFC; padding: 12px; border-radius: 8px; margin: 12px 0; font-size: 12px; color: #334155;\"><strong>Remarks:</strong> " + escapeHtml(notes) + "</div>"
                : "";

        String htmlBody = wrapTemplate(
                "Civic Remediation Progress",
                "Status Update: Complaint #" + escapeHtml(referenceId),
                "<p>Hello " + escapeHtml(citizenName != null ? citizenName : "Resident") + ",</p>"
                        + "<p>There is an operational update on your municipal complaint:</p>"
                        + "<p style=\"font-size: 15px; font-weight: bold; color: #137A70;\">" + escapeHtml(title) + "</p>"
                        + "<p>Status changed from <strong>" + escapeHtml(oldStatus) + "</strong> &rarr; <span style=\"background-color: #E8F4F1; color: #137A70; padding: 4px 8px; border-radius: 4px; font-weight: bold;\">" + escapeHtml(newStatus) + "</span></p>"
                        + notesBlock
                        + "<p style=\"font-size: 12px; color: #667384;\">You can view complete progress logs and photos directly on your citizen dashboard.</p>"
        );
        return sendEmail(toEmail, "Status Update: Complaint #" + referenceId + " is now " + newStatus, htmlBody);
    }

    @Override
    public boolean sendComplaintAssignmentEmail(String toEmail, String recipientName, String role, String referenceId, String title, String priority, String ward) {
        String htmlBody = wrapTemplate(
                "Field Operations Dispatch",
                "Task Assignment #" + escapeHtml(referenceId),
                "<p>Hello " + escapeHtml(recipientName != null ? recipientName : "Field Technician") + ",</p>"
                        + "<p>You have been assigned to an active municipal work order:</p>"
                        + "<table style=\"width: 100%; border-collapse: collapse; margin: 16px 0; font-size: 13px;\">"
                        + "<tr><td style=\"padding: 8px; border-bottom: 1px solid #E2E7ED; color: #667384; width: 35%;\">Ticket:</td><td style=\"padding: 8px; border-bottom: 1px solid #E2E7ED; font-weight: bold;\">#" + escapeHtml(referenceId) + "</td></tr>"
                        + "<tr><td style=\"padding: 8px; border-bottom: 1px solid #E2E7ED; color: #667384;\">Defect:</td><td style=\"padding: 8px; border-bottom: 1px solid #E2E7ED;\">" + escapeHtml(title) + "</td></tr>"
                        + "<tr><td style=\"padding: 8px; border-bottom: 1px solid #E2E7ED; color: #667384;\">Priority:</td><td style=\"padding: 8px; border-bottom: 1px solid #E2E7ED; font-weight: bold; color: #DC2626;\">" + escapeHtml(priority) + "</td></tr>"
                        + "<tr><td style=\"padding: 8px; border-bottom: 1px solid #E2E7ED; color: #667384;\">Ward:</td><td style=\"padding: 8px; border-bottom: 1px solid #E2E7ED;\">" + escapeHtml(ward) + "</td></tr>"
                        + "</table>"
                        + "<p>Please review safety requirements and log site arrival when commencing work.</p>"
        );
        return sendEmail(toEmail, "Task Assigned: Complaint #" + referenceId + " (" + priority + ")", htmlBody);
    }

    @Override
    public boolean sendNewComplaintAlertEmail(String toEmail, String officerName, String referenceId, String title, String priority, String ward) {
        String htmlBody = wrapTemplate(
                "Municipal Intake Alert",
                "New Intake Defect: #" + escapeHtml(referenceId),
                "<p>Hello Officer " + escapeHtml(officerName != null ? officerName : "") + ",</p>"
                        + "<p>A new civic complaint requires triage in <strong>" + escapeHtml(ward) + "</strong>:</p>"
                        + "<p style=\"font-size: 14px; font-weight: bold;\">" + escapeHtml(title) + "</p>"
                        + "<p>Assessed Priority: <strong>" + escapeHtml(priority) + "</strong></p>"
                        + "<p style=\"margin: 20px 0;\"><a href=\"https://civicfix-red.vercel.app/officer/complaints/" + referenceId + "/review\" style=\"display: inline-block; background-color: #137A70; color: #ffffff; padding: 10px 20px; text-decoration: none; border-radius: 8px; font-weight: bold;\">Review Intake Ticket</a></p>"
        );
        return sendEmail(toEmail, "New Complaint Alert: #" + referenceId + " in " + ward, htmlBody);
    }

    private String wrapTemplate(String subtitle, String heading, String content) {
        return "<!DOCTYPE html><html><body style=\"margin: 0; padding: 24px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #F8FAFC; color: #18232F;\">"
                + "<div style=\"max-width: 540px; margin: 0 auto; background: #ffffff; padding: 28px; border: 1px solid #E2E7ED; border-radius: 16px; box-shadow: 0 2px 8px rgba(0,0,0,0.04);\">"
                + "<div style=\"font-size: 11px; font-weight: bold; text-transform: uppercase; color: #667384; letter-spacing: 0.5px; margin-bottom: 4px;\">" + escapeHtml(subtitle) + "</div>"
                + "<h2 style=\"color: #137A70; margin-top: 0; margin-bottom: 20px; font-size: 20px;\">" + escapeHtml(heading) + "</h2>"
                + content
                + "<hr style=\"border: none; border-top: 1px solid #E2E7ED; margin: 24px 0;\" />"
                + "<p style=\"font-size: 11px; color: #94A3B8; margin: 0;\">CivicFix AI Platform &bull; Automated Municipal Remediation Infrastructure &bull; Ward 08 Chennai</p>"
                + "</div></body></html>";
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
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
}
