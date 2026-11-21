package com.civicfix.supervisor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class DeEscalateRequest {

    @NotBlank(message = "Explanation for de-escalation is required.")
    @Size(min = 5, max = 500, message = "Explanation must be between 5 and 500 characters.")
    private String reason;

    private Integer additionalHours;

    private Long version;

    public DeEscalateRequest() {}

    public DeEscalateRequest(String reason) {
        this.reason = reason;
    }

    public DeEscalateRequest(String reason, Integer additionalHours) {
        this.reason = reason;
        this.additionalHours = additionalHours;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Integer getAdditionalHours() {
        return additionalHours;
    }

    public void setAdditionalHours(Integer additionalHours) {
        this.additionalHours = additionalHours;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
