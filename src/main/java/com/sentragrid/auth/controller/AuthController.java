package com.sentragrid.auth.controller;

import com.sentragrid.auth.dto.AuthResponse;
import com.sentragrid.auth.dto.LoginRequest;
import com.sentragrid.auth.dto.RegisterRequest;
import com.sentragrid.auth.service.AuthService;
import com.sentragrid.common.ApiResponse;
import com.sentragrid.security.ClientIpResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User Registration and Login Management")
public class AuthController {

    private final AuthService authService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/register")
    @Operation(summary = "Register a new user (PATIENT, PHARMACIST, PHARMACY_MANAGER, ADMIN)")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.extractClientIp(httpRequest);
        AuthResponse response = authService.register(request, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "User registered successfully"));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and receive JWT token")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.extractClientIp(httpRequest);
        AuthResponse response = authService.login(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    /**
     * Backward-compatible delegation methods for existing unit tests.
     */
    public synchronized void configureTrustedProxies(String proxiesConfig) {
        clientIpResolver.configureTrustedProxies(proxiesConfig);
    }

    public String extractClientIp(HttpServletRequest request) {
        return clientIpResolver.extractClientIp(request);
    }

    public boolean isTrustedProxy(String ip) {
        return clientIpResolver.isTrustedProxy(ip);
    }

    public boolean isValidIpAddress(String ip) {
        return clientIpResolver.isValidIpAddress(ip);
    }
}
