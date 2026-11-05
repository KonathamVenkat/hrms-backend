package com.hrms.workflow.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.common.dto.PagedResponse;
import com.hrms.workflow.dto.response.InboxItemResponse;
import com.hrms.workflow.service.InboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Requests waiting for the caller. The role gate is coarse; the query is keyed on the caller's own identity. */
@RestController
@RequestMapping("/api/v1/inbox")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','GENERAL_MANAGER','CEO','EMPLOYEE')")
public class InboxController {

    private final InboxService inboxService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<InboxItemResponse>>> inbox(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.<PagedResponse<InboxItemResponse>>builder()
            .success(true).message("Inbox fetched").data(inboxService.inbox(page, size)).statusCode(200).build());
    }
}
