package com.civicfix.auth.firebase;

import com.civicfix.auth.dto.FirebaseSessionRequest;
import com.civicfix.common.exception.UnauthorizedException;
import com.civicfix.user.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FirebaseAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private FirebaseTokenVerifier tokenVerifier;

    private Role citizenRole;
    private Role officerRole;

    @BeforeEach
    void setUp() {
        citizenRole = roleRepository.findByName(RoleName.CITIZEN)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.CITIZEN)));
        officerRole = roleRepository.findByName(RoleName.OFFICER)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.OFFICER)));
    }

    @Test
    @DisplayName("1. Valid Firebase ID Token creates new CITIZEN account and establishes session")
    void testValidFirebaseIdTokenNewCitizenRegistration() throws Exception {
        String token = "valid-token-" + UUID.randomUUID();
        String uid = "fb-uid-" + UUID.randomUUID();
        String email = "fb.citizen." + System.currentTimeMillis() + "@civicfix.in";

        when(tokenVerifier.verifyIdToken(eq(token)))
                .thenReturn(new FirebaseTokenClaims(uid, email, true, "Praveen Kumar", Map.of()));

        String uniquePhoneSuffix = String.format("%06d", Math.abs(UUID.randomUUID().hashCode()) % 1000000);
        FirebaseSessionRequest req = new FirebaseSessionRequest(token, "Praveen Kumar", "Ward 10, Chennai", "+91 9840" + uniquePhoneSuffix);

        MvcResult result = mockMvc.perform(post("/api/auth/firebase/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.email", is(email)))
                .andExpect(jsonPath("$.data.role", is("CITIZEN")))
                .andExpect(jsonPath("$.data.roles", hasItem("CITIZEN")))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session, "Server-side HTTP session must be created");

        // Verify database persistence
        User persisted = userRepository.findByFirebaseUid(uid).orElse(null);
        assertNotNull(persisted, "User must be persisted with firebase_uid in Neon database");
        assertEquals(email, persisted.getEmail());
        assertEquals("ACTIVE", persisted.getAccountStatus());
    }

    @Test
    @DisplayName("2. Existing citizen account linking when email matches and email is verified")
    void testExistingCitizenAccountLinking() throws Exception {
        String email = "existing.citizen." + System.currentTimeMillis() + "@civicfix.in";
        User existingUser = new User();
        existingUser.setFullName("Anitha S");
        existingUser.setEmail(email);
        existingUser.setPasswordHash(passwordEncoder.encode("ExistingPass#123"));
        existingUser.setWard("Ward 04");
        existingUser.setAccountStatus("ACTIVE");
        existingUser.setRoles(Set.of(citizenRole));
        existingUser = userRepository.save(existingUser);

        String token = "token-link-" + UUID.randomUUID();
        String uid = "fb-linked-uid-" + UUID.randomUUID();

        when(tokenVerifier.verifyIdToken(eq(token)))
                .thenReturn(new FirebaseTokenClaims(uid, email, true, "Anitha S", Map.of()));

        FirebaseSessionRequest req = new FirebaseSessionRequest(token);

        mockMvc.perform(post("/api/auth/firebase/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.email", is(email)))
                .andExpect(jsonPath("$.data.role", is("CITIZEN")));

        User updated = userRepository.findById(existingUser.getId()).orElseThrow();
        assertEquals(uid, updated.getFirebaseUid(), "Existing citizen must have firebase_uid mapped");
    }

    @Test
    @DisplayName("3. Unverified email handling rejects account linking with 400 Bad Request")
    void testUnverifiedEmailHandlingRejectsLinking() throws Exception {
        String email = "unverified.citizen." + System.currentTimeMillis() + "@civicfix.in";
        User existingUser = new User();
        existingUser.setFullName("Karthik M");
        existingUser.setEmail(email);
        existingUser.setPasswordHash(passwordEncoder.encode("SecurePass#123"));
        existingUser.setWard("Ward 05");
        existingUser.setAccountStatus("ACTIVE");
        existingUser.setRoles(Set.of(citizenRole));
        userRepository.save(existingUser);

        String token = "unverified-token-" + UUID.randomUUID();
        String uid = "fb-unverified-" + UUID.randomUUID();

        // emailVerified = false
        when(tokenVerifier.verifyIdToken(eq(token)))
                .thenReturn(new FirebaseTokenClaims(uid, email, false, "Karthik Impersonator", Map.of()));

        FirebaseSessionRequest req = new FirebaseSessionRequest(token);

        mockMvc.perform(post("/api/auth/firebase/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("Firebase email is not verified")));

        // Verify account was NOT linked
        User checked = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertNull(checked.getFirebaseUid(), "Unverified Firebase identity must not link to existing account");
    }

    @Test
    @DisplayName("4. Duplicate identity prevention rejects linking when account already linked to another UID")
    void testDuplicateIdentityPrevention() throws Exception {
        String email = "duplicate.prevent." + System.currentTimeMillis() + "@civicfix.in";
        User existingUser = new User();
        existingUser.setFullName("Deepa K");
        existingUser.setEmail(email);
        existingUser.setPasswordHash(passwordEncoder.encode("SecurePass#123"));
        existingUser.setWard("Ward 06");
        existingUser.setAccountStatus("ACTIVE");
        existingUser.setFirebaseUid("original-fb-uid-" + UUID.randomUUID());
        existingUser.setRoles(Set.of(citizenRole));
        userRepository.save(existingUser);

        String token = "diff-token-" + UUID.randomUUID();
        String differentUid = "conflicting-fb-uid-" + UUID.randomUUID();

        when(tokenVerifier.verifyIdToken(eq(token)))
                .thenReturn(new FirebaseTokenClaims(differentUid, email, true, "Deepa K", Map.of()));

        FirebaseSessionRequest req = new FirebaseSessionRequest(token);

        mockMvc.perform(post("/api/auth/firebase/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", containsString("already linked to a different Firebase identity")));
    }

    @Test
    @DisplayName("5. Unverified Firebase email rejects staff account linking; verified email preserves staff role")
    void testStaffAccountRestrictionsForbidden() throws Exception {
        String staffEmail = "officer.secure." + System.currentTimeMillis() + "@metro.gov";
        User officerUser = new User();
        officerUser.setFullName("Officer Rajan");
        officerUser.setEmail(staffEmail);
        officerUser.setPasswordHash(passwordEncoder.encode("CivicSecure#2026!"));
        officerUser.setDepartment("Roads & Bridges");
        officerUser.setWard("Ward 07");
        officerUser.setAccountStatus("ACTIVE");
        officerUser.setRoles(Set.of(officerRole));
        userRepository.save(officerUser);

        String unverifiedToken = "staff-unverified-" + UUID.randomUUID();
        String unverifiedUid = "fb-unverified-" + UUID.randomUUID();

        // 1. Unverified email must be rejected
        when(tokenVerifier.verifyIdToken(eq(unverifiedToken)))
                .thenReturn(new FirebaseTokenClaims(unverifiedUid, staffEmail, false, "Officer Rajan", Map.of()));

        FirebaseSessionRequest unverifiedReq = new FirebaseSessionRequest(unverifiedToken);

        mockMvc.perform(post("/api/auth/firebase/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unverifiedReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Firebase email is not verified")));

        // 2. Verified email successfully links and preserves OFFICER role
        String verifiedToken = "staff-token-" + UUID.randomUUID();
        String verifiedUid = "fb-staff-uid-" + UUID.randomUUID();

        when(tokenVerifier.verifyIdToken(eq(verifiedToken)))
                .thenReturn(new FirebaseTokenClaims(verifiedUid, staffEmail, true, "Officer Rajan", Map.of()));

        FirebaseSessionRequest verifiedReq = new FirebaseSessionRequest(verifiedToken);

        mockMvc.perform(post("/api/auth/firebase/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifiedReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role", is("OFFICER")))
                .andExpect(jsonPath("$.data.roles", hasItem("OFFICER")));

        User linked = userRepository.findByFirebaseUid(verifiedUid).orElse(null);
        assertNotNull(linked);
        assertEquals(officerUser.getId(), linked.getId());
        assertTrue(linked.hasRole(RoleName.OFFICER));
    }

    @Test
    @DisplayName("6. Role preservation: Neon database roles are source of truth, ignoring client claims")
    void testRolePreservationFromNeon() throws Exception {
        String email = "role.preserve." + System.currentTimeMillis() + "@civicfix.in";
        String uid = "fb-citizen-role-" + UUID.randomUUID();

        User citizen = new User();
        citizen.setFullName("Sundar P");
        citizen.setEmail(email);
        citizen.setPasswordHash(passwordEncoder.encode("Pass#123"));
        citizen.setFirebaseUid(uid);
        citizen.setAccountStatus("ACTIVE");
        citizen.setRoles(Set.of(citizenRole));
        userRepository.save(citizen);

        String token = "token-spoofed-role-" + UUID.randomUUID();
        // Client token attempts to claim ADMIN privileges in custom claims
        when(tokenVerifier.verifyIdToken(eq(token)))
                .thenReturn(new FirebaseTokenClaims(uid, email, true, "Sundar P", Map.of("role", "ADMIN", "admin", true)));

        FirebaseSessionRequest req = new FirebaseSessionRequest(token);

        mockMvc.perform(post("/api/auth/firebase/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role", is("CITIZEN")))
                .andExpect(jsonPath("$.data.roles", hasItem("CITIZEN")))
                .andExpect(jsonPath("$.data.roles", not(hasItem("ADMIN"))));
    }

    @Test
    @DisplayName("7. Invalid Firebase ID token returns 401 Unauthorized")
    void testInvalidIdTokenRejected() throws Exception {
        String token = "invalid-jwt-token";
        when(tokenVerifier.verifyIdToken(eq(token)))
                .thenThrow(new UnauthorizedException("Invalid Firebase ID token."));

        FirebaseSessionRequest req = new FirebaseSessionRequest(token);

        mockMvc.perform(post("/api/auth/firebase/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", containsString("Invalid Firebase ID token")));
    }

    @Test
    @DisplayName("8. Expired Firebase ID token returns 401 Unauthorized")
    void testExpiredIdTokenRejected() throws Exception {
        String token = "expired-token-999";
        when(tokenVerifier.verifyIdToken(eq(token)))
                .thenThrow(new UnauthorizedException("Firebase ID token has expired. Please re-authenticate."));

        FirebaseSessionRequest req = new FirebaseSessionRequest(token);

        mockMvc.perform(post("/api/auth/firebase/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", containsString("token has expired")));
    }

    @Test
    @DisplayName("9. Wrong Firebase project/audience returns 401 Unauthorized")
    void testWrongFirebaseAudienceRejected() throws Exception {
        String token = "wrong-project-token";
        when(tokenVerifier.verifyIdToken(eq(token)))
                .thenThrow(new UnauthorizedException("Firebase ID token audience mismatch. Invalid project token."));

        FirebaseSessionRequest req = new FirebaseSessionRequest(token);

        mockMvc.perform(post("/api/auth/firebase/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", containsString("audience mismatch")));
    }

    @Test
    @DisplayName("10. Session creation and authenticated access to /api/auth/me")
    void testSessionCreationAndMeEndpoint() throws Exception {
        String uid = "fb-session-test-" + UUID.randomUUID();
        String email = "session.test." + System.currentTimeMillis() + "@civicfix.in";
        String token = "session-token-" + UUID.randomUUID();

        when(tokenVerifier.verifyIdToken(eq(token)))
                .thenReturn(new FirebaseTokenClaims(uid, email, true, "Session User", Map.of()));

        FirebaseSessionRequest req = new FirebaseSessionRequest(token, "Session User", "Ward 02", null);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/firebase/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session, "HTTP session must exist");

        // Use the established session to access /api/auth/me
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.email", is(email)))
                .andExpect(jsonPath("$.data.name", is("Session User")));
    }

    @Test
    @DisplayName("11. Logout invalidates session established via Firebase when CSRF is provided")
    void testLogoutTerminatesSession() throws Exception {
        String uid = "fb-logout-test-" + UUID.randomUUID();
        String email = "logout.test." + System.currentTimeMillis() + "@civicfix.in";
        String token = "logout-token-" + UUID.randomUUID();

        when(tokenVerifier.verifyIdToken(eq(token)))
                .thenReturn(new FirebaseTokenClaims(uid, email, true, "Logout User", Map.of()));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/firebase/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FirebaseSessionRequest(token))))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session);

        // Perform logout with valid CSRF token
        mockMvc.perform(post("/api/auth/logout").with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        // Session must now be invalid or clear
        assertTrue(session.isInvalid(), "Session must be invalidated upon logout");
    }

    @Test
    @DisplayName("12. CSRF protection strictly enforced for mutating endpoints on Firebase sessions")
    void testCsrfProtectionEnforced() throws Exception {
        String uid = "fb-csrf-test-" + UUID.randomUUID();
        String email = "csrf.test." + System.currentTimeMillis() + "@civicfix.in";
        String token = "csrf-token-" + UUID.randomUUID();

        when(tokenVerifier.verifyIdToken(eq(token)))
                .thenReturn(new FirebaseTokenClaims(uid, email, true, "CSRF User", Map.of()));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/firebase/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FirebaseSessionRequest(token))))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session);

        // Attempting mutating request without CSRF token must be rejected with 403 Forbidden
        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isForbidden());
    }
}
