package com.hrms.payroll.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.common.dto.PagedResponse;
import com.hrms.payroll.dto.request.EmployeeSalaryRequest;
import com.hrms.payroll.dto.response.EmployeeSalaryResponse;
import com.hrms.payroll.service.EmployeeSalaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payroll/employee-salary")
@RequiredArgsConstructor
@Slf4j
public class EmployeeSalaryController {

    private final EmployeeSalaryService service;

    // ── Assign / Revise salary ────────────────────────────
    @PostMapping("/assign")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<EmployeeSalaryResponse>> assign(
            @Valid @RequestBody EmployeeSalaryRequest request) {
        log.info("POST /employee-salary/assign — employeeId={}", request.employeeId());
        return ResponseEntity.ok(
                ApiResponse.success("Salary assigned successfully",
                        service.assign(request)));
    }

    // ── Current salary for one employee ──────────────────
    @GetMapping("/{employeeId}/current")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<EmployeeSalaryResponse>> getCurrent(
            @PathVariable Long employeeId) {
        log.info("GET /employee-salary/{}/current", employeeId);
        return ResponseEntity.ok(
                ApiResponse.success("Success", service.getCurrentSalary(employeeId)));
    }

    // ── Salary history for one employee ──────────────────
    @GetMapping("/{employeeId}/history")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<EmployeeSalaryResponse>>> getHistory(
            @PathVariable Long employeeId) {
        return ResponseEntity.ok(
                ApiResponse.success("Success", service.getSalaryHistory(employeeId)));
    }

    // ── Paginated list of all current salaries ────────────
    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<PagedResponse<EmployeeSalaryResponse>>> getAll(
            @RequestParam(required = false) Long structureId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /employee-salary — structureId={}, page={}", structureId, page);
        return ResponseEntity.ok(
                ApiResponse.success("Success",
                        service.getAllCurrent(structureId,
                                PageRequest.of(page, size,
                                        Sort.by("employeeId")))));
    }
}
