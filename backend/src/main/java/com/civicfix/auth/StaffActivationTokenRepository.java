package com.civicfix.auth;

import com.civicfix.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffActivationTokenRepository extends JpaRepository<StaffActivationToken, UUID> {

    Optional<StaffActivationToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE StaffActivationToken t SET t.consumedAt = :now WHERE t.tokenHash = :tokenHash AND t.consumedAt IS NULL AND t.expiresAt > :now")
    int consumeTokenAtomic(@Param("tokenHash") String tokenHash, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("UPDATE StaffActivationToken t SET t.consumedAt = :now WHERE t.user = :user AND t.consumedAt IS NULL")
    int invalidateOutstandingTokensForUser(@Param("user") User user, @Param("now") OffsetDateTime now);
}
