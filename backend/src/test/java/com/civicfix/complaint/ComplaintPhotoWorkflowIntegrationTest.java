package com.civicfix.complaint;

import com.civicfix.security.UserPrincipal;
import com.civicfix.user.Role;
import com.civicfix.user.RoleName;
import com.civicfix.user.User;
import com.civicfix.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ComplaintPhotoWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private ComplaintPhotoRepository complaintPhotoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.civicfix.user.RoleRepository roleRepository;

    private static byte[] createValidJpegBytes() throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "jpg", baos);
        return baos.toByteArray();
    }

    private UserPrincipal buildPrincipal(User u) {
        return new UserPrincipal(u);
    }

    @Test
    @DisplayName("Full Photo Workflow: Upload -> Storage -> complaint_photos DB -> Authenticated Retrieval -> Role Isolation & 404")
    void testFullComplaintPhotoUploadAndAuthenticatedRetrieval() throws Exception {
        User owner = userRepository.findByEmailIgnoreCase("alex.m@citizen.org")
                .orElseThrow(() -> new IllegalStateException("Seed citizen Alex not found"));
        User officer = userRepository.findByEmailIgnoreCase("elena.vance@metro.gov")
                .orElseThrow(() -> new IllegalStateException("Seed officer Elena not found"));

        Role citizenRole = roleRepository.findByName(RoleName.CITIZEN)
                .orElseThrow(() -> new IllegalStateException("CITIZEN role not found"));
        User otherCitizen = new User();
        otherCitizen.setFullName("Other Resident");
        otherCitizen.setEmail("other.photo." + UUID.randomUUID() + "@example.com");
        otherCitizen.setPasswordHash("hashed");
        otherCitizen.setAccountStatus("ACTIVE");
        otherCitizen.setEmailVerified(true);
        otherCitizen.setRoles(Set.of(citizenRole));
        otherCitizen = userRepository.save(otherCitizen);

        Complaint complaint = new Complaint();
        complaint.setReferenceId("CF-PHOTO-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        complaint.setTitle("Waterlogged street near school gate");
        complaint.setDescription("Severe drainage blockage causing stagnant water on main road.");
        complaint.setCategory("Drainage & Sewage");
        complaint.setPriority(ComplaintPriority.HIGH);
        complaint.setStatus(ComplaintStatus.SUBMITTED);
        complaint.setAddress("12 Main Road, Chennai");
        complaint.setWard("Ward 08, Anna Nagar");
        complaint.setCitizen(owner);
        complaint = complaintRepository.save(complaint);

        MockMultipartFile jpegFile = new MockMultipartFile(
                "files",
                "road-defect.jpg",
                "image/jpeg",
                createValidJpegBytes()
        );

        // 1. Upload image as complaint owner
        mockMvc.perform(multipart("/api/complaints/{id}/photos", complaint.getId())
                        .file(jpegFile)
                        .with(user(buildPrincipal(owner)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 2. Verify metadata saved in complaint_photos linked to complaint.id
        List<ComplaintPhoto> savedPhotos = complaintPhotoRepository.findByComplaintIdOrderByCreatedAtAsc(complaint.getId());
        assertEquals(1, savedPhotos.size());
        ComplaintPhoto photo = savedPhotos.get(0);
        assertEquals(complaint.getId(), photo.getComplaint().getId());
        assertNotNull(photo.getObjectKey());
        assertTrue(photo.getObjectKey().startsWith("complaints/" + complaint.getId() + "/"));
        assertTrue(
                photo.getPhotoUrl().startsWith("/api/complaints/photos/files/")
                        || photo.getPhotoUrl().startsWith("/api/complaints/evidence/files/")
        );
        assertEquals("image/jpeg", photo.getContentType());

        // 3. Owner retrieves photo via photoUrl -> 200 OK with private no-store headers
        mockMvc.perform(get(photo.getPhotoUrl())
                        .with(user(buildPrincipal(owner))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(content().contentType("image/jpeg"));

        // 4. Owner retrieves photo via ComplaintPhoto UUID -> 200 OK
        mockMvc.perform(get("/api/complaints/photos/files/{fileId}", photo.getId())
                        .with(user(buildPrincipal(owner))))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));

        // 5. Officer can retrieve photo -> 200 OK
        mockMvc.perform(get(photo.getPhotoUrl())
                        .with(user(buildPrincipal(officer))))
                .andExpect(status().isOk());

        // 6. Unauthenticated request -> 401 Unauthorized
        mockMvc.perform(get(photo.getPhotoUrl()))
                .andExpect(status().isUnauthorized());

        // 7. Unrelated citizen -> 403 Forbidden
        mockMvc.perform(get(photo.getPhotoUrl())
                        .with(user(buildPrincipal(otherCitizen))))
                .andExpect(status().isForbidden());

        // 8. Missing photo -> 404 Not Found
        mockMvc.perform(get("/api/complaints/photos/files/nonexistent-photo-123.jpg")
                        .with(user(buildPrincipal(owner))))
                .andExpect(status().isNotFound());
    }
}
