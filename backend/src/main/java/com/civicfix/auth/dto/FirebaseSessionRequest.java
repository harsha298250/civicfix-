package com.civicfix.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class FirebaseSessionRequest {

    @NotBlank(message = "Firebase ID token is required.")
    private String idToken;

    private String name;
    private String ward;
    private String phone;

    public FirebaseSessionRequest() {}

    public FirebaseSessionRequest(String idToken) {
        this.idToken = idToken;
    }

    public FirebaseSessionRequest(String idToken, String name, String ward, String phone) {
        this.idToken = idToken;
        this.name = name;
        this.ward = ward;
        this.phone = phone;
    }

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWard() {
        return ward;
    }

    public void setWard(String ward) {
        this.ward = ward;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
