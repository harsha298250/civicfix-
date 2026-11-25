package com.civicfix.auth;

import com.civicfix.auth.dto.AuthUserResponse;
import com.civicfix.auth.dto.ForgotPasswordRequest;
import com.civicfix.auth.dto.GoogleAuthRequest;
import com.civicfix.auth.dto.LoginRequest;
import com.civicfix.auth.dto.RegisterCitizenRequest;
import com.civicfix.auth.dto.ResendVerificationRequest;
import com.civicfix.auth.dto.ResetPasswordRequest;
import com.civicfix.auth.dto.VerifyActivationTokenResponse;
import com.civicfix.auth.dto.VerifyEmailRequest;
import com.civicfix.auth.dto.ActivateStaffRequest;
import com.civicfix.common.ApiResponse;
import com.civicfix.security.UserPrincipal;
import com.civicfix.security.ratelimit.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final StaffActivationService staffActivationService;
    private final ClientIpResolver clientIpResolver;

    public AuthController(AuthService authService, ClientIpResolver clientIpResolver) {
        this(authService, null, clientIpResolver);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AuthController(AuthService authService,
                          StaffActivationService staffActivationService,
                          ClientIpResolver clientIpResolver) {
        this.authService = authService;
        this.staffActivationService = staffActivationService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthUserResponse>> register(
            @Valid @RequestBody RegisterCitizenRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AuthUserResponse response = authService.registerCitizen(request, httpRequest, httpResponse);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Citizen account registered successfully.", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthUserResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AuthUserResponse response = authService.login(request, httpRequest, httpResponse);
        return ResponseEntity.ok(ApiResponse.success("Authentication successful.", response));
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthUserResponse>> loginWithGoogle(
            @Valid @RequestBody GoogleAuthRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AuthUserResponse response = authService.loginWithGoogle(request, httpRequest, httpResponse);
        return ResponseEntity.ok(ApiResponse.success("Google authentication successful.", response));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolveClientIp(httpRequest);
        String message = authService.verifyEmail(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success(message, null));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolveClientIp(httpRequest);
        String message = authService.resendVerification(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success(message, null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthUserResponse>> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal principal) {
        AuthUserResponse response = authService.getCurrentUser(principal);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            @AuthenticationPrincipal UserPrincipal principal) {
        authService.logout(httpRequest, httpResponse, principal);
        return ResponseEntity.ok(ApiResponse.success("Signed out successfully.", null));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolveClientIp(httpRequest);
        String message = authService.requestPasswordReset(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success(message, null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        String message = authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(message, null));
    }

    @GetMapping("/staff-activation/verify")
    public ResponseEntity<ApiResponse<VerifyActivationTokenResponse>> verifyStaffActivation(
            @RequestParam("token") String token) {
        VerifyActivationTokenResponse response = staffActivationService.verifyToken(token);
        return ResponseEntity.ok(ApiResponse.success("Activation token verified successfully.", response));
    }

    @PostMapping("/staff-activation/verify")
    public ResponseEntity<ApiResponse<VerifyActivationTokenResponse>> verifyStaffActivationPost(
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String token = (body != null) ? body.get("token") : null;
        VerifyActivationTokenResponse response = staffActivationService.verifyToken(token);
        return ResponseEntity.ok(ApiResponse.success("Activation token verified successfully.", response));
    }

    @PostMapping("/staff-activation/activate")
    public ResponseEntity<ApiResponse<Void>> activateStaff(
            @Valid @RequestBody ActivateStaffRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolveClientIp(httpRequest);
        staffActivationService.activateStaff(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success("Staff account activated successfully. Please log in with your new password.", null));
    }
}
