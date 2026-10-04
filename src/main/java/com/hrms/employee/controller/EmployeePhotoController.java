package com.hrms.employee.controller;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.response.EmployeeResponse;
import com.hrms.employee.service.EmployeePhotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Profile photo upload / download. Allowed for HR_ADMIN, HR_MANAGER and the employee themself
 * (not for other employees). The image is served from here, not from a public URL, so the
 * browser has to ask with its session.
 */
@RestController
@RequestMapping("/api/v1/employees/{employeeId}/photo")
@RequiredArgsConstructor
@Tag(name = "Employee Photo", description = "Upload and serve employee profile photos")
public class EmployeePhotoController {

    private final EmployeePhotoService photoService;
    private final EmployeeAccessGuard  accessGuard;

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    @Operation(summary = "Upload the employee's profile photo (JPG, PNG or WebP, max 2 MB)")
    public ResponseEntity<ApiResponse<EmployeeResponse>> upload(
            @PathVariable Long employeeId,
            @RequestPart("file") MultipartFile file) {

        accessGuard.assertSelfOrPrivileged(employeeId);
        return ResponseEntity.ok(
            ApiResponse.success("Profile photo updated", photoService.upload(employeeId, file)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    @Operation(summary = "Download the employee's profile photo")
    public ResponseEntity<byte[]> get(@PathVariable Long employeeId) {
        accessGuard.assertSelfOrPrivileged(employeeId);
        EmployeePhotoService.Photo photo = photoService.get(employeeId);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(photo.contentType()))
            .cacheControl(CacheControl.noCache().cachePrivate())
            .header("X-Content-Type-Options", "nosniff")
            .body(photo.content());
    }

    @DeleteMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    @Operation(summary = "Remove the employee's profile photo")
    public ResponseEntity<ApiResponse<EmployeeResponse>> remove(@PathVariable Long employeeId) {
        accessGuard.assertSelfOrPrivileged(employeeId);
        return ResponseEntity.ok(
            ApiResponse.success("Profile photo removed", photoService.remove(employeeId)));
    }
}
