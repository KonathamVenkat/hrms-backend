package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.ContractTypeRequest;
import com.hrms.employee.dto.response.ContractTypeResponse;
import com.hrms.employee.service.ContractTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Master-data maintenance for contract types. SYSTEM_ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/contract-types")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class ContractTypeAdminController {

    private final ContractTypeService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ContractTypeResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<ContractTypeResponse>>builder()
            .success(true).message("Contract type list fetched")
            .data(service.getAll()).statusCode(200).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ContractTypeResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<ContractTypeResponse>builder()
            .success(true).message("Contract type fetched")
            .data(service.getById(id)).statusCode(200).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ContractTypeResponse>> create(@Valid @RequestBody ContractTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<ContractTypeResponse>builder()
                .success(true).message("Contract type created successfully")
                .data(service.create(request)).statusCode(201).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ContractTypeResponse>> update(
            @PathVariable Long id, @Valid @RequestBody ContractTypeRequest request) {
        return ResponseEntity.ok(ApiResponse.<ContractTypeResponse>builder()
            .success(true).message("Contract type updated successfully")
            .data(service.update(id, request)).statusCode(200).build());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Contract type deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Contract type activated").statusCode(200).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Contract type deleted").statusCode(200).build());
    }
}
