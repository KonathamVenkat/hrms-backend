package com.hrms.employee.controller;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.EmployeeBankAccountRequest;
import com.hrms.employee.dto.response.EmployeeBankAccountResponse;
import com.hrms.employee.service.EmployeeBankAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Bank accounts of one employee. Read: HR staff, or the employee for their own record (HR_MANAGER and the
 * employee see what the service allows: HR_MANAGER gets masked numbers). Write: HR_ADMIN only.
 */
@RestController
@RequestMapping("/api/v1/employees/{employeeId}/bank-accounts")
@RequiredArgsConstructor
public class EmployeeBankAccountController {

    private final EmployeeBankAccountService service;
    private final EmployeeAccessGuard accessGuard;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<EmployeeBankAccountResponse>>> getAccounts(@PathVariable Long employeeId) {
        accessGuard.assertSelfOrPrivileged(employeeId);
        return ResponseEntity.ok(ApiResponse.<List<EmployeeBankAccountResponse>>builder()
            .success(true).message("Bank accounts fetched successfully")
            .data(service.getAccounts(employeeId)).statusCode(200).build());
    }

    @PostMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<EmployeeBankAccountResponse>> addAccount(
            @PathVariable Long employeeId, @Valid @RequestBody EmployeeBankAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<EmployeeBankAccountResponse>builder()
            .success(true).message("Bank account added successfully")
            .data(service.addAccount(employeeId, request)).statusCode(201).build());
    }

    @PutMapping("/{accountId}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<EmployeeBankAccountResponse>> updateAccount(
            @PathVariable Long employeeId, @PathVariable Long accountId,
            @Valid @RequestBody EmployeeBankAccountRequest request) {
        return ResponseEntity.ok(ApiResponse.<EmployeeBankAccountResponse>builder()
            .success(true).message("Bank account updated successfully")
            .data(service.updateAccount(employeeId, accountId, request)).statusCode(200).build());
    }

    @PatchMapping("/{accountId}/set-primary")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> setPrimary(@PathVariable Long employeeId, @PathVariable Long accountId) {
        service.setPrimary(employeeId, accountId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Primary bank account updated").statusCode(200).build());
    }

    /** Deactivates; an account is never deleted. */
    @DeleteMapping("/{accountId}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateAccount(
            @PathVariable Long employeeId, @PathVariable Long accountId) {
        service.deactivateAccount(employeeId, accountId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Bank account deactivated").statusCode(200).build());
    }
}
