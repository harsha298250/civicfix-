package com.civicfix.auth.dto;

import java.time.OffsetDateTime;

public class VerifyActivationTokenResponse {

    private boolean valid;
    private String email;
    private String fullName;
    private String role;
    private String department;
    private OffsetDateTime expiresAt;

    public VerifyActivationTokenResponse() {}

    public VerifyActivationTokenResponse(boolean valid, String email, String fullName, String role, String department, OffsetDateTime expiresAt) {
        this.valid = valid;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.department = department;
        this.expiresAt = expiresAt;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
