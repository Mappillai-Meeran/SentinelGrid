package com.sentragrid.manager.controller;

import com.sentragrid.common.ApiResponse;
import com.sentragrid.manager.dto.ManagerProfileDto;
import com.sentragrid.manager.dto.ManagerRulesDto;
import com.sentragrid.manager.dto.ManagerStaffDto;
import com.sentragrid.manager.dto.ManagerWorkspaceDto;
import com.sentragrid.manager.service.ManagerWorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/manager/workspace")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PHARMACY_MANAGER') or hasRole('ADMIN')")
public class ManagerWorkspaceController {

    private final ManagerWorkspaceService managerWorkspaceService;

    @GetMapping
    public ResponseEntity<ApiResponse<ManagerWorkspaceDto>> getWorkspace(Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success(managerWorkspaceService.getWorkspace(username)));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<ManagerProfileDto>> updateProfile(@RequestBody ManagerProfileDto request, Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success(managerWorkspaceService.updateProfile(request, username), "Profile saved"));
    }

    @PutMapping("/rules")
    public ResponseEntity<ApiResponse<ManagerRulesDto>> updateRules(@RequestBody ManagerRulesDto request, Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success(managerWorkspaceService.updateRules(request, username), "Rules saved"));
    }

    @GetMapping("/staff")
    public ResponseEntity<ApiResponse<List<ManagerStaffDto>>> getStaff(Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success(managerWorkspaceService.getStaff(username)));
    }

    @PostMapping("/staff")
    public ResponseEntity<ApiResponse<ManagerStaffDto>> saveStaff(@RequestBody ManagerStaffDto request, Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success(managerWorkspaceService.saveStaff(request, username), "Staff member saved"));
    }

    @PutMapping("/staff/{id}/status/{status}")
    public ResponseEntity<ApiResponse<ManagerStaffDto>> updateStaffStatus(
            @PathVariable Long id,
            @PathVariable String status,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(ApiResponse.success(managerWorkspaceService.updateStaffStatus(id, status, username), "Staff status updated"));
    }

    @DeleteMapping("/staff/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStaff(@PathVariable Long id, Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        managerWorkspaceService.deleteStaff(id, username);
        return ResponseEntity.ok(ApiResponse.success(null, "Staff member removed"));
    }
}
