package com.civicfix.ai.summary;

import com.civicfix.complaint.Complaint;
import com.civicfix.complaint.ComplaintPhoto;
import com.civicfix.complaint.ComplaintPriority;
import com.civicfix.complaint.ComplaintStatus;
import com.civicfix.security.UserPrincipal;
import com.civicfix.user.Role;
import com.civicfix.user.RoleName;
import com.civicfix.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ComplaintSummaryServiceTest {

    private ComplaintSummaryService summaryService;
    private UserPrincipal officerPrincipal;

    @BeforeEach
    public void setUp() {
        summaryService = new ComplaintSummaryService();

        User officerUser = new User();
        officerUser.setId(UUID.randomUUID());
        officerUser.setEmail("officer@metro.gov");
        Role offRole = new Role();
        offRole.setName(RoleName.OFFICER);
        officerUser.setRoles(Set.of(offRole));
        officerPrincipal = new UserPrincipal(officerUser);
    }

    @Test
    @DisplayName("Synthesizes concise executive summary for English complaint with photo evidence")
    public void testEnglishComplaintSummary() {
        Complaint c = new Complaint();
        c.setId(UUID.randomUUID());
        c.setReferenceId("CF-2026-EN001");
        c.setTitle("Deep pothole near Anna Nagar roundabout");
        c.setDescription("A massive 2-foot crater has opened in the right lane following rain. Two two-wheelers skidded this morning.");
        c.setCategory("ROADS");
        c.setPriority(ComplaintPriority.HIGH);
        c.setStatus(ComplaintStatus.SUBMITTED);
        c.setWard("Ward 12");
        c.setAddress("2nd Avenue, Anna Nagar East");
        c.setLatitude(new BigDecimal("13.0850"));
        c.setLongitude(new BigDecimal("80.2150"));

        ComplaintPhoto photo = new ComplaintPhoto();
        photo.setPhotoUrl("uploads/evidence/pothole1.jpg");
        c.setPhotos(List.of(photo));

        ComplaintSummaryResponse response = summaryService.generateSummary(c, officerPrincipal);

        assertNotNull(response);
        assertEquals("CF-2026-EN001", response.getReferenceId());
        assertEquals("Deep pothole near Anna Nagar roundabout", response.getDefectCore());
        assertTrue(response.getLocationContext().contains("Ward 12"));
        assertTrue(response.getEvidenceStatus().contains("1 photograph attached"));
        assertTrue(response.getEvidenceStatus().contains("GPS coordinates locked"));
        assertTrue(response.getRecommendedAction().contains("Road Maintenance"));
        assertEquals("en", response.getLanguage());
        assertTrue(response.getSummary().contains("Deep pothole near Anna Nagar roundabout"));
    }

    @Test
    @DisplayName("Synthesizes summary for Tamil Unicode complaint narrative")
    public void testTamilComplaintSummary() {
        Complaint c = new Complaint();
        c.setId(UUID.randomUUID());
        c.setReferenceId("CF-2026-TA002");
        c.setTitle("குடிநீர் குழாய் உடைந்து வீணாகிறது");
        c.setDescription("சாலையில் குடிநீர் குழாய் உடைந்து தண்ணீர் ஆறாக ஓடுகிறது. கடந்த இரண்டு நாட்களாக குடிநீர் இல்லை.");
        c.setCategory("WATER_SEWERAGE");
        c.setPriority(ComplaintPriority.CRITICAL);
        c.setStatus(ComplaintStatus.SUBMITTED);
        c.setWard("Ward 05");
        c.setAddress("தியாகராய நகர், சென்னை");

        ComplaintSummaryResponse response = summaryService.generateSummary(c, officerPrincipal);

        assertNotNull(response);
        assertEquals("CF-2026-TA002", response.getReferenceId());
        assertEquals("குடிநீர் குழாய் உடைந்து வீணாகிறது", response.getDefectCore());
        assertTrue(response.getLanguage().equals("ta") || response.getLanguage().equals("mixed"));
        assertTrue(response.getRecommendedAction().contains("Water"));
        assertTrue(response.getRecommendedAction().contains("24 Hours"));
        assertTrue(response.getImpactAssessment().contains("Immediate public safety hazard") || response.getImpactAssessment().contains("disruption"));
    }

    @Test
    @DisplayName("Handles null or empty complaint gracefully without throwing NPE")
    public void testNullComplaintGracefulHandling() {
        ComplaintSummaryResponse response = summaryService.generateSummary(null, officerPrincipal);

        assertNotNull(response);
        assertNull(response.getComplaintId());
        assertTrue(response.getSummary().contains("No complaint data provided"));
    }

    @Test
    @DisplayName("Appends dispute reopen indicator when complaint was reopened")
    public void testReopenedComplaintSummary() {
        Complaint c = new Complaint();
        c.setId(UUID.randomUUID());
        c.setReferenceId("CF-2026-REOPEN");
        c.setTitle("Broken streetlight flickering");
        c.setDescription("Light was marked fixed but bulb failed again next evening.");
        c.setCategory("STREET_LIGHT");
        c.setPriority(ComplaintPriority.MEDIUM);
        c.setStatus(ComplaintStatus.REOPENED);
        c.setReopenCount(2);

        ComplaintSummaryResponse response = summaryService.generateSummary(c, officerPrincipal);

        assertNotNull(response);
        assertTrue(response.getSummary().contains("reopened 2 times for dispute review"));
    }
}
