package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.EmployeeDocumentRequest;
import com.hrms.employee.dto.response.EmployeeDocumentResponse;
import com.hrms.employee.service.EmployeeDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/employees/{employeeId}/documents")
@RequiredArgsConstructor
public class EmployeeDocumentController {

    private final EmployeeDocumentService documentService;

    /**
     * GET /api/v1/employees/{employeeId}/documents
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<EmployeeDocumentResponse>>> getDocuments(
            @PathVariable Long employeeId) {
        return ResponseEntity.ok(
            ApiResponse.<List<EmployeeDocumentResponse>>builder()
                .success(true).message("Documents fetched successfully")
                .data(documentService.getDocuments(employeeId))
                .statusCode(200).build());
    }

    /**
     * GET /api/v1/employees/{employeeId}/documents/{documentId}
     */
    @GetMapping("/{documentId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<EmployeeDocumentResponse>> getDocumentById(
            @PathVariable Long employeeId,
            @PathVariable Long documentId) {
        return ResponseEntity.ok(
            ApiResponse.<EmployeeDocumentResponse>builder()
                .success(true).message("Document fetched")
                .data(documentService.getDocumentById(employeeId, documentId))
                .statusCode(200).build());
    }

    /**
     * POST /api/v1/employees/{employeeId}/documents
     * Multipart upload: file + JSON metadata
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<EmployeeDocumentResponse>> uploadDocument(
            @PathVariable Long employeeId,
            @RequestPart("metadata") @Valid EmployeeDocumentRequest request,
            @RequestPart("file") MultipartFile file) {

        log.info("POST document upload for employee {}", employeeId);
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.<EmployeeDocumentResponse>builder()
                .success(true).message("Document uploaded successfully")
                .data(documentService.uploadDocument(employeeId, request, file))
                .statusCode(201).build());
    }

    /**
     * PUT /api/v1/employees/{employeeId}/documents/{documentId}
     * Update metadata only (no file replacement)
     */
    @PutMapping("/{documentId}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<EmployeeDocumentResponse>> updateDocumentInfo(
            @PathVariable Long employeeId,
            @PathVariable Long documentId,
            @Valid @RequestBody EmployeeDocumentRequest request) {
        return ResponseEntity.ok(
            ApiResponse.<EmployeeDocumentResponse>builder()
                .success(true).message("Document updated successfully")
                .data(documentService.updateDocumentInfo(employeeId, documentId, request))
                .statusCode(200).build());
    }

    /**
     * PATCH /api/v1/employees/{employeeId}/documents/{documentId}/verify
     */
    @PatchMapping("/{documentId}/verify")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<EmployeeDocumentResponse>> verifyDocument(
            @PathVariable Long employeeId,
            @PathVariable Long documentId,
            Authentication authentication) {
        // The principal type isn't guaranteed to be UserDetails, so use the authentication's
        // name (the username) — otherwise the audit trail silently falls back to "SYSTEM".
        String verifiedBy = authentication != null ? authentication.getName() : "SYSTEM";
        return ResponseEntity.ok(
            ApiResponse.<EmployeeDocumentResponse>builder()
                .success(true).message("Document verified")
                .data(documentService.verifyDocument(employeeId, documentId, verifiedBy))
                .statusCode(200).build());
    }

    /**
     * DELETE /api/v1/employees/{employeeId}/documents/{documentId}
     */
    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            @PathVariable Long employeeId,
            @PathVariable Long documentId) {
        documentService.deleteDocument(employeeId, documentId);
        return ResponseEntity.ok(
            ApiResponse.<Void>builder()
                .success(true).message("Document deleted")
                .statusCode(200).build());
    }

    /**
     * GET /api/v1/employees/{employeeId}/documents/{documentId}/download
     */
    @GetMapping("/{documentId}/download")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable Long employeeId,
            @PathVariable Long documentId) {

        Resource resource = documentService.downloadDocument(employeeId, documentId);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                    .filename(resource.getFilename(), StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(resource);
    }
}