package com.civicfix.security.ratelimit;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ip_login_attempts")
public class IpLoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "ip_address", nullable = false, unique = true, length = 100)
    private String ipAddress;

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

    public IpLoginAttempt() {}

    public IpLoginAttempt(String ipAddress) {
        this.ipAddress = ipAddress;
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

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
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
