package com.civicfix.config;

import com.civicfix.config.filter.CorrelationIdFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SecurityHeadersAndCorrelationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Security: Public health endpoint returns required production security headers")
    public void testProductionSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=(self)")))
                .andExpect(header().string("Permissions-Policy", containsString("microphone=()")))
                .andExpect(header().string("Permissions-Policy", containsString("geolocation=(self)")))
                .andExpect(header().exists(CorrelationIdFilter.CORRELATION_ID_HEADER));
    }

    @Test
    @DisplayName("Observability: Inbound X-Request-ID header is sanitized and preserved in response")
    public void testCorrelationIdPreservation() throws Exception {
        String testCorrelationId = "test-client-trace-987654321";

        mockMvc.perform(get("/api/health")
                        .header("X-Request-ID", testCorrelationId))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, testCorrelationId));
    }

    @Test
    @DisplayName("Observability: Unhandled API error includes correlated requestId in response body")
    public void testErrorResponseIncludesRequestId() throws Exception {
        String customRequestId = "trace-err-test-45678";

        mockMvc.perform(get("/api/complaints/non-existent-uuid-reference")
                        .header("X-Request-ID", customRequestId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized()) // Unauthenticated request fails securely
                .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, customRequestId));
    }

    @Test
    @DisplayName("Security: Malicious characters in X-Request-ID are sanitized to prevent header and log injection")
    public void testCorrelationIdHeaderInjectionSanitization() throws Exception {
        String maliciousRequestId = "trace-12345\r\nInjected-Header: evil\n<script>";

        mockMvc.perform(get("/api/health")
                        .header("X-Request-ID", maliciousRequestId))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, "trace-12345Injected-Headerevilscript"));
    }
}
