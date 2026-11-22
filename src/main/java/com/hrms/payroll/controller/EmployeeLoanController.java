package com.hrms.payroll.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.common.dto.PagedResponse;
import com.hrms.employee.config.PageLimits;
import com.hrms.payroll.dto.request.EmployeeLoanRequest;
import com.hrms.payroll.dto.request.LoanStatusRequest;
import com.hrms.payroll.dto.response.EmployeeLoanResponse;
import com.hrms.payroll.dto.response.LoanTypeResponse;
import com.hrms.payroll.service.EmployeeLoanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** The loan register: loans carry pay amounts, so HR_ADMIN only, like salaries. */
@RestController
@RequestMapping("/api/v1/payroll/loans")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('HR_ADMIN')")
public class EmployeeLoanController {

    private final EmployeeLoanService service;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<EmployeeLoanResponse>>> search(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) Long componentId,
            @RequestParam(required = false) @Pattern(regexp = "ACTIVE|CLOSED|CANCELLED") String status,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Fixed order in the query (newest first); no client sort.
        return ResponseEntity.ok(ApiResponse.success("Success",
            service.search(q, componentId, status, employeeId, PageLimits.of(page, size))));
    }

    @GetMapping("/types")
    public ResponseEntity<ApiResponse<List<LoanTypeResponse>>> loanTypes() {
        return ResponseEntity.ok(ApiResponse.success("Success", service.loanTypes()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EmployeeLoanResponse>> create(@Valid @RequestBody EmployeeLoanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Loan recorded", service.create(request)));
    }

    @PatchMapping("/{loanId}/close")
    public ResponseEntity<ApiResponse<EmployeeLoanResponse>> close(
            @PathVariable Long loanId, @Valid @RequestBody LoanStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Loan closed", service.close(loanId, request)));
    }

    @PatchMapping("/{loanId}/cancel")
    public ResponseEntity<ApiResponse<EmployeeLoanResponse>> cancel(
            @PathVariable Long loanId, @Valid @RequestBody LoanStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Loan cancelled", service.cancel(loanId, request)));
    }
}
