package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.BankRequest;
import com.hrms.employee.dto.response.BankResponse;
import com.hrms.employee.service.BankService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Master-data maintenance for banks. SYSTEM_ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/banks")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class BankAdminController {

    private final BankService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BankResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<BankResponse>>builder()
            .success(true).message("Bank list fetched")
            .data(service.getAll()).statusCode(200).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BankResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<BankResponse>builder()
            .success(true).message("Bank fetched")
            .data(service.getById(id)).statusCode(200).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BankResponse>> create(@Valid @RequestBody BankRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<BankResponse>builder()
                .success(true).message("Bank created successfully")
                .data(service.create(request)).statusCode(201).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BankResponse>> update(
            @PathVariable Long id, @Valid @RequestBody BankRequest request) {
        return ResponseEntity.ok(ApiResponse.<BankResponse>builder()
            .success(true).message("Bank updated successfully")
            .data(service.update(id, request)).statusCode(200).build());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Bank deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Bank activated").statusCode(200).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Bank deleted").statusCode(200).build());
    }
}
