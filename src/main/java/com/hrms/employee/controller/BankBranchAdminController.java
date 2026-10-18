package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.BankBranchRequest;
import com.hrms.employee.dto.response.BankBranchResponse;
import com.hrms.employee.service.BankBranchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Master-data maintenance for bank branchs. SYSTEM_ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/bank-branches")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class BankBranchAdminController {

    private final BankBranchService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BankBranchResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<BankBranchResponse>>builder()
            .success(true).message("Bank branch list fetched")
            .data(service.getAll()).statusCode(200).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BankBranchResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<BankBranchResponse>builder()
            .success(true).message("Bank branch fetched")
            .data(service.getById(id)).statusCode(200).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BankBranchResponse>> create(@Valid @RequestBody BankBranchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<BankBranchResponse>builder()
                .success(true).message("Bank branch created successfully")
                .data(service.create(request)).statusCode(201).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BankBranchResponse>> update(
            @PathVariable Long id, @Valid @RequestBody BankBranchRequest request) {
        return ResponseEntity.ok(ApiResponse.<BankBranchResponse>builder()
            .success(true).message("Bank branch updated successfully")
            .data(service.update(id, request)).statusCode(200).build());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Bank branch deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Bank branch activated").statusCode(200).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Bank branch deleted").statusCode(200).build());
    }
}
