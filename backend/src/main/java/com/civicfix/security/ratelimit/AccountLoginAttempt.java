package com.civicfix.security.ratelimit;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "account_login_attempts")
public class AccountLoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String identifier;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 1;

    @Column(name = "first_attempt_at", nullable = false)
    private OffsetDateTime firstAttemptAt = OffsetDateTime.now();

    @Column(name = "last_attempt_at", nullable = false)
    private OffsetDateTime lastAttemptAt = OffsetDateTime.now();

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @PrePersist
    protected void onCreate() {
        if (firstAttemptAt == null) firstAttemptAt = OffsetDateTime.now();
        if (lastAttemptAt == null) lastAttemptAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastAttemptAt = OffsetDateTime.now();
    }

    public AccountLoginAttempt() {}

    public AccountLoginAttempt(String identifier) {
        this.identifier = identifier;
        this.failedAttempts = 1;
        this.firstAttemptAt = OffsetDateTime.now();
        this.lastAttemptAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public OffsetDateTime getFirstAttemptAt() {
        return firstAttemptAt;
    }

    public void setFirstAttemptAt(OffsetDateTime firstAttemptAt) {
        this.firstAttemptAt = firstAttemptAt;
    }

    public OffsetDateTime getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(OffsetDateTime lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }

    public OffsetDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(OffsetDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }
}
