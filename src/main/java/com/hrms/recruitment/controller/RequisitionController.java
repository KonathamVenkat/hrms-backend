package com.hrms.recruitment.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.common.dto.PagedResponse;
import com.hrms.recruitment.dto.request.ApproveRequisitionRequest;
import com.hrms.recruitment.dto.request.RejectRequisitionRequest;
import com.hrms.recruitment.dto.request.RequisitionActionRequest;
import com.hrms.recruitment.dto.request.RequisitionRequest;
import com.hrms.recruitment.dto.response.PersonOptionResponse;
import com.hrms.recruitment.dto.response.RaiseOptionsResponse;
import com.hrms.recruitment.dto.response.RequisitionResponse;
import com.hrms.recruitment.dto.response.RequisitionSummaryResponse;
import com.hrms.recruitment.enums.RequisitionStatus;
import com.hrms.recruitment.service.RequisitionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Job requisitions. The role check here is only the first gate: the service decides per record (own requisition,
 * head of the department, HR) and refuses the rest. SYSTEM_ADMIN has no access.
 */
@RestController
@RequestMapping("/api/v1/recruitment/requisitions")
@RequiredArgsConstructor
@Validated
public class RequisitionController {

    private static final String ANY_REQUESTER = "hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')";
    private static final String HR_ONLY = "hasAnyRole('HR_ADMIN','HR_MANAGER')";

    private final RequisitionService service;

    @GetMapping
    @PreAuthorize(ANY_REQUESTER)
    public ResponseEntity<ApiResponse<PagedResponse<RequisitionSummaryResponse>>> search(
            @RequestParam(required = false) RequisitionStatus status,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        return ResponseEntity.ok(ApiResponse.success("Requisitions fetched",
            service.search(status, departmentId, q, page, size, sortBy, direction)));
    }

    @GetMapping("/raise-options")
    @PreAuthorize(ANY_REQUESTER)
    public ResponseEntity<ApiResponse<RaiseOptionsResponse>> raiseOptions() {
        return ResponseEntity.ok(ApiResponse.success("Options fetched", service.raiseOptions()));
    }

    @GetMapping("/raise-options/people")
    @PreAuthorize(ANY_REQUESTER)
    public ResponseEntity<ApiResponse<List<PersonOptionResponse>>> people(@RequestParam Long departmentId) {
        return ResponseEntity.ok(ApiResponse.success("People fetched", service.peopleOf(departmentId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(ANY_REQUESTER)
    public ResponseEntity<ApiResponse<RequisitionResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Requisition fetched", service.get(id)));
    }

    @PostMapping
    @PreAuthorize(ANY_REQUESTER)
    public ResponseEntity<ApiResponse<RequisitionResponse>> create(@Valid @RequestBody RequisitionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created("Requisition created", service.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize(ANY_REQUESTER)
    public ResponseEntity<ApiResponse<RequisitionResponse>> update(
            @PathVariable Long id, @Valid @RequestBody RequisitionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Requisition updated", service.update(id, request)));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize(ANY_REQUESTER)
    public ResponseEntity<ApiResponse<RequisitionResponse>> submit(
            @PathVariable Long id, @Valid @RequestBody RequisitionActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Requisition submitted for approval", service.submit(id, request)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(ANY_REQUESTER)
    public ResponseEntity<ApiResponse<RequisitionResponse>> cancel(
            @PathVariable Long id, @Valid @RequestBody RequisitionActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Requisition cancelled", service.cancel(id, request)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(HR_ONLY)
    public ResponseEntity<ApiResponse<RequisitionResponse>> approve(
            @PathVariable Long id, @Valid @RequestBody ApproveRequisitionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Requisition approved", service.approve(id, request)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(HR_ONLY)
    public ResponseEntity<ApiResponse<RequisitionResponse>> reject(
            @PathVariable Long id, @Valid @RequestBody RejectRequisitionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Requisition rejected", service.reject(id, request)));
    }

    @PostMapping("/{id}/hold")
    @PreAuthorize(HR_ONLY)
    public ResponseEntity<ApiResponse<RequisitionResponse>> hold(
            @PathVariable Long id, @Valid @RequestBody RequisitionActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Requisition put on hold", service.hold(id, request)));
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize(HR_ONLY)
    public ResponseEntity<ApiResponse<RequisitionResponse>> resume(
            @PathVariable Long id, @Valid @RequestBody RequisitionActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Requisition resumed", service.resume(id, request)));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize(HR_ONLY)
    public ResponseEntity<ApiResponse<RequisitionResponse>> close(
            @PathVariable Long id, @Valid @RequestBody RequisitionActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Requisition closed", service.close(id, request)));
    }
}
