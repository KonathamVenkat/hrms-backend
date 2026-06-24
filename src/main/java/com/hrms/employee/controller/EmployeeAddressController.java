package com.hrms.employee.controller;

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

    private final EmployeeAddressService addressService;

    /**
     * GET /api/v1/employees/{employeeId}/addresses
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<EmployeeAddressResponse>>> getAddresses(
            @PathVariable Long employeeId) {
        return ResponseEntity.ok(ApiResponse.<List<EmployeeAddressResponse>>builder()
            .success(true).message("Addresses fetched successfully")
            .data(addressService.getAddresses(employeeId)).statusCode(200).build());
    }

    /**
     * GET /api/v1/employees/{employeeId}/addresses/{addressId}
     */
    @GetMapping("/{addressId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<EmployeeAddressResponse>> getAddressById(
            @PathVariable Long employeeId,
            @PathVariable Long addressId) {
        return ResponseEntity.ok(ApiResponse.<EmployeeAddressResponse>builder()
            .success(true).message("Address fetched")
            .data(addressService.getAddressById(employeeId, addressId)).statusCode(200).build());
    }

    /**
     * POST /api/v1/employees/{employeeId}/addresses
     */
    @PostMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<EmployeeAddressResponse>> addAddress(
            @PathVariable Long employeeId,
            @Valid @RequestBody EmployeeAddressRequest request) {
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
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<EmployeeAddressResponse>> updateAddress(
            @PathVariable Long employeeId,
            @PathVariable Long addressId,
            @Valid @RequestBody EmployeeAddressRequest request) {
        return ResponseEntity.ok(ApiResponse.<EmployeeAddressResponse>builder()
            .success(true).message("Address updated successfully")
            .data(addressService.updateAddress(employeeId, addressId, request)).statusCode(200).build());
    }

    /**
     * PATCH /api/v1/employees/{employeeId}/addresses/{addressId}/set-primary
     */
    @PatchMapping("/{addressId}/set-primary")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> setPrimary(
            @PathVariable Long employeeId,
            @PathVariable Long addressId) {
        addressService.setPrimary(employeeId, addressId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Primary address updated").statusCode(200).build());
    }

    /**
     * DELETE /api/v1/employees/{employeeId}/addresses/{addressId}
     */
    @DeleteMapping("/{addressId}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateAddress(
            @PathVariable Long employeeId,
            @PathVariable Long addressId) {
        addressService.deactivateAddress(employeeId, addressId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Address removed").statusCode(200).build());
    }
}
