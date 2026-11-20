package com.civicfix.security;

import com.civicfix.common.exception.ForbiddenException;
import com.civicfix.supervisor.SupervisorService;
import com.civicfix.supervisor.dto.SupervisorWorkerResponse;
import com.civicfix.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Worker Directory Security Tests (Phase 1.2 Task 6)")
class WorkerDirectorySecurityTest {

    @Mock
    private UserRepository userRepository;

    private SupervisorService supervisorService;

    private User supervisorRoads;
    private User supervisorCrossDept;
    private User adminUser;
    private User workerRoads;
    private User workerLighting;

    @BeforeEach
    void setUp() {
        supervisorService = new SupervisorService(null, userRepository, null, null);

        Role supervisorRole = new Role(RoleName.SUPERVISOR);
        Role adminRole = new Role(RoleName.ADMIN);
        Role workerRole = new Role(RoleName.WORKER);

        supervisorRoads = new User();
        supervisorRoads.setId(UUID.randomUUID());
        supervisorRoads.setFullName("Supervisor Ramesh");
        supervisorRoads.setEmail("ramesh@metro.gov");
        supervisorRoads.setDepartment("ROADS");
        supervisorRoads.setCrossDepartmentAccess(false);
        supervisorRoads.setRoles(Set.of(supervisorRole));

        supervisorCrossDept = new User();
        supervisorCrossDept.setId(UUID.randomUUID());
        supervisorCrossDept.setFullName("Supervisor Sarah Jenkins");
        supervisorCrossDept.setEmail("sarah.jenkins@metro.gov");
        supervisorCrossDept.setDepartment("Central Municipal Operations");
        supervisorCrossDept.setCrossDepartmentAccess(true);
        supervisorCrossDept.setRoles(Set.of(supervisorRole));

        adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setFullName("Admin Priya");
        adminUser.setEmail("priya@metro.gov");
        adminUser.setRoles(Set.of(adminRole));

        workerRoads = new User();
        workerRoads.setId(UUID.randomUUID());
        workerRoads.setFullName("Worker Road Ravi");
        workerRoads.setEmail("ravi@fieldops.gov");
        workerRoads.setPhone("+91 91111 22222");
        workerRoads.setDepartment("ROADS");
        workerRoads.setWard("Ward 08");
        workerRoads.setAccountStatus("ACTIVE");
        workerRoads.setTrustScore(95);
        workerRoads.setRoles(Set.of(workerRole));

        workerLighting = new User();
        workerLighting.setId(UUID.randomUUID());
        workerLighting.setFullName("Worker Light Lakshmi");
        workerLighting.setEmail("lakshmi@fieldops.gov");
        workerLighting.setPhone("+91 93333 44444");
        workerLighting.setDepartment("STREET_LIGHT");
        workerLighting.setWard("Ward 12");
        workerLighting.setAccountStatus("ACTIVE");
        workerLighting.setTrustScore(90);
        workerLighting.setRoles(Set.of(workerRole));
    }

    @Test
    @DisplayName("Supervisor querying workers in own department receives only their department workers")
    void supervisorQueriesOwnDepartmentSucceeds() {
        when(userRepository.findActiveUsersByRoleNameAndDepartment(RoleName.WORKER, "ROADS"))
                .thenReturn(List.of(workerRoads));

        UserPrincipal principal = new UserPrincipal(supervisorRoads);
        List<SupervisorWorkerResponse> responses = supervisorService.getActiveWorkers(principal, "ROADS");

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("Worker Road Ravi", responses.get(0).getFullName());
        assertEquals("ROADS", responses.get(0).getDepartment());
        verify(userRepository, times(1)).findActiveUsersByRoleNameAndDepartment(RoleName.WORKER, "ROADS");
    }

    @Test
    @DisplayName("Supervisor querying workers with no filter defaults to own department")
    void supervisorDefaultDepartmentSucceeds() {
        when(userRepository.findActiveUsersByRoleNameAndDepartment(RoleName.WORKER, "ROADS"))
                .thenReturn(List.of(workerRoads));

        UserPrincipal principal = new UserPrincipal(supervisorRoads);
        List<SupervisorWorkerResponse> responses = supervisorService.getActiveWorkers(principal, null);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("Worker Road Ravi", responses.get(0).getFullName());
        verify(userRepository, times(1)).findActiveUsersByRoleNameAndDepartment(RoleName.WORKER, "ROADS");
    }

    @Test
    @DisplayName("Supervisor attempting to query another department is rejected with 403 Forbidden")
    void supervisorQueriesAnotherDepartmentForbidden() {
        UserPrincipal principal = new UserPrincipal(supervisorRoads);

        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
                supervisorService.getActiveWorkers(principal, "STREET_LIGHT")
        );

        assertTrue(ex.getMessage().contains("Supervisors cannot query workers outside their authorized department"));
        verify(userRepository, never()).findActiveUsersByRoleNameAndDepartment(any(), any());
    }

    @Test
    @DisplayName("Supervisor with missing or blank department is rejected with 403 Forbidden (fail-closed)")
    void supervisorMissingDepartmentForbidden() {
        supervisorRoads.setDepartment(null);
        UserPrincipal principalNullDept = new UserPrincipal(supervisorRoads);

        ForbiddenException exNull = assertThrows(ForbiddenException.class, () ->
                supervisorService.getActiveWorkers(principalNullDept, null)
        );
        assertTrue(exNull.getMessage().contains("Supervisor has no assigned department"));

        supervisorRoads.setDepartment("   ");
        UserPrincipal principalBlankDept = new UserPrincipal(supervisorRoads);

        ForbiddenException exBlank = assertThrows(ForbiddenException.class, () ->
                supervisorService.getActiveWorkers(principalBlankDept, null)
        );
        assertTrue(exBlank.getMessage().contains("Supervisor has no assigned department"));
    }

    @Test
    @DisplayName("Cross-department supervisor can query any department or municipal-wide workers")
    void crossDepartmentSupervisorCanQueryAnyDepartment() {
        when(userRepository.findActiveUsersByRoleNameAndDepartment(RoleName.WORKER, "STREET_LIGHT"))
                .thenReturn(List.of(workerLighting));
        when(userRepository.findActiveUsersByRoleName(RoleName.WORKER))
                .thenReturn(List.of(workerRoads, workerLighting));

        UserPrincipal principal = new UserPrincipal(supervisorCrossDept);

        List<SupervisorWorkerResponse> lightResponses = supervisorService.getActiveWorkers(principal, "STREET_LIGHT");
        assertEquals(1, lightResponses.size());
        assertEquals("Worker Light Lakshmi", lightResponses.get(0).getFullName());

        List<SupervisorWorkerResponse> allResponses = supervisorService.getActiveWorkers(principal, "ALL");
        assertEquals(2, allResponses.size());

        List<SupervisorWorkerResponse> nullResponses = supervisorService.getActiveWorkers(principal, null);
        assertEquals(2, nullResponses.size());
    }

    @Test
    @DisplayName("Admin can query any department or all workers without restriction")
    void adminCanQueryAnyDepartment() {
        when(userRepository.findActiveUsersByRoleNameAndDepartment(RoleName.WORKER, "ROADS"))
                .thenReturn(List.of(workerRoads));

        UserPrincipal principal = new UserPrincipal(adminUser);
        List<SupervisorWorkerResponse> responses = supervisorService.getActiveWorkers(principal, "ROADS");

        assertEquals(1, responses.size());
        assertEquals("Worker Road Ravi", responses.get(0).getFullName());
    }

    @Test
    @DisplayName("SupervisorWorkerResponse data minimization: excludes personal phone, email, and trust scores")
    void supervisorWorkerResponseDataMinimization() {
        SupervisorWorkerResponse response = SupervisorWorkerResponse.fromEntity(workerRoads);

        assertEquals(workerRoads.getId(), response.getId());
        assertEquals("Worker Road Ravi", response.getFullName());
        assertEquals("ROADS", response.getDepartment());
        assertEquals("Ward 08", response.getWard());
        assertEquals("ACTIVE", response.getAccountStatus());

        // Verify via reflection that sensitive fields are not declared on SupervisorWorkerResponse
        List<String> fieldNames = Arrays.stream(SupervisorWorkerResponse.class.getDeclaredFields())
                .map(Field::getName)
                .toList();

        assertFalse(fieldNames.contains("email"), "email must not be exposed in SupervisorWorkerResponse");
        assertFalse(fieldNames.contains("phone"), "phone must not be exposed in SupervisorWorkerResponse");
        assertFalse(fieldNames.contains("phoneNumber"), "phoneNumber must not be exposed in SupervisorWorkerResponse");
        assertFalse(fieldNames.contains("trustScore"), "trustScore must not be exposed in SupervisorWorkerResponse");
        assertFalse(fieldNames.contains("passwordHash"), "passwordHash must not be exposed in SupervisorWorkerResponse");

        // Verify getter methods
        List<String> methodNames = Arrays.stream(SupervisorWorkerResponse.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();
        assertFalse(methodNames.contains("getEmail"));
        assertFalse(methodNames.contains("getPhone"));
        assertFalse(methodNames.contains("getTrustScore"));
    }
}
