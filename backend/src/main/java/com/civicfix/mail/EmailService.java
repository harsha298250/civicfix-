package com.civicfix.mail;

public interface EmailService {

    /**
     * Dispatches a secure password recovery email containing the one-time reset link.
     * Implementations must handle provider delivery failures gracefully without bubbling
     * internal SMTP exceptions to the caller.
     *
     * @param toEmail   Recipient email address
     * @param resetUrl  Complete password reset link containing the one-time raw token
     */
    void sendPasswordResetEmail(String toEmail, String resetUrl);
}
