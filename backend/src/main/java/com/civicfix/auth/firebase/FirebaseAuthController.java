package com.civicfix.auth.firebase;

import com.civicfix.auth.dto.AuthUserResponse;
import com.civicfix.auth.dto.FirebaseSessionRequest;
import com.civicfix.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/firebase")
public class FirebaseAuthController {

    private final FirebaseAuthService firebaseAuthService;

    public FirebaseAuthController(FirebaseAuthService firebaseAuthService) {
        this.firebaseAuthService = firebaseAuthService;
    }

    @PostMapping("/session")
    public ResponseEntity<ApiResponse<AuthUserResponse>> createSession(
            @Valid @RequestBody FirebaseSessionRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AuthUserResponse response = firebaseAuthService.authenticateFirebaseUser(request, httpRequest, httpResponse);
        return ResponseEntity.ok(ApiResponse.success("Authentication successful.", response));
    }
}
