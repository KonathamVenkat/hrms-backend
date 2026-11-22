package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.response.GradeScaleStepResponse;
import com.hrms.employee.service.GradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The pay scale (grade, increment, starting scale) for choosing an employee's grade on job details. HR_ADMIN only:
 * the amounts are salary data, which is HR_ADMIN-only (policy of 2026-11-05). Lives in the employee module because
 * grades do; payroll may read it through GradeService.
 */
@RestController
@RequestMapping("/api/v1/payroll/grade-scale")
@RequiredArgsConstructor
@PreAuthorize("hasRole('HR_ADMIN')")
public class GradeScaleController {

    private final GradeService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<GradeScaleStepResponse>>> getPayScale() {
        return ResponseEntity.ok(ApiResponse.<List<GradeScaleStepResponse>>builder()
            .success(true).message("Pay scale fetched")
            .data(service.getPayScale()).statusCode(200).build());
    }
}
