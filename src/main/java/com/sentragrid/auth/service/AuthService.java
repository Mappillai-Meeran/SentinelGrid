package com.sentragrid.auth.service;

import com.sentragrid.audit.service.AuditLogService;
import com.sentragrid.auth.dto.AuthResponse;
import com.sentragrid.auth.dto.LoginRequest;
import com.sentragrid.auth.dto.RegisterRequest;
import com.sentragrid.entity.User;
import com.sentragrid.entity.enums.UserRole;
import com.sentragrid.exception.BadRequestException;
import com.sentragrid.repository.UserRepository;
import com.sentragrid.security.JwtTokenProvider;
import com.sentragrid.security.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    private final AuditLogService auditLogService;
    private final RateLimiterService rateLimiterService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        return register(request, "127.0.0.1");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, String clientIp) {
        rateLimiterService.checkRegistrationAllowed(clientIp);

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already registered");
        }

        // Enforce PATIENT role for all public self-registrations to prevent privilege escalation
        UserRole assignedRole = UserRole.PATIENT;

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(assignedRole)
                .build();

        User savedUser = userRepository.save(user);
        rateLimiterService.recordRegistration(clientIp);

        auditLogService.logAction("User", savedUser.getId(), "USER_REGISTERED", savedUser.getUsername(),
                "New user registered with role " + savedUser.getRole());

        String token = tokenProvider.generateTokenForUser(savedUser.getUsername());

        Long pharmacyId = savedUser.getPharmacy() != null ? savedUser.getPharmacy().getId() : null;
        String pharmacyName = savedUser.getPharmacy() != null ? savedUser.getPharmacy().getName() : null;

        return AuthResponse.builder()
                .token(token)
                .userId(savedUser.getId())
                .username(savedUser.getUsername())
                .email(savedUser.getEmail())
                .role(savedUser.getRole())
                .pharmacyId(pharmacyId)
                .pharmacyName(pharmacyName)
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        return login(request, "127.0.0.1");
    }

    public AuthResponse login(LoginRequest request, String clientIp) {
        rateLimiterService.checkLoginAllowed(request.getUsername(), clientIp);

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (org.springframework.security.core.AuthenticationException ex) {
            rateLimiterService.recordLoginFailure(request.getUsername(), clientIp);
            throw ex;
        }

        // Authentication succeeded: clear failed counter
        rateLimiterService.recordLoginSuccess(request.getUsername(), clientIp);

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadRequestException("Invalid credentials"));

        String token = tokenProvider.generateToken(authentication);
        auditLogService.logAction("User", user.getId(), "USER_LOGIN", user.getUsername(), "Successful login");

        Long pharmacyId = user.getPharmacy() != null ? user.getPharmacy().getId() : null;
        String pharmacyName = user.getPharmacy() != null ? user.getPharmacy().getName() : null;

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .pharmacyId(pharmacyId)
                .pharmacyName(pharmacyName)
                .build();
    }
}
