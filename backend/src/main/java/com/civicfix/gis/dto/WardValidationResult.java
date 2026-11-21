package com.civicfix.gis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WardValidationResult {

    private String status; // "VALID", "MISMATCH", "OUTSIDE_BOUNDS", "OUTSIDE_GCC", "UNAVAILABLE"
    private String selectedWard;
    private String detectedWard;
    private String message;
    private String boundarySource;
    private Double distanceMeters;
    private Boolean insideState;
    private Boolean insideZone;
    private Boolean approximate = true;

    public WardValidationResult() {}

    public WardValidationResult(String status, String selectedWard, String detectedWard, String message, String boundarySource, Double distanceMeters, Boolean insideState, Boolean insideZone) {
        this.status = status;
        this.selectedWard = selectedWard;
        this.detectedWard = detectedWard;
        this.message = message;
        this.boundarySource = boundarySource;
        this.distanceMeters = distanceMeters;
        this.insideState = insideState;
        this.insideZone = insideZone;
        this.approximate = true;
    }

    public static WardValidationResult valid(String selectedWard, String detectedWard, String message, String boundarySource) {
        return new WardValidationResult("VALID", selectedWard, detectedWard, message, boundarySource, 0.0, true, true);
    }

    public static WardValidationResult mismatch(String selectedWard, String detectedWard, String message, String boundarySource, Double distanceMeters) {
        return new WardValidationResult("MISMATCH", selectedWard, detectedWard, message, boundarySource, distanceMeters, true, false);
    }

    public static WardValidationResult outsideBounds(String selectedWard, String message, String boundarySource, Double distanceMeters) {
        return new WardValidationResult("OUTSIDE_BOUNDS", selectedWard, null, message, boundarySource, distanceMeters, false, false);
    }

    public static WardValidationResult outsideGcc(String selectedWard, String message, String boundarySource, Double distanceMeters) {
        return new WardValidationResult("OUTSIDE_GCC", selectedWard, "Outside GCC (Tamil Nadu Non-Chennai Jurisdiction)", message, boundarySource, distanceMeters, true, false);
    }

    public static WardValidationResult unavailable(String message) {
        return new WardValidationResult("UNAVAILABLE", null, null, message, null, null, null, null);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSelectedWard() {
        return selectedWard;
    }

    public void setSelectedWard(String selectedWard) {
        this.selectedWard = selectedWard;
    }

    public String getDetectedWard() {
        return detectedWard;
    }

    public void setDetectedWard(String detectedWard) {
        this.detectedWard = detectedWard;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getBoundarySource() {
        return boundarySource;
    }

    public void setBoundarySource(String boundarySource) {
        this.boundarySource = boundarySource;
    }

    public Double getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(Double distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public Boolean getInsideState() {
        return insideState;
    }

    public void setInsideState(Boolean insideState) {
        this.insideState = insideState;
    }

    public Boolean getInsideZone() {
        return insideZone;
    }

    public void setInsideZone(Boolean insideZone) {
        this.insideZone = insideZone;
    }

    public Boolean getApproximate() {
        return approximate;
    }

    public void setApproximate(Boolean approximate) {
        this.approximate = approximate;
    }
}
