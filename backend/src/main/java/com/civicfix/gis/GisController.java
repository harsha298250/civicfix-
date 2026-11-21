package com.civicfix.gis;

import com.civicfix.common.ApiResponse;
import com.civicfix.gis.dto.ReverseGeocodeResponse;
import com.civicfix.gis.dto.ValidateLocationRequest;
import com.civicfix.gis.dto.WardValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gis")
public class GisController {

    private final GisService gisService;

    public GisController(GisService gisService) {
        this.gisService = gisService;
    }

    @GetMapping("/reverse-geocode")
    public ResponseEntity<ApiResponse<ReverseGeocodeResponse>> reverseGeocode(
            @RequestParam Double lat,
            @RequestParam Double lng) {
        ReverseGeocodeResponse response = gisService.reverseGeocode(lat, lng);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/boundaries/state")
    public ResponseEntity<ApiResponse<JsonNode>> getStateBoundary() {
        JsonNode boundary = gisService.getStateBoundaryGeoJson();
        if (boundary == null) {
            return ResponseEntity.status(503).body(ApiResponse.error("Tamil Nadu state boundary data is currently unavailable."));
        }
        return ResponseEntity.ok(ApiResponse.success(boundary));
    }

    @GetMapping("/boundaries/zones")
    public ResponseEntity<ApiResponse<JsonNode>> getZonesBoundary() {
        JsonNode boundary = gisService.getZonesBoundaryGeoJson();
        if (boundary == null) {
            return ResponseEntity.status(503).body(ApiResponse.error("Chennai GCC zones boundary data is currently unavailable."));
        }
        return ResponseEntity.ok(ApiResponse.success(boundary));
    }

    @PostMapping("/validate-location")
    public ResponseEntity<ApiResponse<WardValidationResult>> validateLocation(@RequestBody ValidateLocationRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Validation request payload is required."));
        }
        WardValidationResult result = gisService.validateWardContainment(request.getLat(), request.getLng(), request.getWard());
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
