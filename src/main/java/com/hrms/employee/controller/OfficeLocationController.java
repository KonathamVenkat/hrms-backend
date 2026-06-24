package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.OfficeLocationRequest;
import com.hrms.employee.dto.response.OfficeLocationResponse;
import com.hrms.employee.service.OfficeLocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/office-locations")
@RequiredArgsConstructor
public class OfficeLocationController {

    private final OfficeLocationService locationService;

    @GetMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<List<OfficeLocationResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<OfficeLocationResponse>>builder()
            .success(true).message("Locations fetched")
            .data(locationService.getAllLocations()).statusCode(200).build());
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<OfficeLocationResponse>>> getActive() {
        return ResponseEntity.ok(ApiResponse.<List<OfficeLocationResponse>>builder()
            .success(true).message("Active locations fetched")
            .data(locationService.getActiveLocations()).statusCode(200).build());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<OfficeLocationResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<OfficeLocationResponse>builder()
            .success(true).message("Location fetched")
            .data(locationService.getLocationById(id)).statusCode(200).build());
    }

    @PostMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<OfficeLocationResponse>> create(
            @Valid @RequestBody OfficeLocationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<OfficeLocationResponse>builder()
                .success(true).message("Location created successfully")
                .data(locationService.createLocation(request)).statusCode(201).build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<OfficeLocationResponse>> update(
            @PathVariable Long id, @Valid @RequestBody OfficeLocationRequest request) {
        return ResponseEntity.ok(ApiResponse.<OfficeLocationResponse>builder()
            .success(true).message("Location updated successfully")
            .data(locationService.updateLocation(id, request)).statusCode(200).build());
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        locationService.deactivateLocation(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Location deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        locationService.activateLocation(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Location activated").statusCode(200).build());
    }
}
