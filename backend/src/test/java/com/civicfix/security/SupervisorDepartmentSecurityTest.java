package com.civicfix.security;

import com.civicfix.common.exception.ForbiddenException;
import com.civicfix.complaint.Complaint;
import com.civicfix.supervisor.SupervisorService;
import com.civicfix.user.Role;
import com.civicfix.user.RoleName;
import com.civicfix.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Supervisor Department Authorization Tests (Decision A)")
class SupervisorDepartmentSecurityTest {

    private SupervisorService supervisorService;

    @BeforeEach
    void setUp() {
        supervisorService = new SupervisorService(null, null, null, null);
    }

    @Test
    @DisplayName("Roads supervisor is allowed to manage ROADS complaints")
    void roadsSupervisorAllowedForRoads() {
        Role supervisorRole = new Role(RoleName.SUPERVISOR);
        User roadsSupervisor = new User();
        roadsSupervisor.setId(UUID.randomUUID());
        roadsSupervisor.setFullName("Supervisor Ramesh");
        roadsSupervisor.setDepartment("SDOT Road Maintenance");
        roadsSupervisor.setRoles(Set.of(supervisorRole));

        Complaint roadsComplaint = new Complaint();
        roadsComplaint.setCategory("ROADS");

        UserPrincipal principal = new UserPrincipal(roadsSupervisor);

        assertDoesNotThrow(() ->
                supervisorService.enforceSupervisorDepartmentAccess(roadsComplaint, principal)
        );
    }

    @Test
    @DisplayName("Roads supervisor is rejected with 403 Forbidden when managing STREET_LIGHT complaint")
    void roadsSupervisorForbiddenForStreetLight() {
        Role supervisorRole = new Role(RoleName.SUPERVISOR);
        User roadsSupervisor = new User();
        roadsSupervisor.setId(UUID.randomUUID());
        roadsSupervisor.setFullName("Supervisor Ramesh");
        roadsSupervisor.setDepartment("SDOT Road Maintenance");
        roadsSupervisor.setRoles(Set.of(supervisorRole));

        Complaint lightComplaint = new Complaint();
        lightComplaint.setCategory("STREET_LIGHT");

        UserPrincipal principal = new UserPrincipal(roadsSupervisor);

        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
                supervisorService.enforceSupervisorDepartmentAccess(lightComplaint, principal)
        );

        assertTrue(ex.getMessage().contains("outside their authorized department"));
    }

    @Test
    @DisplayName("Municipal Operations Command supervisor can manage all categories")
    void operationsCommandSupervisorUniversalAccess() {
        Role supervisorRole = new Role(RoleName.SUPERVISOR);
        User opsSupervisor = new User();
        opsSupervisor.setId(UUID.randomUUID());
        opsSupervisor.setFullName("Sarah Jenkins");
        opsSupervisor.setDepartment("Municipal Operations Command");
        opsSupervisor.setCrossDepartmentAccess(true);
        opsSupervisor.setRoles(Set.of(supervisorRole));

        Complaint waterComplaint = new Complaint();
        waterComplaint.setCategory("WATER_SEWERAGE");

        UserPrincipal principal = new UserPrincipal(opsSupervisor);

        assertDoesNotThrow(() ->
                supervisorService.enforceSupervisorDepartmentAccess(waterComplaint, principal)
        );
    }

    @Test
    @DisplayName("Category allowed for department helper correctly maps categories")
    void categoryAllowedForDepartmentMapping() {
        assertTrue(SupervisorService.isCategoryAllowedForDepartment("ROADS", "SDOT Road Crew"));
        assertFalse(SupervisorService.isCategoryAllowedForDepartment("GARBAGE", "SDOT Road Crew"));
        assertTrue(SupervisorService.isCategoryAllowedForDepartment("WATER_SEWERAGE", "Water & Sewerage Board"));
        assertTrue(SupervisorService.isCategoryAllowedForDepartment("DRAINAGE", "Metro Water and Drainage"));
        assertTrue(SupervisorService.isCategoryAllowedForDepartment("STREET_LIGHT", "Street Lighting & Electrical"));
        assertTrue(SupervisorService.isCategoryAllowedForDepartment("GARBAGE", "Solid Waste Management"));
    }

    @Test
    @DisplayName("Similar department names are rejected: no loose substring matching")
    void similarDepartmentNamesRejected() {
        assertFalse(SupervisorService.isCategoryAllowedForDepartment("ROADS", "Off-road Vehicle Racing"));
        assertFalse(SupervisorService.isCategoryAllowedForDepartment("ROADS", "Broadband Infrastructure"));
        assertFalse(SupervisorService.isCategoryAllowedForDepartment("ROADS", "Roadside Advertising"));
        assertFalse(SupervisorService.isCategoryAllowedForDepartment("STREET_LIGHT", "Light Refreshments Committee"));
        assertFalse(SupervisorService.isCategoryAllowedForDepartment("WATER_SEWERAGE", "Watermelon Farming Association"));

        Role supervisorRole = new Role(RoleName.SUPERVISOR);
        User fakeSupervisor = new User();
        fakeSupervisor.setId(UUID.randomUUID());
        fakeSupervisor.setFullName("Imposter Supervisor");
        fakeSupervisor.setDepartment("Off-road Vehicle Racing");
        fakeSupervisor.setRoles(Set.of(supervisorRole));

        Complaint roadsComplaint = new Complaint();
        roadsComplaint.setCategory("ROADS");

        UserPrincipal principal = new UserPrincipal(fakeSupervisor);

        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
                supervisorService.enforceSupervisorDepartmentAccess(roadsComplaint, principal)
        );
        assertTrue(ex.getMessage().contains("outside their authorized department"));
    }

    @Test
    @DisplayName("Supervisor with null or missing department is rejected with 403 Forbidden")
    void missingDepartmentRejected() {
        Role supervisorRole = new Role(RoleName.SUPERVISOR);
        User noDeptSupervisor = new User();
        noDeptSupervisor.setId(UUID.randomUUID());
        noDeptSupervisor.setFullName("Unassigned Supervisor");
        noDeptSupervisor.setDepartment(null);
        noDeptSupervisor.setRoles(Set.of(supervisorRole));

        Complaint roadsComplaint = new Complaint();
        roadsComplaint.setCategory("ROADS");

        UserPrincipal principal = new UserPrincipal(noDeptSupervisor);

        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
                supervisorService.enforceSupervisorDepartmentAccess(roadsComplaint, principal)
        );
        assertTrue(ex.getMessage().contains("no assigned department"));
    }

    @Test
    @DisplayName("Cross-department disabled supervisor is rejected for other departments")
    void crossDepartmentDisabledRejected() {
        Role supervisorRole = new Role(RoleName.SUPERVISOR);
        User supervisor = new User();
        supervisor.setId(UUID.randomUUID());
        supervisor.setFullName("Strict Roads Supervisor");
        supervisor.setDepartment("Road Infrastructure");
        supervisor.setCrossDepartmentAccess(false);
        supervisor.setRoles(Set.of(supervisorRole));

        Complaint lightComplaint = new Complaint();
        lightComplaint.setCategory("STREET_LIGHT");

        UserPrincipal principal = new UserPrincipal(supervisor);

        assertThrows(ForbiddenException.class, () ->
                supervisorService.enforceSupervisorDepartmentAccess(lightComplaint, principal)
        );
    }
}
