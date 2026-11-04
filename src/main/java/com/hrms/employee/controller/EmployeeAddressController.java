package com.hrms.employee.controller;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.EmployeeAddressRequest;
import com.hrms.employee.dto.response.EmployeeAddressResponse;
import com.hrms.employee.service.EmployeeAddressService;
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
@RequestMapping("/api/v1/employees/{employeeId}/addresses")
@RequiredArgsConstructor
public class EmployeeAddressController {

    private final EmployeeAccessGuard accessGuard;
    private final EmployeeAddressService addressService;

    /**
     * GET /api/v1/employees/{employeeId}/addresses
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<EmployeeAddressResponse>>> getAddresses(
            @PathVariable Long employeeId) {
        accessGuard.assertSelfOrPrivileged(employeeId);
        return ResponseEntity.ok(ApiResponse.<List<EmployeeAddressResponse>>builder()
            .success(true).message("Addresses fetched successfully")
            .data(addressService.getAddresses(employeeId)).statusCode(200).build());
    }

    /**
     * GET /api/v1/employees/{employeeId}/addresses/{addressId}
     */
    @GetMapping("/{addressId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<EmployeeAddressResponse>> getAddressById(
            @PathVariable Long employeeId,
            @PathVariable Long addressId) {
        accessGuard.assertSelfOrPrivileged(employeeId);
        return ResponseEntity.ok(ApiResponse.<EmployeeAddressResponse>builder()
            .success(true).message("Address fetched")
            .data(addressService.getAddressById(employeeId, addressId)).statusCode(200).build());
    }

    /**
     * POST /api/v1/employees/{employeeId}/addresses
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<EmployeeAddressResponse>> addAddress(
            @PathVariable Long employeeId,
            @Valid @RequestBody EmployeeAddressRequest request) {
        accessGuard.assertSelfOrAdmin(employeeId);
        log.info("POST address for employee {}", employeeId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<EmployeeAddressResponse>builder()
                .success(true).message("Address added successfully")
                .data(addressService.addAddress(employeeId, request)).statusCode(201).build());
    }

    /**
     * PUT /api/v1/employees/{employeeId}/addresses/{addressId}
     */
    @PutMapping("/{addressId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<EmployeeAddressResponse>> updateAddress(
            @PathVariable Long employeeId,
            @PathVariable Long addressId,
            @Valid @RequestBody EmployeeAddressRequest request) {
        accessGuard.assertSelfOrAdmin(employeeId);
        return ResponseEntity.ok(ApiResponse.<EmployeeAddressResponse>builder()
            .success(true).message("Address updated successfully")
            .data(addressService.updateAddress(employeeId, addressId, request)).statusCode(200).build());
    }

    /**
     * PATCH /api/v1/employees/{employeeId}/addresses/{addressId}/set-primary
     */
    @PatchMapping("/{addressId}/set-primary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> setPrimary(
            @PathVariable Long employeeId,
            @PathVariable Long addressId) {
        accessGuard.assertSelfOrAdmin(employeeId);
        addressService.setPrimary(employeeId, addressId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Primary address updated").statusCode(200).build());
    }

    /**
     * DELETE /api/v1/employees/{employeeId}/addresses/{addressId}
     */
    @DeleteMapping("/{addressId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deactivateAddress(
            @PathVariable Long employeeId,
            @PathVariable Long addressId) {
        accessGuard.assertSelfOrAdmin(employeeId);
        addressService.deactivateAddress(employeeId, addressId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Address removed").statusCode(200).build());
    }
}
