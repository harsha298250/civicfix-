package com.civicfix.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ActivateStaffRequest {

    @NotBlank(message = "Activation token is required.")
    private String token;

    @Size(min = 8, max = 128, message = "Password must be at least 8 characters long.")
    private String newPassword;

    private String idToken;

    public ActivateStaffRequest() {}

    public ActivateStaffRequest(String token, String newPassword) {
        this.token = token;
        this.newPassword = newPassword;
    }

    public ActivateStaffRequest(String token, String newPassword, String idToken) {
        this.token = token;
        this.newPassword = newPassword;
        this.idToken = idToken;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public void setPassword(String password) {
        if (this.newPassword == null || this.newPassword.isBlank()) {
            this.newPassword = password;
        }
    }

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }
}
