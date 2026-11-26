package com.civicfix.citizen.dto;

import jakarta.validation.constraints.Size;

public class UpdateCitizenProfileRequest {

    @Size(min = 2, max = 150, message = "Name must be between 2 and 150 characters")
    private String name;

    @Size(max = 25, message = "Phone number cannot exceed 25 characters")
    private String phone;

    @Size(max = 120, message = "District cannot exceed 120 characters")
    private String district;

    @Size(max = 150, message = "Zone cannot exceed 150 characters")
    private String zone;

    @Size(max = 150, message = "Ward cannot exceed 150 characters")
    private String ward;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
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

    public String getWard() {
        return ward;
    }

    public void setWard(String ward) {
        this.ward = ward;
    }
}
