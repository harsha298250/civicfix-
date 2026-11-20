package com.civicfix.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RestoreComplaintRequest {

    @NotBlank(message = "A valid restoration reason is required.")
    @Size(min = 5, max = 500, message = "Restoration reason must be between 5 and 500 characters.")
    private String reason;

    public RestoreComplaintRequest() {}

    public RestoreComplaintRequest(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
