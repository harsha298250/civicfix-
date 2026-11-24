package com.civicfix.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ActivateStaffRequest {

    @NotBlank(message = "Activation token is required.")
    private String token;

    @NotBlank(message = "Permanent password is required.")
    @Size(min = 8, max = 128, message = "Password must be at least 8 characters long.")
    private String newPassword;

    public ActivateStaffRequest() {}

    public ActivateStaffRequest(String token, String newPassword) {
        this.token = token;
        this.newPassword = newPassword;
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
}
