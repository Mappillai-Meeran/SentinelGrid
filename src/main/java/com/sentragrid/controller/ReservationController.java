package com.sentragrid.controller;

import com.sentragrid.common.ApiResponse;
import com.sentragrid.dto.ReservationRequestDto;
import com.sentragrid.dto.ReservationResponseDto;
import com.sentragrid.entity.User;
import com.sentragrid.repository.UserRepository;
import com.sentragrid.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservations", description = "Medicine Reservation Management API")
public class ReservationController {

    private final ReservationService reservationService;
    private final UserRepository userRepository;

    @PostMapping
    @PreAuthorize("hasRole('PATIENT') or hasRole('ADMIN')")
    @Operation(summary = "Create a medicine reservation with optimistic locking protection")
    public ResponseEntity<ApiResponse<ReservationResponseDto>> createReservation(
            @Valid @RequestBody ReservationRequestDto request,
            Authentication authentication) {
        User patient = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

        ReservationResponseDto response = reservationService.createReservation(patient.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Reservation created successfully. Stock reserved."));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasRole('PHARMACIST') or hasRole('ADMIN')")
    @Operation(summary = "Confirm pickup of reserved medicine")
    public ResponseEntity<ApiResponse<ReservationResponseDto>> confirmPickup(
            @PathVariable Long id,
            Authentication authentication) {
        ReservationResponseDto response = reservationService.confirmPickup(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(response, "Reservation pickup confirmed successfully"));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('PATIENT') or hasRole('PHARMACIST') or hasRole('ADMIN')")
    @Operation(summary = "Cancel an active reservation and release stock")
    public ResponseEntity<ApiResponse<ReservationResponseDto>> cancelReservation(
            @PathVariable Long id,
            Authentication authentication) {
        ReservationResponseDto response = reservationService.cancelReservation(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(response, "Reservation cancelled successfully"));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('PATIENT') or hasRole('ADMIN')")
    @Operation(summary = "Get reservations for current authenticated patient")
    public ResponseEntity<ApiResponse<List<ReservationResponseDto>>> getMyReservations(Authentication authentication) {
        User patient = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

        List<ReservationResponseDto> reservations = reservationService.getPatientReservations(patient.getId());
        return ResponseEntity.ok(ApiResponse.success(reservations, "Patient reservations retrieved"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get reservation by ID")
    public ResponseEntity<ApiResponse<ReservationResponseDto>> getReservationById(@PathVariable Long id) {
        ReservationResponseDto response = reservationService.getReservationById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
