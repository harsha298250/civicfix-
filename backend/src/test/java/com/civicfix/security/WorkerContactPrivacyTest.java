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

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Worker and Citizen Contact Privacy Tests (ISSUE-SEC-01)")
class WorkerContactPrivacyTest {

    private Complaint testComplaint;
    private User citizenUser;
    private User otherCitizenUser;
    private User workerUser;
    private User officerUser;

    private UserPrincipal citizenPrincipal;
    private UserPrincipal otherCitizenPrincipal;
    private UserPrincipal workerPrincipal;
    private UserPrincipal officerPrincipal;

    @BeforeEach
    void setUp() {
        Role citizenRole = new Role(RoleName.CITIZEN);
        Role workerRole = new Role(RoleName.WORKER);
        Role officerRole = new Role(RoleName.OFFICER);

        citizenUser = new User();
        citizenUser.setId(UUID.randomUUID());
        citizenUser.setFullName("Ramesh Kumar");
        citizenUser.setEmail("ramesh.kumar@example.com");
        citizenUser.setPhone("+91 98401 23456");
        citizenUser.setRoles(Set.of(citizenRole));
        citizenPrincipal = new UserPrincipal(citizenUser);

        otherCitizenUser = new User();
        otherCitizenUser.setId(UUID.randomUUID());
        otherCitizenUser.setFullName("Kavitha Selvam");
        otherCitizenUser.setEmail("kavitha@example.com");
        otherCitizenUser.setPhone("+91 98402 34567");
        otherCitizenUser.setRoles(Set.of(citizenRole));
        otherCitizenPrincipal = new UserPrincipal(otherCitizenUser);

        workerUser = new User();
        workerUser.setId(UUID.randomUUID());
        workerUser.setFullName("Murugan Road Crew");
        workerUser.setEmail("murugan@civicfix.local");
        workerUser.setPhone("+91 98403 45678");
        workerUser.setRoles(Set.of(workerRole));
        workerPrincipal = new UserPrincipal(workerUser);

        officerUser = new User();
        officerUser.setId(UUID.randomUUID());
        officerUser.setFullName("Officer Anitha");
        officerUser.setEmail("anitha.officer@metro.gov");
        officerUser.setWard("Ward 08");
        officerUser.setRoles(Set.of(officerRole));
        officerPrincipal = new UserPrincipal(officerUser);

        testComplaint = new Complaint();
        testComplaint.setId(UUID.randomUUID());
        testComplaint.setReferenceId("CF-2026-99001");
        testComplaint.setTitle("Large pothole near school gate");
        testComplaint.setDescription("Dangerous road depression");
        testComplaint.setCategory("ROADS");
        testComplaint.setPriority(ComplaintPriority.HIGH);
        testComplaint.setStatus(ComplaintStatus.ASSIGNED);
        testComplaint.setWard("Ward 08");
        testComplaint.setAddress("12 Anna Salai, Chennai");
        testComplaint.setCitizen(citizenUser);
        testComplaint.setAssignedWorker(workerUser);
        testComplaint.setAssignedOfficer(officerUser);
    }

    @Test
    @DisplayName("Worker caller receives NO citizen email and NO citizen phone")
    void workerReceivesNoCitizenContactData() {
        ComplaintResponse response = ComplaintResponse.fromEntity(testComplaint, workerPrincipal, true);

        assertNotNull(response);
        Map<String, Object> citizenMap = response.getCitizen();
        assertNotNull(citizenMap, "Citizen object wrapper should exist with identifier/name");
        assertEquals("Ramesh Kumar", citizenMap.get("name"));
        assertEquals(citizenUser.getId().toString(), citizenMap.get("id"));

        // Strictly verify contact details are NOT present
        assertFalse(citizenMap.containsKey("email"), "Worker must not receive citizen email key");
        assertFalse(citizenMap.containsKey("phone"), "Worker must not receive citizen phone key");
        assertNull(citizenMap.get("email"));
        assertNull(citizenMap.get("phone"));
    }

    @Test
    @DisplayName("Owning citizen receives their own email and phone")
    void owningCitizenReceivesOwnContactData() {
        ComplaintResponse response = ComplaintResponse.fromEntity(testComplaint, citizenPrincipal, true);

        assertNotNull(response);
        Map<String, Object> citizenMap = response.getCitizen();
        assertNotNull(citizenMap);
        assertEquals("ramesh.kumar@example.com", citizenMap.get("email"));
        assertEquals("+91 98401 23456", citizenMap.get("phone"));
    }

    @Test
    @DisplayName("Non-owning citizen receives NO email and NO phone")
    void nonOwningCitizenReceivesNoContactData() {
        ComplaintResponse response = ComplaintResponse.fromEntity(testComplaint, otherCitizenPrincipal, true);

        assertNotNull(response);
        Map<String, Object> citizenMap = response.getCitizen();
        assertNotNull(citizenMap);
        assertFalse(citizenMap.containsKey("email"));
        assertFalse(citizenMap.containsKey("phone"));
    }

    @Test
    @DisplayName("Municipal officer receives contact data in detail view (isDetailView = true)")
    void officerReceivesContactDataInDetailView() {
        ComplaintResponse response = ComplaintResponse.fromEntity(testComplaint, officerPrincipal, true);

        assertNotNull(response);
        Map<String, Object> citizenMap = response.getCitizen();
        assertNotNull(citizenMap);
        assertEquals("ramesh.kumar@example.com", citizenMap.get("email"));
        assertEquals("+91 98401 23456", citizenMap.get("phone"));
    }

    @Test
    @DisplayName("Municipal officer list view minimizes contact data (isDetailView = false)")
    void officerReceivesNoContactDataInListView() {
        ComplaintResponse response = ComplaintResponse.fromEntity(testComplaint, officerPrincipal, false);

        assertNotNull(response);
        Map<String, Object> citizenMap = response.getCitizen();
        assertNotNull(citizenMap);
        assertFalse(citizenMap.containsKey("email"), "List view must minimize citizen email");
        assertFalse(citizenMap.containsKey("phone"), "List view must minimize citizen phone");
    }

    @Test
    @DisplayName("Raw fromEntity without principal defaults to fail-closed (no email/phone)")
    void rawFromEntityDefaultsToFailClosed() {
        ComplaintResponse response = ComplaintResponse.fromEntity(testComplaint);

        assertNotNull(response);
        Map<String, Object> citizenMap = response.getCitizen();
        assertNotNull(citizenMap);
        assertFalse(citizenMap.containsKey("email"), "Fail-closed default must not populate email");
        assertFalse(citizenMap.containsKey("phone"), "Fail-closed default must not populate phone");
    }
}
