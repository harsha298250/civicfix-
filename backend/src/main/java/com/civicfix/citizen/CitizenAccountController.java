package com.civicfix.citizen;

import com.civicfix.citizen.dto.CitizenDataExportResponse;
import com.civicfix.citizen.dto.DeleteAccountRequest;
import com.civicfix.common.ApiResponse;
import com.civicfix.security.UserPrincipal;
import com.civicfix.security.ratelimit.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
}
