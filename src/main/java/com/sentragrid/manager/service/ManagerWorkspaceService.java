package com.sentragrid.manager.service;

import com.sentragrid.dashboard.service.DashboardService;
import com.sentragrid.entity.Pharmacy;
import com.sentragrid.entity.User;
import com.sentragrid.entity.enums.ReservationStatus;
import com.sentragrid.exception.BadRequestException;
import com.sentragrid.exception.ResourceNotFoundException;
import com.sentragrid.manager.dto.ManagerProfileDto;
import com.sentragrid.manager.dto.ManagerReportDto;
import com.sentragrid.manager.dto.ManagerRulesDto;
import com.sentragrid.manager.dto.ManagerStaffDto;
import com.sentragrid.manager.dto.ManagerWorkspaceDto;
import com.sentragrid.manager.entity.ManagerSettings;
import com.sentragrid.manager.entity.ManagerStaff;
import com.sentragrid.manager.repository.ManagerSettingsRepository;
import com.sentragrid.manager.repository.ManagerStaffRepository;
import com.sentragrid.repository.InventoryRepository;
import com.sentragrid.repository.PharmacyRepository;
import com.sentragrid.repository.ReservationRepository;
import com.sentragrid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ManagerWorkspaceService {

    private final ManagerSettingsRepository settingsRepository;
    private final ManagerStaffRepository staffRepository;
    private final DashboardService dashboardService;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final InventoryRepository inventoryRepository;
    private final PharmacyRepository pharmacyRepository;

    @org.springframework.beans.factory.annotation.Value("${app.demo.protection-enabled:false}")
    private boolean demoProtectionEnabled;

    public Pharmacy resolveManagerPharmacy(String username) {
        if (username == null || username.isBlank()) {
            throw new AccessDeniedException("User authentication required");
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user not found: " + username));
        if (user.getPharmacy() == null) {
            throw new AccessDeniedException("Authenticated manager has no assigned pharmacy branch");
        }
        return user.getPharmacy();
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : null;
    }

    @Transactional
    public ManagerWorkspaceDto getWorkspace() {
        return getWorkspace(getCurrentUsername());
    }

    @Transactional
    public ManagerWorkspaceDto getWorkspace(String username) {
        Pharmacy pharmacy = resolveManagerPharmacy(username);
        ManagerSettings settings = ensureSettings(pharmacy);
        ensureDefaultStaff(pharmacy);

        return ManagerWorkspaceDto.builder()
                .profile(toProfile(settings))
                .rules(toRules(settings))
                .staff(getStaff(username))
                .reports(buildReports(pharmacy.getId(), settings.getLowStockThreshold()))
                .build();
    }

    @Transactional
    public ManagerProfileDto updateProfile(ManagerProfileDto request) {
        return updateProfile(request, getCurrentUsername());
    }

    @Transactional
    public ManagerProfileDto updateProfile(ManagerProfileDto request, String username) {
        Pharmacy pharmacy = resolveManagerPharmacy(username);
        ManagerSettings settings = ensureSettings(pharmacy);
        settings.setPharmacyName(request.getName());
        settings.setAddress(request.getAddress());
        settings.setCity(request.getCity());
        settings.setPhone(request.getPhone());
        settings.setHours(request.getHours());
        settings.setCoverage(request.getCoverage());
        settings.setVerificationStatus(request.getVerificationStatus());
        ManagerSettings savedSettings = settingsRepository.save(settings);

        // Synchronize with patient-facing Pharmacy entity to eliminate data mismatch
        if (request.getName() != null && !request.getName().isBlank()) {
            pharmacy.setName(request.getName().trim());
        }
        if (request.getAddress() != null && !request.getAddress().isBlank()) {
            pharmacy.setAddress(request.getAddress().trim());
        }
        if (request.getCity() != null && !request.getCity().isBlank()) {
            pharmacy.setCity(request.getCity().trim());
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            pharmacy.setContactNumber(request.getPhone().trim());
        }
        pharmacyRepository.save(pharmacy);

        return toProfile(savedSettings);
    }

    @Transactional
    public ManagerRulesDto updateRules(ManagerRulesDto request) {
        return updateRules(request, getCurrentUsername());
    }

    @Transactional
    public ManagerRulesDto updateRules(ManagerRulesDto request, String username) {
        Pharmacy pharmacy = resolveManagerPharmacy(username);
        ManagerSettings settings = ensureSettings(pharmacy);
        settings.setHoldExpiryMinutes(request.getHoldExpiryMinutes());
        settings.setLowStockThreshold(request.getLowStockThreshold());
        settings.setMaxEmergencyHoldQuantity(request.getMaxEmergencyHoldQuantity());
        return toRules(settingsRepository.save(settings));
    }

    @Transactional(readOnly = true)
    public List<ManagerStaffDto> getStaff() {
        return getStaff(getCurrentUsername());
    }

    @Transactional(readOnly = true)
    public List<ManagerStaffDto> getStaff(String username) {
        Pharmacy pharmacy = resolveManagerPharmacy(username);
        return staffRepository.findByPharmacyIdOrderByUsernameAsc(pharmacy.getId()).stream()
                .map(this::toStaffDto)
                .toList();
    }

    @Transactional
    public ManagerStaffDto saveStaff(ManagerStaffDto request) {
        return saveStaff(request, getCurrentUsername());
    }

    @Transactional
    public ManagerStaffDto saveStaff(ManagerStaffDto request, String username) {
        Pharmacy pharmacy = resolveManagerPharmacy(username);
        ManagerStaff staff;
        if (request.getId() == null) {
            staff = new ManagerStaff();
            staff.setPharmacy(pharmacy);
        } else {
            staff = staffRepository.findById(request.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Staff member not found with ID: " + request.getId()));
            if (staff.getPharmacy() == null || !staff.getPharmacy().getId().equals(pharmacy.getId())) {
                throw new AccessDeniedException("Access denied: Staff member does not belong to your pharmacy branch");
            }
        }

        staff.setUsername(request.getUsername());
        staff.setRole(request.getRole());
        staff.setShift(request.getShift());
        staff.setStatus(request.getStatus());
        staff.setReservationsHandled(request.getReservationsHandled() == null ? 0 : request.getReservationsHandled());
        return toStaffDto(staffRepository.save(staff));
    }

    @Transactional
    public ManagerStaffDto updateStaffStatus(Long id, String status) {
        return updateStaffStatus(id, status, getCurrentUsername());
    }

    @Transactional
    public ManagerStaffDto updateStaffStatus(Long id, String status, String username) {
        Pharmacy pharmacy = resolveManagerPharmacy(username);
        ManagerStaff staff = staffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found with ID: " + id));
        if (staff.getPharmacy() == null || !staff.getPharmacy().getId().equals(pharmacy.getId())) {
            throw new AccessDeniedException("Access denied: Staff member does not belong to your pharmacy branch");
        }
        staff.setStatus(status);
        return toStaffDto(staffRepository.save(staff));
    }

    @Transactional
    public void deleteStaff(Long id) {
        deleteStaff(id, getCurrentUsername());
    }

    @Transactional
    public void deleteStaff(Long id, String username) {
        if (demoProtectionEnabled) {
            throw new BadRequestException("Removing pharmacy staff is disabled in the public demonstration.");
        }
        Pharmacy pharmacy = resolveManagerPharmacy(username);
        ManagerStaff staff = staffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found with ID: " + id));
        if (staff.getPharmacy() == null || !staff.getPharmacy().getId().equals(pharmacy.getId())) {
            throw new AccessDeniedException("Access denied: Staff member does not belong to your pharmacy branch");
        }
        staffRepository.delete(staff);
    }

    private ManagerReportDto buildReports(Long pharmacyId, int lowStockThreshold) {
        List<ManagerReportDto.StockRiskDto> stockRisks;
        if (pharmacyId != null) {
            stockRisks = inventoryRepository.findLowStockInventoryByPharmacyId(pharmacyId, lowStockThreshold).stream()
                    .map(item -> ManagerReportDto.StockRiskDto.builder()
                            .medicine(item.getMedicine().getName())
                            .available(item.getQuantity() - item.getReservedQuantity())
                            .threshold(lowStockThreshold)
                            .build())
                    .toList();
        } else {
            stockRisks = dashboardService.getLowStockInventory(lowStockThreshold).stream()
                    .map(item -> ManagerReportDto.StockRiskDto.builder()
                            .medicine(item.getMedicineName())
                            .available(item.getAvailableQuantity())
                            .threshold(lowStockThreshold)
                            .build())
                    .toList();
        }

        return ManagerReportDto.builder()
                .stockRisks(stockRisks)
                .reservationOutcomes(List.of(
                        ManagerReportDto.ReservationOutcomeDto.builder().name("Verified").value((int) (pharmacyId != null ? reservationRepository.countByPharmacyIdAndStatus(pharmacyId, ReservationStatus.CONFIRMED) : reservationRepository.countByStatus(ReservationStatus.CONFIRMED))).color("#10b981").build(),
                        ManagerReportDto.ReservationOutcomeDto.builder().name("Pending").value((int) (pharmacyId != null ? reservationRepository.countByPharmacyIdAndStatus(pharmacyId, ReservationStatus.PENDING) : reservationRepository.countByStatus(ReservationStatus.PENDING))).color("#f59e0b").build(),
                        ManagerReportDto.ReservationOutcomeDto.builder().name("Expired").value((int) (pharmacyId != null ? reservationRepository.countByPharmacyIdAndStatus(pharmacyId, ReservationStatus.EXPIRED) : reservationRepository.countByStatus(ReservationStatus.EXPIRED))).color("#ef4444").build()
                ))
                .build();
    }

    private ManagerSettings ensureSettings(Pharmacy pharmacy) {
        Long pharmacyId = pharmacy.getId();
        return settingsRepository.findById(pharmacyId)
                .orElseGet(() -> settingsRepository.save(ManagerSettings.builder()
                        .id(pharmacyId)
                        .pharmacyName(pharmacy.getName() != null ? pharmacy.getName() : "Apollo Pharmacy")
                        .address(pharmacy.getAddress() != null ? pharmacy.getAddress() : "Main Branch")
                        .city(pharmacy.getCity() != null ? pharmacy.getCity() : "Puducherry")
                        .phone(pharmacy.getContactNumber() != null ? pharmacy.getContactNumber() : "0413-2341000")
                        .hours(pharmacy.is24Seven() ? "Open 24/7" : "09:00 AM - 11:00 PM")
                        .coverage(pharmacy.getName() + " service coverage area")
                        .verificationStatus("Verified Hub")
                        .holdExpiryMinutes(30)
                        .lowStockThreshold(8)
                        .maxEmergencyHoldQuantity(5)
                        .build()));
    }

    private void ensureDefaultStaff(Pharmacy pharmacy) {
        if (staffRepository.countByPharmacyId(pharmacy.getId()) > 0) {
            return;
        }

        if (pharmacy.getId().equals(1L)) {
            staffRepository.save(ManagerStaff.builder().pharmacy(pharmacy).username("pharmacist1").role("Lead Pharmacist").shift("Morning").status("Active").reservationsHandled(18).build());
            staffRepository.save(ManagerStaff.builder().pharmacy(pharmacy).username("pharmacist2").role("Dispensing Pharmacist").shift("Evening").status("Active").reservationsHandled(13).build());
            staffRepository.save(ManagerStaff.builder().pharmacy(pharmacy).username("pharmacist3").role("Inventory Support").shift("Backup").status("Inactive").reservationsHandled(4).build());
        } else {
            staffRepository.save(ManagerStaff.builder().pharmacy(pharmacy).username("staff_lead_" + pharmacy.getId()).role("Lead Pharmacist").shift("Morning").status("Active").reservationsHandled(0).build());
        }
    }

    private ManagerProfileDto toProfile(ManagerSettings settings) {
        return ManagerProfileDto.builder()
                .name(settings.getPharmacyName())
                .address(settings.getAddress())
                .city(settings.getCity())
                .phone(settings.getPhone())
                .hours(settings.getHours())
                .coverage(settings.getCoverage())
                .verificationStatus(settings.getVerificationStatus())
                .build();
    }

    private ManagerRulesDto toRules(ManagerSettings settings) {
        return ManagerRulesDto.builder()
                .holdExpiryMinutes(settings.getHoldExpiryMinutes())
                .lowStockThreshold(settings.getLowStockThreshold())
                .maxEmergencyHoldQuantity(settings.getMaxEmergencyHoldQuantity())
                .build();
    }

    private ManagerStaffDto toStaffDto(ManagerStaff staff) {
        return ManagerStaffDto.builder()
                .id(staff.getId())
                .pharmacyId(staff.getPharmacy() != null ? staff.getPharmacy().getId() : null)
                .username(staff.getUsername())
                .role(staff.getRole())
                .shift(staff.getShift())
                .status(staff.getStatus())
                .reservationsHandled(staff.getReservationsHandled())
                .build();
    }
}
