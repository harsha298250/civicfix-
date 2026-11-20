package com.civicfix.auth;

import com.civicfix.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    List<PasswordResetToken> findByUserAndConsumedAtIsNull(User user);

    @Query("SELECT COUNT(t) FROM PasswordResetToken t WHERE t.user = :user AND t.createdAt > :cutoff")
    long countRecentTokensForUser(@Param("user") User user, @Param("cutoff") OffsetDateTime cutoff);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.consumedAt = :now WHERE t.user = :user AND t.consumedAt IS NULL")
    void invalidateOutstandingTokensForUser(@Param("user") User user, @Param("now") OffsetDateTime now);

    @org.springframework.transaction.annotation.Transactional
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.consumedAt = :now WHERE t.tokenHash = :tokenHash AND t.consumedAt IS NULL AND t.expiresAt > :now")
    int consumeTokenAtomic(@Param("tokenHash") String tokenHash, @Param("now") OffsetDateTime now);
}
