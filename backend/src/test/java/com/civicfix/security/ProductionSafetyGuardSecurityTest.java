package com.civicfix.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProductionSafetyGuard Fail-Closed Enforcement (ISSUE-SEC-04)")
class ProductionSafetyGuardSecurityTest {

    @Test
    @DisplayName("Startup is rejected when allowSeedAccounts is true in prod profile (fail-closed)")
    void startupRejectedWhenAllowSeedAccountsTrue() {
        ProductionSafetyGuard guard = new ProductionSafetyGuard(
                null,
                "StrongProductionPassword987!#",
                true // allowSeedAccounts = true
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, guard::enforceSeedAccountPolicy);
        assertTrue(ex.getMessage().contains("ALLOW_SEED_ACCOUNTS cannot be enabled in the 'prod' profile"));
    }

    @Test
    @DisplayName("Startup is rejected when database password equals dev default in prod")
    void startupRejectedWithDevDefaultPassword() {
        ProductionSafetyGuard guard = new ProductionSafetyGuard(
                null,
                ProductionSafetyGuard.DEV_DEFAULT_DB_PASSWORD,
                false
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, guard::validateDatabasePassword);
        assertTrue(ex.getMessage().contains("Database password cannot be empty or the dev default"));
    }

    @Test
    @DisplayName("Startup is rejected when database password is empty in prod")
    void startupRejectedWithEmptyPassword() {
        ProductionSafetyGuard guard = new ProductionSafetyGuard(
                null,
                "",
                false
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, guard::validateDatabasePassword);
        assertTrue(ex.getMessage().contains("Database password cannot be empty or the dev default"));
    }

    @Test
    @DisplayName("Startup succeeds with unique database password and allowSeedAccounts=false")
    void startupSucceedsWithSecureConfiguration() {
        ProductionSafetyGuard guard = new ProductionSafetyGuard(
                null,
                "ProductionSuperSecretDbKey_2026!#",
                false
        );

        assertDoesNotThrow(guard::validateDatabasePassword);
    }
}
