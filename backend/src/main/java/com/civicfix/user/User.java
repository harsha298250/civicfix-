package com.civicfix.user;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "full_name", length = 150, nullable = false)
    private String fullName;

    @Column(length = 255, unique = true)
    private String email;

    @Column(length = 50, unique = true)
    private String phone;

    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    @Column(name = "firebase_uid", length = 128, unique = true)
    private String firebaseUid;

    @Column(name = "google_id", length = 128, unique = true)
    private String googleId;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "account_status", length = 50, nullable = false)
    private String accountStatus = "ACTIVE";

    @Column(length = 150)
    private String department;

    @Column(length = 150)
    private String ward;

    @Column(length = 120)
    private String district;

    @Column(length = 150)
    private String zone;

    @Column(name = "profile_photo_object_key", length = 500)
    private String profilePhotoObjectKey;

    @Column(name = "profile_photo_url", length = 1000)
    private String profilePhotoUrl;

    @Column(name = "trust_score")
    private Integer trustScore = 90;

    @Column(name = "cross_ward_access", nullable = false)
    private boolean crossWardAccess = false;

    @Column(name = "cross_ward_expires_at")
    private OffsetDateTime crossWardExpiresAt;

    @Column(name = "cross_department_access", nullable = false)
    private boolean crossDepartmentAccess = false;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public User() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFirebaseUid() {
        return firebaseUid;
    }

    public void setFirebaseUid(String firebaseUid) {
        this.firebaseUid = firebaseUid;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getWard() {
        return ward;
    }

    public void setWard(String ward) {
        this.ward = ward;
    }

    public Integer getTrustScore() {
        return trustScore;
    }

    public void setTrustScore(Integer trustScore) {
        this.trustScore = trustScore;
    }

    public boolean isCrossWardAccess() {
        return crossWardAccess;
    }

    public void setCrossWardAccess(boolean crossWardAccess) {
        this.crossWardAccess = crossWardAccess;
    }

    public OffsetDateTime getCrossWardExpiresAt() {
        return crossWardExpiresAt;
    }

    public void setCrossWardExpiresAt(OffsetDateTime crossWardExpiresAt) {
        this.crossWardExpiresAt = crossWardExpiresAt;
    }

    public boolean isCrossDepartmentAccess() {
        return crossDepartmentAccess;
    }

    public void setCrossDepartmentAccess(boolean crossDepartmentAccess) {
        this.crossDepartmentAccess = crossDepartmentAccess;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getGoogleId() {
        return googleId;
    }

    public void setGoogleId(String googleId) {
        this.googleId = googleId;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public boolean hasRole(RoleName roleName) {
        return roles.stream().anyMatch(r -> r.getName() == roleName);
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public String getProfilePhotoObjectKey() {
        return profilePhotoObjectKey;
    }

    public void setProfilePhotoObjectKey(String profilePhotoObjectKey) {
        this.profilePhotoObjectKey = profilePhotoObjectKey;
    }

    public String getProfilePhotoUrl() {
        return profilePhotoUrl;
    }

    public void setProfilePhotoUrl(String profilePhotoUrl) {
        this.profilePhotoUrl = profilePhotoUrl;
    }
}
