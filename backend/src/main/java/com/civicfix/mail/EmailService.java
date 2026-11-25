package com.civicfix.mail;

/**
 * Enterprise Email Delivery Service Interface
 *
 * Defines contracts for transactional emails across citizen authentication,
 * password lifecycle, and municipal defect remediation workflows.
 */
public interface EmailService {

    /**
     * Dispatches an account email verification email containing the one-time activation link.
     * Link expires in 24 hours.
     *
     * @param toEmail         Recipient email address
     * @param name            User's full name
     * @param verificationUrl Complete verification URL containing the single-use raw token
     * @return true if accepted by provider or simulated; false if rejected
     */
    boolean sendEmailVerificationEmail(String toEmail, String name, String verificationUrl);

    /**
     * Dispatches a secure, single-use password recovery email containing the one-time reset link.
     * Link expires in 15 minutes.
     *
     * @param toEmail   Recipient email address
     * @param resetUrl  Complete password reset URL containing the one-time raw token
     * @return true if accepted by provider or simulated; false if rejected
     */
    boolean sendPasswordResetEmail(String toEmail, String resetUrl);

    /**
     * Dispatches an account security confirmation email immediately following a password reset.
     *
     * @param toEmail  Recipient email address
     * @param name     User's full name
     * @return true if accepted by provider or simulated; false if rejected
     */
    boolean sendPasswordResetConfirmationEmail(String toEmail, String name);

    /**
     * Dispatches an official municipal welcome email to a newly registered citizen.
     * Explains defect reporting and transparent tracking without falsely claiming unverified email verification.
     *
     * @param toEmail  Recipient email address
     * @param name     Citizen's legal name
     * @return true if accepted by provider or simulated; false if rejected
     */
    boolean sendWelcomeEmail(String toEmail, String name);

    /**
     * Dispatches an official municipal intake receipt email to the reporting citizen upon complaint creation.
     *
     * @param toEmail      Recipient citizen email address
     * @param citizenName  Reporting citizen's name
     * @param referenceId  Complaint reference identifier (e.g. CF-2026-00124)
     * @param title        Brief title of the civic defect
     * @param category     Municipal issue category (e.g. ROADS, STREETLIGHTS)
     * @param ward         Municipal ward designation
     * @return true if accepted by provider or simulated; false if rejected
     */
    boolean sendComplaintSubmittedEmail(String toEmail, String citizenName, String referenceId, String title, String category, String ward);

    /**
     * Dispatches a lifecycle status update email to the reporting citizen when work progresses.
     *
     * @param toEmail      Recipient citizen email address
     * @param citizenName  Reporting citizen's name
     * @param referenceId  Complaint reference identifier
     * @param title        Brief title of the civic defect
     * @param oldStatus    Previous complaint status
     * @param newStatus    Updated complaint status
     * @param notes        Public operational notes or resolution remarks
     * @return true if accepted by provider or simulated; false if rejected
     */
    boolean sendComplaintStatusUpdateEmail(String toEmail, String citizenName, String referenceId, String title, String oldStatus, String newStatus, String notes);

    /**
     * Dispatches an official task assignment dispatch notification to an assigned worker or officer.
     *
     * @param toEmail        Recipient personnel email address
     * @param recipientName  Assigned personnel's name
     * @param role           Assigned role description (e.g. FIELD_WORKER)
     * @param referenceId    Complaint reference identifier
     * @param title          Brief title of the civic defect
     * @param priority       Priority level (LOW, MEDIUM, HIGH, CRITICAL)
     * @param ward           Municipal ward designation
     * @return true if accepted by provider or simulated; false if rejected
     */
    boolean sendComplaintAssignmentEmail(String toEmail, String recipientName, String role, String referenceId, String title, String priority, String ward);

    /**
     * Dispatches a new complaint intake alert to relevant municipal officers for triage and dispatch.
     *
     * @param toEmail      Recipient municipal officer email address
     * @param officerName  Officer's name
     * @param referenceId  Complaint reference identifier
     * @param title        Brief title of the civic defect
     * @param priority     Assessed priority level
     * @param ward         Municipal ward designation
     * @return true if accepted by provider or simulated; false if rejected
     */
    boolean sendNewComplaintAlertEmail(String toEmail, String officerName, String referenceId, String title, String priority, String ward);
}
