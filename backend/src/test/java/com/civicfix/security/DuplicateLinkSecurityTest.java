package com.civicfix.security;

import com.civicfix.complaint.Complaint;
import com.civicfix.complaint.ComplaintPriority;
import com.civicfix.complaint.ComplaintStatus;
import com.civicfix.complaint.dto.ComplaintResponse;
import com.civicfix.user.Role;
import com.civicfix.user.RoleName;
import com.civicfix.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Duplicate Link Response Security Tests (ISSUE-SEC-02)")
class DuplicateLinkSecurityTest {

    private Complaint childComplaint;
    private Complaint parentComplaint;
    private User citizenUser;
    private User workerUser;
    private User officerUser;

    private UserPrincipal workerPrincipal;
    private UserPrincipal officerPrincipal;

    @BeforeEach
    void setUp() {
        Role citizenRole = new Role(RoleName.CITIZEN);
        Role workerRole = new Role(RoleName.WORKER);
        Role officerRole = new Role(RoleName.OFFICER);

        citizenUser = new User();
        citizenUser.setId(UUID.randomUUID());
        citizenUser.setFullName("Kavitha Selvam");
        citizenUser.setEmail("kavitha.s@example.com");
        citizenUser.setPhone("+91 98402 34567");
        citizenUser.setRoles(Set.of(citizenRole));

        workerUser = new User();
        workerUser.setId(UUID.randomUUID());
        workerUser.setFullName("Murugan Technician");
        workerUser.setRoles(Set.of(workerRole));
        workerPrincipal = new UserPrincipal(workerUser);

        officerUser = new User();
        officerUser.setId(UUID.randomUUID());
        officerUser.setFullName("Officer Anitha");
        officerUser.setEmail("anitha@metro.gov");
        officerUser.setWard("Ward 08");
        officerUser.setRoles(Set.of(officerRole));
        officerPrincipal = new UserPrincipal(officerUser);

        parentComplaint = new Complaint();
        parentComplaint.setId(UUID.randomUUID());
        parentComplaint.setReferenceId("CF-2026-00100");
        parentComplaint.setTitle("Broken Water Pipe");
        parentComplaint.setCategory("WATER_SEWERAGE");
        parentComplaint.setPriority(ComplaintPriority.HIGH);
        parentComplaint.setStatus(ComplaintStatus.IN_PROGRESS);
        parentComplaint.setWard("Ward 08");

        childComplaint = new Complaint();
        childComplaint.setId(UUID.randomUUID());
        childComplaint.setReferenceId("CF-2026-00101");
        childComplaint.setTitle("Leakage on 3rd cross");
        childComplaint.setCategory("WATER_SEWERAGE");
        childComplaint.setPriority(ComplaintPriority.HIGH);
        childComplaint.setStatus(ComplaintStatus.UNDER_REVIEW);
        childComplaint.setWard("Ward 08");
        childComplaint.setCitizen(citizenUser);
        childComplaint.setDuplicateOf(parentComplaint);
    }

    @Test
    @DisplayName("Duplicate response projected for worker contains NO citizen email or phone")
    void duplicateResponseForWorkerContainsNoContactData() {
        ComplaintResponse response = ComplaintResponse.fromEntity(childComplaint, workerPrincipal, true);

        assertNotNull(response);
        assertNotNull(response.getCitizen());
        assertEquals("Kavitha Selvam", response.getCitizen().get("name"));
        assertFalse(response.getCitizen().containsKey("email"), "Worker must not receive email in duplicate response");
        assertFalse(response.getCitizen().containsKey("phone"), "Worker must not receive phone in duplicate response");
    }

    @Test
    @DisplayName("Duplicate response projected for officer in detail view includes citizen contact info")
    void duplicateResponseForOfficerIncludesContactData() {
        ComplaintResponse response = ComplaintResponse.fromEntity(childComplaint, officerPrincipal, true);

        assertNotNull(response);
        assertNotNull(response.getCitizen());
        assertEquals("kavitha.s@example.com", response.getCitizen().get("email"));
        assertEquals("+91 98402 34567", response.getCitizen().get("phone"));
    }

    @Test
    @DisplayName("Raw fromEntity without principal omits contact info to fail safe")
    void rawFromEntityOmitsContactInfo() {
        ComplaintResponse response = ComplaintResponse.fromEntity(childComplaint);

        assertNotNull(response);
        assertNotNull(response.getCitizen());
        assertFalse(response.getCitizen().containsKey("email"));
        assertFalse(response.getCitizen().containsKey("phone"));
    }
}
