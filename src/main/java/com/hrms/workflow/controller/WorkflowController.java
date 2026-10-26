package com.hrms.workflow.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.response.DepartmentLookupResponse;
import com.hrms.workflow.dto.request.WorkflowActionRequest;
import com.hrms.workflow.dto.request.WorkflowOverrideRequest;
import com.hrms.workflow.dto.response.RequestHistoryResponse;
import com.hrms.workflow.dto.response.WorkflowStatusResponse;
import com.hrms.workflow.enums.RequestType;
import com.hrms.workflow.service.WorkflowDefinitionService;
import com.hrms.workflow.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Thin. The role gate here is coarse; who may see or act on a given request is decided by the service
 * (participant check on reads, live step holder on actions, requester on cancel, HR_ADMIN on override).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WorkflowController {

    private static final String PARTICIPANT_ROLES = "hasAnyRole('HR_ADMIN','HR_MANAGER','GENERAL_MANAGER','EMPLOYEE')";

    private final WorkflowService workflowService;
    private final WorkflowDefinitionService definitionService;

    @GetMapping("/requests/{type}/{requestId}/workflow")
    @PreAuthorize(PARTICIPANT_ROLES)
    public ResponseEntity<ApiResponse<WorkflowStatusResponse>> status(@PathVariable RequestType type,
                                                                      @PathVariable long requestId) {
        return ok("Workflow status fetched", workflowService.status(type, requestId));
    }

    @GetMapping("/requests/{type}/{requestId}/workflow/target-departments")
    @PreAuthorize(PARTICIPANT_ROLES)
    public ResponseEntity<ApiResponse<List<DepartmentLookupResponse>>> targetDepartments(
            @PathVariable RequestType type, @PathVariable long requestId) {
        return ok("Departments fetched", workflowService.targetDepartments(type, requestId));
    }

    @PostMapping("/requests/{type}/{requestId}/actions")
    @PreAuthorize(PARTICIPANT_ROLES)
    public ResponseEntity<ApiResponse<WorkflowStatusResponse>> act(@PathVariable RequestType type,
            @PathVariable long requestId, @Valid @RequestBody WorkflowActionRequest request) {
        return ok("Action recorded", workflowService.act(type, requestId, request));
    }

    @PostMapping("/requests/{type}/{requestId}/cancel")
    @PreAuthorize(PARTICIPANT_ROLES)
    public ResponseEntity<ApiResponse<WorkflowStatusResponse>> cancel(@PathVariable RequestType type,
                                                                      @PathVariable long requestId) {
        return ok("Request cancelled", workflowService.cancel(type, requestId));
    }

    @PostMapping("/requests/{type}/{requestId}/override")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<WorkflowStatusResponse>> override(@PathVariable RequestType type,
            @PathVariable long requestId, @Valid @RequestBody WorkflowOverrideRequest request) {
        return ok("Override recorded", workflowService.override(type, requestId, request));
    }

    @GetMapping("/requests/{type}/{requestId}/history")
    @PreAuthorize(PARTICIPANT_ROLES)
    public ResponseEntity<ApiResponse<List<RequestHistoryResponse>>> history(@PathVariable RequestType type,
                                                                            @PathVariable long requestId) {
        return ok("History fetched", workflowService.history(type, requestId));
    }

    /** HR_ADMIN, and SYSTEM_ADMIN only while the SYSTEM_ADMIN_CAN_MANAGE_WORKFLOWS setting is true. */
    @PostMapping("/admin/workflows/{type}/publish-default")
    @PreAuthorize("hasRole('HR_ADMIN') or (hasRole('SYSTEM_ADMIN') and @workflowAdminPolicy.systemAdminMayManage())")
    public ResponseEntity<ApiResponse<Void>> publishDefault(@PathVariable RequestType type) {
        definitionService.publishDefault(type);
        return ok("Default workflow published", null);
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder().success(true).message(message).data(data)
            .statusCode(200).build());
    }
}
