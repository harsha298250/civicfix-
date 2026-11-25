package com.civicfix.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class GoogleAuthRequest {

    @NotBlank(message = "Google ID token is required")
    private String idToken;

    private Boolean rememberMe = false;

    public GoogleAuthRequest() {}

    public GoogleAuthRequest(String idToken, Boolean rememberMe) {
        this.idToken = idToken;
        this.rememberMe = rememberMe != null && rememberMe;
    }

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }

    public Boolean getRememberMe() {
        return rememberMe != null && rememberMe;
    }

    public void setRememberMe(Boolean rememberMe) {
        this.rememberMe = rememberMe != null && rememberMe;
    }
}
