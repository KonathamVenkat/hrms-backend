package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.DocumentTypeRequest;
import com.hrms.employee.dto.response.DocumentTypeResponse;
import com.hrms.employee.service.DocumentTypeService;
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
@RequestMapping("/api/v1/admin/document-types")
@RequiredArgsConstructor
public class DocumentTypeController {

    private final DocumentTypeService documentTypeService;

    @GetMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<List<DocumentTypeResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<DocumentTypeResponse>>builder()
            .success(true).message("Document types fetched")
            .data(documentTypeService.getAllDocumentTypes()).statusCode(200).build());
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<DocumentTypeResponse>>> getActive() {
        return ResponseEntity.ok(ApiResponse.<List<DocumentTypeResponse>>builder()
            .success(true).message("Active document types fetched")
            .data(documentTypeService.getActiveDocumentTypes()).statusCode(200).build());
    }

    @GetMapping("/mandatory")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<DocumentTypeResponse>>> getMandatory() {
        return ResponseEntity.ok(ApiResponse.<List<DocumentTypeResponse>>builder()
            .success(true).message("Mandatory document types fetched")
            .data(documentTypeService.getMandatoryDocumentTypes()).statusCode(200).build());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<DocumentTypeResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<DocumentTypeResponse>builder()
            .success(true).message("Document type fetched")
            .data(documentTypeService.getDocumentTypeById(id)).statusCode(200).build());
    }

    @PostMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<DocumentTypeResponse>> create(
            @Valid @RequestBody DocumentTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<DocumentTypeResponse>builder()
                .success(true).message("Document type created successfully")
                .data(documentTypeService.createDocumentType(request)).statusCode(201).build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<DocumentTypeResponse>> update(
            @PathVariable Long id, @Valid @RequestBody DocumentTypeRequest request) {
        return ResponseEntity.ok(ApiResponse.<DocumentTypeResponse>builder()
            .success(true).message("Document type updated successfully")
            .data(documentTypeService.updateDocumentType(id, request)).statusCode(200).build());
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        documentTypeService.deactivateDocumentType(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Document type deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        documentTypeService.activateDocumentType(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Document type activated").statusCode(200).build());
    }
}
