package com.civicfix.auth.google;

public class GoogleTokenClaims {

    private final String subject;
    private final String email;
    private final boolean emailVerified;
    private final String name;
    private final String pictureUrl;

    public GoogleTokenClaims(String subject, String email, boolean emailVerified, String name, String pictureUrl) {
        this.subject = subject;
        this.email = email;
        this.emailVerified = emailVerified;
        this.name = name;
        this.pictureUrl = pictureUrl;
    }

    public String getSubject() {
        return subject;
    }

    public String getEmail() {
        return email;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public String getName() {
        return name;
    }

    public String getPictureUrl() {
        return pictureUrl;
    }
}
