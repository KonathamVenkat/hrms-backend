package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.HouseTypeRequest;
import com.hrms.employee.dto.response.HouseTypeResponse;
import com.hrms.employee.service.HouseTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Master-data maintenance for house types. SYSTEM_ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/house-types")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class HouseTypeAdminController {

    private final HouseTypeService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<HouseTypeResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<HouseTypeResponse>>builder()
            .success(true).message("House type list fetched")
            .data(service.getAll()).statusCode(200).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<HouseTypeResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<HouseTypeResponse>builder()
            .success(true).message("House type fetched")
            .data(service.getById(id)).statusCode(200).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<HouseTypeResponse>> create(@Valid @RequestBody HouseTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<HouseTypeResponse>builder()
                .success(true).message("House type created successfully")
                .data(service.create(request)).statusCode(201).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<HouseTypeResponse>> update(
            @PathVariable Long id, @Valid @RequestBody HouseTypeRequest request) {
        return ResponseEntity.ok(ApiResponse.<HouseTypeResponse>builder()
            .success(true).message("House type updated successfully")
            .data(service.update(id, request)).statusCode(200).build());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("House type deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("House type activated").statusCode(200).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("House type deleted").statusCode(200).build());
    }
}
