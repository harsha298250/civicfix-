package com.civicfix.supervisor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ExtendSlaRequest {

    @NotNull(message = "Additional hours is required.")
    @Min(value = 1, message = "Additional hours must be at least 1.")
    @Max(value = 720, message = "Additional hours cannot exceed 720 (30 days).")
    private Integer additionalHours;

    @NotBlank(message = "Reason for extension is required.")
    @Size(min = 5, max = 500, message = "Reason must be between 5 and 500 characters.")
    private String reason;

    private Long version;

    public ExtendSlaRequest() {}

    public ExtendSlaRequest(Integer additionalHours, String reason) {
        this.additionalHours = additionalHours;
        this.reason = reason;
    }

    public Integer getAdditionalHours() {
        return additionalHours;
    }

    public void setAdditionalHours(Integer additionalHours) {
        this.additionalHours = additionalHours;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
