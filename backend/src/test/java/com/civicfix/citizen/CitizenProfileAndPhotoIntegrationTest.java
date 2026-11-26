package com.civicfix.citizen;

import com.civicfix.auth.dto.LoginRequest;
import com.civicfix.citizen.dto.UpdateCitizenProfileRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CitizenProfileAndPhotoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private MockHttpSession loginAs(String email, String password) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) res.getRequest().getSession();
    }

    @Test
    @DisplayName("Citizen can update Royapuram location and upload/retrieve/delete profile photo with isolation")
    void testProfileLocationUpdateAndPhotoLifecycle() throws Exception {
        MockHttpSession citizenSession = loginAs("alex.m@citizen.org", "CivicSecure#2026!");
        MockHttpSession officerSession = loginAs("officer@civicfix.gov", "CivicSecure#2026!");

        // 1. Update resident location to Zone 05, Royapuram, Chennai
        UpdateCitizenProfileRequest updateReq = new UpdateCitizenProfileRequest();
        updateReq.setDistrict("Chennai");
        updateReq.setZone("Zone 05, Royapuram, Chennai");
        updateReq.setWard("Zone 05, Royapuram, Chennai");

        mockMvc.perform(put("/api/citizen/account/profile")
                        .with(csrf())
                        .session(citizenSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.district", is("Chennai")))
                .andExpect(jsonPath("$.data.zone", is("Zone 05, Royapuram, Chennai")))
                .andExpect(jsonPath("$.data.ward", is("Zone 05, Royapuram, Chennai")));

        // 2. Verify /api/auth/me returns persisted Royapuram location
        mockMvc.perform(get("/api/auth/me").session(citizenSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ward", is("Zone 05, Royapuram, Chennai")));

        // 3. Upload valid PNG profile photo (1x1 valid PNG magic bytes + IHDR)
        byte[] pngBytes = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89,
                0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54,
                0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00, 0x05, 0x00, 0x01,
                0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
                (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };
        MockMultipartFile photoFile = new MockMultipartFile("file", "avatar.png", "image/png", pngBytes);

        MvcResult uploadRes = mockMvc.perform(multipart("/api/citizen/account/profile/photo")
                        .file(photoFile)
                        .with(csrf())
                        .session(citizenSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profilePhotoUrl", startsWith("/api/citizen/account/profile/photo/")))
                .andReturn();

        JsonNode uploadJson = objectMapper.readTree(uploadRes.getResponse().getContentAsString());
        String photoUrl = uploadJson.get("data").get("profilePhotoUrl").asText();

        // 4. Owner can retrieve profile photo; another user (non-admin) is forbidden
        mockMvc.perform(get(photoUrl).session(citizenSession))
                .andExpect(status().isOk());

        mockMvc.perform(get(photoUrl).session(officerSession))
                .andExpect(status().isForbidden());

        // 5. Delete profile photo
        mockMvc.perform(delete("/api/citizen/account/profile/photo")
                        .with(csrf())
                        .session(citizenSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profilePhotoUrl", nullValue()));
    }
}
