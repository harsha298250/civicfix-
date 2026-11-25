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
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    @Query("SELECT COUNT(t) FROM EmailVerificationToken t WHERE t.user = :user AND t.createdAt > :cutoff")
    long countRecentTokensForUser(@Param("user") User user, @Param("cutoff") OffsetDateTime cutoff);

    @Modifying
    @Query("UPDATE EmailVerificationToken t SET t.consumedAt = :now WHERE t.user = :user AND t.consumedAt IS NULL")
    void invalidateOutstandingTokensForUser(@Param("user") User user, @Param("now") OffsetDateTime now);
}
