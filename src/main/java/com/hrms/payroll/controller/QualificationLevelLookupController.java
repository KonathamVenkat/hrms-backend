package com.hrms.payroll.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.payroll.dto.response.QualificationLevelResponse;
import com.hrms.payroll.service.QualificationLevelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Active qualification levels for the Assign Salary dropdown. They carry pay amounts: HR_ADMIN only, like salaries. */
@RestController
@RequestMapping("/api/v1/payroll/qualification-levels")
@RequiredArgsConstructor
public class QualificationLevelLookupController {

    private final QualificationLevelService service;

    @GetMapping("/lookup")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<List<QualificationLevelResponse>>> lookup() {
        return ResponseEntity.ok(ApiResponse.success("Success", service.getActive()));
    }
}
