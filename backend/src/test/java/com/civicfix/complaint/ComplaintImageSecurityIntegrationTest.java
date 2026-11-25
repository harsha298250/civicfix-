package com.civicfix.complaint;

import com.civicfix.auth.dto.LoginRequest;
import com.civicfix.auth.dto.RegisterCitizenRequest;
import com.civicfix.complaint.dto.CreateComplaintRequest;
import com.civicfix.storage.TestImageHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Complaint Image Storage Security & RBAC Integration Tests")
class ComplaintImageSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.civicfix.user.UserRepository userRepository;

    @Autowired
    private ComplaintPhotoRepository complaintPhotoRepository;

    private MockHttpSession citizen1Session;
    private MockHttpSession citizen2Session;
    private MockHttpSession adminSession;
    private MockHttpSession officerSession;

    private String complaintId;
    private String photoId;

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

    @BeforeEach
    void setUp() throws Exception {
        // 1. Citizen 1 (Alex Morgan)
        citizen1Session = loginAs("alex.m@citizen.org", "CivicSecure#2026!");

        // 2. Admin & Officer
        adminSession = loginAs("priya.sharma@admin.metro.gov", "CivicSecure#2026!");
        officerSession = loginAs("elena.vance@metro.gov", "CivicSecure#2026!");

        // 3. Register and login Citizen 2 (unauthorized other citizen)
        String rand = UUID.randomUUID().toString().substring(0, 8);
        String citizen2Email = "citizen2." + rand + "@citizen.org";
        RegisterCitizenRequest regReq = new RegisterCitizenRequest();
        regReq.setName("Second Citizen");
        regReq.setEmail(citizen2Email);
        regReq.setPassword("CivicSecure#2026!");
        regReq.setPhone("+91 97" + (10000000 + (int)(Math.random() * 89999999)));

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated());

        userRepository.findByEmailIgnoreCase(citizen2Email).ifPresent(u -> {
            u.setAccountStatus("ACTIVE");
            u.setEmailVerified(true);
            userRepository.save(u);
        });

        citizen2Session = loginAs(citizen2Email, "CivicSecure#2026!");

        // 4. Citizen 1 creates a complaint with 1 photo
        byte[] photoBytes = TestImageHelper.createValidJpeg(10, 10);
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "road_damage.jpg",
                "image/jpeg",
                photoBytes
        );

        MvcResult createResult = mockMvc.perform(multipart("/api/complaints")
                        .file(file)
                        .param("title", "Broken Asphalt Pit")
                        .param("description", "Pothole on 2nd Avenue causing safety concerns.")
                        .param("category", "Pothole & Road Damage")
                        .param("priority", "HIGH")
                        .param("address", "2nd Avenue Anna Nagar")
                        .param("ward", "Ward 08")
                        .param("latitude", "13.0850000")
                        .param("longitude", "80.2150000")
                        .with(csrf())
                        .session(citizen1Session))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(createResult.getResponse().getContentAsString());
        complaintId = json.get("data").get("id").asText();
        var photos = complaintPhotoRepository.findByComplaintIdOrderByCreatedAtAsc(UUID.fromString(complaintId));
        assertFalse(photos.isEmpty());
        photoId = photos.get(0).getId().toString();
    }

    @Test
    @DisplayName("Authorized citizen who created the complaint can retrieve presigned URLs")
    void testAuthorizedCitizen_CanRetrievePresignedUrls() throws Exception {
        // Individual photo presigned URL
        mockMvc.perform(get("/api/complaints/" + complaintId + "/photos/" + photoId + "/presigned-url")
                        .session(citizen1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.photoId", is(photoId)))
                .andExpect(jsonPath("$.data.presignedUrl", notNullValue()));

        // All complaint photos presigned URLs
        mockMvc.perform(get("/api/complaints/" + complaintId + "/photos/presigned-urls")
                        .session(citizen1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].photoId", is(photoId)))
                .andExpect(jsonPath("$.data[0].presignedUrl", notNullValue()));
    }

    @Test
    @DisplayName("Unauthorized citizen cannot view presigned URLs of another citizen's complaint")
    void testUnauthorizedCitizen_DeniedPresignedUrl_Returns403() throws Exception {
        // Individual photo presigned URL denied
        mockMvc.perform(get("/api/complaints/" + complaintId + "/photos/" + photoId + "/presigned-url")
                        .session(citizen2Session))
                .andExpect(status().isForbidden());

        // Batch photo presigned URLs denied
        mockMvc.perform(get("/api/complaints/" + complaintId + "/photos/presigned-urls")
                        .session(citizen2Session))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Municipal Staff and Admin can view complaint photos presigned URLs")
    void testAuthorizedStaffAndAdmin_CanViewPresignedUrls() throws Exception {
        // Central Officer (Ward 08)
        mockMvc.perform(get("/api/complaints/" + complaintId + "/photos/" + photoId + "/presigned-url")
                        .session(officerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.presignedUrl", notNullValue()));

        // Admin
        mockMvc.perform(get("/api/complaints/" + complaintId + "/photos/" + photoId + "/presigned-url")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.presignedUrl", notNullValue()));
    }

    @Test
    @DisplayName("Unauthorized citizen cannot delete photos from another citizen's complaint")
    void testUnauthorizedCitizen_CannotDeletePhoto_Returns403() throws Exception {
        mockMvc.perform(delete("/api/complaints/" + complaintId + "/photos/" + photoId)
                        .with(csrf())
                        .session(citizen2Session))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Owning citizen can delete a photo on their pending complaint")
    void testOwningCitizen_CanDeletePhoto() throws Exception {
        mockMvc.perform(delete("/api/complaints/" + complaintId + "/photos/" + photoId)
                        .with(csrf())
                        .session(citizen1Session))
                .andExpect(status().isOk());

        // Verifying photo is removed
        mockMvc.perform(get("/api/complaints/" + complaintId + "/photos/presigned-urls")
                        .session(citizen1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }
}
