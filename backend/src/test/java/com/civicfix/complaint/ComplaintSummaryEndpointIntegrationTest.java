package com.civicfix.complaint;

import com.civicfix.auth.dto.LoginRequest;
import com.civicfix.complaint.dto.CreateComplaintRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ComplaintSummaryEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private MockHttpSession loginAs(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession();
    }

    private String createComplaint(MockHttpSession session, String title, String desc, String category) throws Exception {
        CreateComplaintRequest req = new CreateComplaintRequest();
        req.setTitle(title);
        req.setDescription(desc);
        req.setCategory(category);
        req.setPriority(ComplaintPriority.MEDIUM);
        req.setAddress("Anna Salai, Chennai");
        req.setWard("Ward 04");
        req.setLatitude(new BigDecimal("13.0827"));
        req.setLongitude(new BigDecimal("80.2707"));

        MvcResult result = mockMvc.perform(post("/api/complaints")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("data").get("id").asText();
    }

    @Test
    @DisplayName("AI Summary: Authorized OFFICER can retrieve structured executive summary")
    void testOfficerCanRetrieveAiSummary() throws Exception {
        MockHttpSession citizenSession = loginAs("alex.m@citizen.org", "CivicSecure#2026!");
        String complaintId = createComplaint(
                citizenSession,
                "Flooded stormwater drain",
                "Monsoon overflow has blocked the service road. Water entering nearby residences.",
                "DRAINAGE"
        );

        MockHttpSession officerSession = loginAs("elena.vance@metro.gov", "CivicSecure#2026!");

        mockMvc.perform(get("/api/complaints/" + complaintId + "/ai/summary")
                        .session(officerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.defectCore").value(containsString("Flooded stormwater drain")))
                .andExpect(jsonPath("$.data.locationContext").value(containsString("Ward 04")))
                .andExpect(jsonPath("$.data.recommendedAction").value(containsString("Drain")))
                .andExpect(jsonPath("$.data.summary").value(containsString("Issue Briefing")));
    }

    @Test
    @DisplayName("AI Summary RBAC: CITIZEN cannot access internal AI staff summary (403 Forbidden)")
    void testCitizenCannotAccessStaffSummary() throws Exception {
        MockHttpSession citizenSession = loginAs("alex.m@citizen.org", "CivicSecure#2026!");
        String complaintId = createComplaint(citizenSession, "Pothole on 1st Main", "Pothole depth 15cm", "ROADS");

        mockMvc.perform(get("/api/complaints/" + complaintId + "/ai/summary")
                        .session(citizenSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AI Summary RBAC: WORKER cannot access internal AI staff summary (403 Forbidden)")
    void testWorkerCannotAccessStaffSummary() throws Exception {
        MockHttpSession citizenSession = loginAs("alex.m@citizen.org", "CivicSecure#2026!");
        String complaintId = createComplaint(citizenSession, "Fallen wire", "Cable hanging loose", "STREET_LIGHT");

        MockHttpSession workerSession = loginAs("marcus.chen@fieldops.gov", "CivicSecure#2026!");

        mockMvc.perform(get("/api/complaints/" + complaintId + "/ai/summary")
                        .session(workerSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AI Summary Security: Unauthenticated request returns 401 Unauthorized")
    void testUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/complaints/00000000-0000-0000-0000-000000000001/ai/summary"))
                .andExpect(status().isUnauthorized());
    }
}
