package com.civicfix.citizen;

import com.civicfix.auth.dto.AuthUserResponse;
import com.civicfix.citizen.dto.CitizenDataExportResponse;
import com.civicfix.citizen.dto.DeleteAccountRequest;
import com.civicfix.citizen.dto.UpdateCitizenProfileRequest;
import com.civicfix.common.ApiResponse;
import com.civicfix.security.UserPrincipal;
import com.civicfix.security.ratelimit.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/citizen/account")
public class CitizenAccountController {

    private final CitizenAccountService citizenAccountService;
    private final ClientIpResolver clientIpResolver;

    @Autowired
    public CitizenAccountController(CitizenAccountService citizenAccountService,
                                    @Autowired(required = false) ClientIpResolver clientIpResolver) {
        this.citizenAccountService = citizenAccountService;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('CITIZEN')")
    public ResponseEntity<ApiResponse<CitizenDataExportResponse>> exportAccountData(
            @AuthenticationPrincipal UserPrincipal principal) {
        CitizenDataExportResponse response = citizenAccountService.exportCitizenData(principal);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/delete")
    @PreAuthorize("hasRole('CITIZEN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteAccount(
            @Valid @RequestBody DeleteAccountRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        String ipAddress = clientIpResolver != null ? clientIpResolver.resolveClientIp(httpRequest) : httpRequest.getRemoteAddr();
        Map<String, Object> result = citizenAccountService.deleteCitizenAccount(request, principal, httpRequest, ipAddress);
        return ResponseEntity.ok(ApiResponse.success("Account deleted successfully.", result));
    }

    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AuthUserResponse>> updateProfile(
            @Valid @RequestBody UpdateCitizenProfileRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        String ipAddress = clientIpResolver != null ? clientIpResolver.resolveClientIp(httpRequest) : httpRequest.getRemoteAddr();
        AuthUserResponse updated = citizenAccountService.updateProfile(request, principal, ipAddress);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully.", updated));
    }

    @PostMapping(value = "/profile/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AuthUserResponse>> uploadProfilePhoto(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        String ipAddress = clientIpResolver != null ? clientIpResolver.resolveClientIp(httpRequest) : httpRequest.getRemoteAddr();
        AuthUserResponse updated = citizenAccountService.uploadProfilePhoto(file, principal, ipAddress);
        return ResponseEntity.ok(ApiResponse.success("Profile photo uploaded successfully.", updated));
    }

    @DeleteMapping("/profile/photo")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AuthUserResponse>> deleteProfilePhoto(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        String ipAddress = clientIpResolver != null ? clientIpResolver.resolveClientIp(httpRequest) : httpRequest.getRemoteAddr();
        AuthUserResponse updated = citizenAccountService.deleteProfilePhoto(principal, ipAddress);
        return ResponseEntity.ok(ApiResponse.success("Profile photo removed successfully.", updated));
    }

    @GetMapping("/profile/photo/{userId}/{filename:.+}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> getProfilePhoto(
            @PathVariable UUID userId,
            @PathVariable String filename,
            @AuthenticationPrincipal UserPrincipal principal) {
        Resource resource = citizenAccountService.loadProfilePhoto(userId, filename, principal);
        String lower = filename.toLowerCase();
        String ct = lower.endsWith(".png") ? "image/png" : lower.endsWith(".webp") ? "image/webp" : "image/jpeg";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(ct))
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }
}
