package com.hrms.auth.controller;

import com.hrms.auth.dto.response.AuditEventResponse;
import com.hrms.auth.service.AuditEventService;
import com.hrms.common.dto.ApiResponse;
import com.hrms.common.dto.PagedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/audit-events")
@RequiredArgsConstructor
public class AuditEventController {

    private final AuditEventService auditEvents;

    /**
     * GET /api/v1/admin/audit-events?actor=&action=&targetType=&targetId=&from=&to=&page=&size=
     * HR_ADMIN reads the audit trail, newest first. Every filter is optional.
     */
    @GetMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<AuditEventResponse>>> search(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.success("Audit events",
                auditEvents.search(actor, action, targetType, targetId, from, to, page, size)));
    }
}
