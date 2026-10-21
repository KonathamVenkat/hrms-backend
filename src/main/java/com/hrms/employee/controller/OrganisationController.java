package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.common.dto.PagedResponse;
import com.hrms.employee.dto.request.BranchAssignmentRequest;
import com.hrms.employee.dto.request.BranchPlacementRequest;
import com.hrms.employee.dto.response.BranchAssignmentResult;
import com.hrms.employee.dto.response.BranchLookupResponse;
import com.hrms.employee.dto.response.BranchPlacementResponse;
import com.hrms.employee.dto.response.EmployeeBranchGapResponse;
import com.hrms.employee.dto.response.OrganisationSummaryResponse;
import com.hrms.employee.service.OrganisationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Organisation set-up: where each branch sits and who manages it, which employees still have no branch, and the
 * back-fill that gives them one. HR_ADMIN and HR_MANAGER only; SYSTEM_ADMIN keeps branch code, name and company in
 * the admin masters.
 */
@RestController
@RequestMapping("/api/v1/organisation")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
public class OrganisationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final OrganisationService service;

    @GetMapping("/branches")
    public ResponseEntity<ApiResponse<List<BranchPlacementResponse>>> branches() {
        return ok("Branches fetched", service.listBranches());
    }

    @GetMapping("/branches/lookup")
    public ResponseEntity<ApiResponse<List<BranchLookupResponse>>> lookup(
            @RequestParam(required = false) Long locationId) {
        return ok("Branch lookup fetched", service.lookupBranches(locationId));
    }

    @PutMapping("/branches/{id}/placement")
    public ResponseEntity<ApiResponse<BranchPlacementResponse>> setPlacement(
            @PathVariable Long id, @Valid @RequestBody BranchPlacementRequest request) {
        return ok("Branch placement updated", service.setPlacement(id, request));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<OrganisationSummaryResponse>> summary() {
        return ok("Organisation summary fetched", service.summary());
    }

    @GetMapping("/employees-without-branch")
    public ResponseEntity<ApiResponse<PagedResponse<EmployeeBranchGapResponse>>> employeesWithoutBranch(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return ok("Employees without a branch fetched",
            PagedResponse.from(service.employeesWithoutBranch(q, pageable)));
    }

    @GetMapping("/manager-candidates")
    public ResponseEntity<ApiResponse<PagedResponse<EmployeeBranchGapResponse>>> managerCandidates(
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return ok("Manager candidates fetched",
            PagedResponse.from(service.managerCandidates(locationId, q, pageable)));
    }

    @PutMapping("/branch-assignments")
    public ResponseEntity<ApiResponse<BranchAssignmentResult>> assignBranches(
            @Valid @RequestBody BranchAssignmentRequest request) {
        return ok("Branch assignments processed", service.assignBranches(request));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
            .success(true).message(message).data(data).statusCode(200).build());
    }
}
