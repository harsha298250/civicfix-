package com.civicfix.gis.dto;

import java.math.BigDecimal;

public class ValidateLocationRequest {

    private BigDecimal lat;
    private BigDecimal lng;
    private String ward;

    public ValidateLocationRequest() {}

    public ValidateLocationRequest(BigDecimal lat, BigDecimal lng, String ward) {
        this.lat = lat;
        this.lng = lng;
        this.ward = ward;
    }

    public BigDecimal getLat() {
        return lat;
    }

    public void setLat(BigDecimal lat) {
        this.lat = lat;
    }

    public BigDecimal getLng() {
        return lng;
    }

    public void setLng(BigDecimal lng) {
        this.lng = lng;
    }

    public String getWard() {
        return ward;
    }

    public void setWard(String ward) {
        this.ward = ward;
    }
}
