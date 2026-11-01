package com.sentragrid.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentragrid.ai.dto.SearchNormalizationRequest;
import com.sentragrid.ai.dto.SearchNormalizationResponse;
import com.sentragrid.audit.service.AuditLogService;
import com.sentragrid.auth.dto.AuthResponse;
import com.sentragrid.auth.dto.RegisterRequest;
import com.sentragrid.auth.service.AuthService;
import com.sentragrid.entity.User;
import com.sentragrid.entity.enums.UserRole;
import com.sentragrid.repository.UserRepository;
import com.sentragrid.security.JwtTokenProvider;
import com.sentragrid.security.RateLimiterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthSecurityAndContractTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private RateLimiterService rateLimiterService;

    @InjectMocks
    private AuthService authService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Public registration must strictly enforce PATIENT role and prevent ADMIN role escalation")
    void registrationMustEnforcePatientRole() {
        // Given an attacker attempting to register with ADMIN role
        RegisterRequest request = RegisterRequest.builder()
                .username("attacker")
                .email("attacker@test.com")
                .password("secret123")
                .role(UserRole.ADMIN)
                .build();

        when(userRepository.existsByUsername("attacker")).thenReturn(false);
        when(userRepository.existsByEmail("attacker@test.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("encodedPassword");
        when(tokenProvider.generateTokenForUser(any())).thenReturn("mock-token");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(userCaptor.capture())).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(99L);
            return u;
        });

        // When
        AuthResponse response = authService.register(request);

        // Then: The user saved to database must be PATIENT, NOT ADMIN
        User savedUser = userCaptor.getValue();
        assertEquals(UserRole.PATIENT, savedUser.getRole(), "Public registration must never allow self-assignment of ADMIN role");
        assertEquals(UserRole.PATIENT, response.getRole(), "Auth response role must be PATIENT");
    }

    @Test
    @DisplayName("AI Search request DTO must accept both 'prompt' and 'query' JSON payloads")
    void aiSearchRequestMustAcceptQueryAlias() throws Exception {
        String jsonPayloadWithQuery = "{\"query\":\"snake bite injection\",\"city\":\"Puducherry\"}";
        SearchNormalizationRequest req = objectMapper.readValue(jsonPayloadWithQuery, SearchNormalizationRequest.class);

        assertEquals("snake bite injection", req.getPrompt(), "Jackson must bind 'query' to prompt field");
        assertEquals("Puducherry", req.getCity());
    }

    @Test
    @DisplayName("AI Search response DTO must expose both 'normalizedMedicine' and 'normalizedMedicineName'")
    void aiSearchResponseMustExposeNormalizedMedicineName() throws Exception {
        SearchNormalizationResponse resp = SearchNormalizationResponse.builder()
                .normalizedMedicine("Anti-Venom Polyvalent")
                .category("EMERGENCY_ANTIVENOM")
                .explanation("Mapped to antivenom")
                .build();

        String json = objectMapper.writeValueAsString(resp);
        assertTrue(json.contains("\"normalizedMedicine\":\"Anti-Venom Polyvalent\""));
        assertTrue(json.contains("\"normalizedMedicineName\":\"Anti-Venom Polyvalent\""), 
                "JSON must include normalizedMedicineName for frontend compatibility");
    }
}
