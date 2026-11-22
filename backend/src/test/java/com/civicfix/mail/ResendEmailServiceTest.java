package com.civicfix.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ResendEmailServiceTest {

    @Test
    @DisplayName("ResendEmailService dispatches email with correct Bearer auth, payload, and handles 200 OK")
    void testSendEmailSuccess() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.resend.com/emails");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(header("Authorization", "Bearer test_resend_key_123"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.from").value("CivicFix Platform <onboarding@resend.dev>"))
                .andExpect(jsonPath("$.to[0]").value("citizen@example.com"))
                .andExpect(jsonPath("$.subject").value("CivicFix Password Reset Request"))
                .andRespond(withSuccess("{\"id\": \"resend_msg_001\"}", MediaType.APPLICATION_JSON));

        ResendEmailService service = new ResendEmailService(
                "test_resend_key_123",
                "onboarding@resend.dev",
                "https://api.resend.com/emails",
                5000,
                restClient
        );

        boolean result = service.sendPasswordResetEmail("citizen@example.com", "https://civicfix-red.vercel.app/reset-password?token=abc");

        assertTrue(result, "Service should report true when Resend accepts the email with 200 OK");
        server.verify();
    }

    @Test
    @DisplayName("ResendEmailService handles provider rejection (422 Unprocessable) without throwing exceptions")
    void testSendEmailProviderRejection() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.resend.com/emails");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(header("Authorization", "Bearer test_resend_key_123"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"statusCode\": 422, \"message\": \"Domain not verified\", \"name\": \"validation_error\"}"));

        ResendEmailService service = new ResendEmailService(
                "test_resend_key_123",
                "onboarding@resend.dev",
                "https://api.resend.com/emails",
                5000,
                restClient
        );

        boolean result = service.sendWelcomeEmail("citizen@example.com", "Alex Morgan");

        assertFalse(result, "Service should return false when Resend rejects the email");
        server.verify();
    }

    @Test
    @DisplayName("ResendEmailService returns false and logs warning when API key is missing or blank")
    void testMissingKeyReturnsFalse() {
        ResendEmailService service = new ResendEmailService(
                "",
                "onboarding@resend.dev",
                "https://api.resend.com/emails",
                5000,
                null
        );

        boolean welcomeResult = service.sendWelcomeEmail("citizen@example.com", "Alex Morgan");
        boolean resetResult = service.sendPasswordResetConfirmationEmail("citizen@example.com", "Alex Morgan");
        boolean intakeResult = service.sendComplaintSubmittedEmail("citizen@example.com", "Alex", "CF-2026-001", "Pothole", "ROADS", "Ward 08");

        assertFalse(welcomeResult, "Service should return false when API key is missing");
        assertFalse(resetResult, "Service should return false for password confirmation when API key is missing");
        assertFalse(intakeResult, "Service should return false for intake receipt when API key is missing");
    }

    @Test
    @DisplayName("ResendEmailService rejects invalid recipient format before making network request")
    void testInvalidRecipientSkipped() {
        ResendEmailService service = new ResendEmailService(
                "key_present",
                "onboarding@resend.dev",
                "https://api.resend.com/emails",
                5000,
                null
        );

        assertFalse(service.sendEmail(null, "Test", "Content"));
        assertFalse(service.sendEmail("invalid-email-no-at-sign", "Test", "Content"));
    }
}
