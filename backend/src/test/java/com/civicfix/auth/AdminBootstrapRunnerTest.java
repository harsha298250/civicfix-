package com.civicfix.auth;

import com.civicfix.user.Role;
import com.civicfix.user.RoleName;
import com.civicfix.user.RoleRepository;
import com.civicfix.user.User;
import com.civicfix.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AdminBootstrapRunnerTest {

    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private PasswordEncoder passwordEncoder;
    private StaffActivationService staffActivationService;
    private AdminBootstrapRunner runner;

    private Role adminRole;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        staffActivationService = mock(StaffActivationService.class);

        adminRole = new Role(RoleName.ADMIN);
        adminRole.setId(5);
        when(roleRepository.findByName(RoleName.ADMIN)).thenReturn(Optional.of(adminRole));

        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "encoded_" + inv.getArgument(0));

        runner = new AdminBootstrapRunner(userRepository, roleRepository, passwordEncoder, staffActivationService);
    }

    @Test
    @DisplayName("Empty or null INITIAL_ADMIN_EMAIL skips provisioning and does not create any account")
    void testEmptyEmailSkipsProvisioning() {
        ReflectionTestUtils.setField(runner, "initialAdminEmail", "");
        ReflectionTestUtils.setField(runner, "initialAdminPassword", "");

        when(userRepository.countByRoleName(RoleName.ADMIN)).thenReturn(1L);

        runner.run(new DefaultApplicationArguments(new String[]{}));

        verify(userRepository, never()).save(any(User.class));
        verify(staffActivationService, never()).createActivationToken(any(), any());
    }

    @Test
    @DisplayName("New admin is provisioned with configured initial password and ACTIVE status")
    void testNewAdminProvisionedWithPassword() {
        ReflectionTestUtils.setField(runner, "initialAdminEmail", "admin@metro.gov");
        ReflectionTestUtils.setField(runner, "initialAdminPassword", "SecureAdminPass#2026");

        when(userRepository.findByEmailIgnoreCase("admin@metro.gov")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        runner.run(new DefaultApplicationArguments(new String[]{}));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User saved = userCaptor.getValue();
        assertEquals("admin@metro.gov", saved.getEmail());
        assertEquals("ACTIVE", saved.getAccountStatus());
        assertTrue(saved.isEmailVerified());
        assertTrue(saved.isCrossWardAccess());
        assertTrue(saved.isCrossDepartmentAccess());
        assertEquals("encoded_SecureAdminPass#2026", saved.getPasswordHash());
        assertTrue(saved.getRoles().contains(adminRole));
    }

    @Test
    @DisplayName("New admin without password generates random password and dispatches activation email")
    void testNewAdminWithoutPasswordDispatchesActivationEmail() {
        ReflectionTestUtils.setField(runner, "initialAdminEmail", "admin@metro.gov");
        ReflectionTestUtils.setField(runner, "initialAdminPassword", "");

        when(userRepository.findByEmailIgnoreCase("admin@metro.gov")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        runner.run(new DefaultApplicationArguments(new String[]{}));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        verify(staffActivationService).createActivationToken(any(User.class), isNull());
    }

    @Test
    @DisplayName("Existing user is granted ROLE_ADMIN idempotently without modifying their existing password")
    void testExistingUserGrantedAdminRolePreservingPassword() {
        ReflectionTestUtils.setField(runner, "initialAdminEmail", "existing.staff@metro.gov");
        ReflectionTestUtils.setField(runner, "initialAdminPassword", "ShouldNotOverwriteExistingPassword");

        Role officerRole = new Role(RoleName.OFFICER);
        officerRole.setId(2);

        User existing = new User();
        existing.setId(UUID.randomUUID());
        existing.setEmail("existing.staff@metro.gov");
        existing.setPasswordHash("existing_secure_password_hash");
        existing.setRoles(new HashSet<>(Set.of(officerRole)));

        when(userRepository.findByEmailIgnoreCase("existing.staff@metro.gov")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        runner.run(new DefaultApplicationArguments(new String[]{}));

        verify(userRepository).save(existing);
        assertEquals("existing_secure_password_hash", existing.getPasswordHash(),
                "Existing password hash must NEVER be overwritten during bootstrap");
        assertTrue(existing.getRoles().contains(adminRole), "Must have ADMIN role attached");
        assertTrue(existing.getRoles().contains(officerRole), "Original roles must be preserved");
    }

    @Test
    @DisplayName("Idempotent execution: existing admin is not re-saved")
    void testIdempotentExecutionWhenAdminAlreadyHasRole() {
        ReflectionTestUtils.setField(runner, "initialAdminEmail", "admin@metro.gov");

        User existingAdmin = new User();
        existingAdmin.setId(UUID.randomUUID());
        existingAdmin.setEmail("admin@metro.gov");
        existingAdmin.setRoles(Set.of(adminRole));

        when(userRepository.findByEmailIgnoreCase("admin@metro.gov")).thenReturn(Optional.of(existingAdmin));

        runner.run(new DefaultApplicationArguments(new String[]{}));

        verify(userRepository, never()).save(any(User.class));
    }
}
