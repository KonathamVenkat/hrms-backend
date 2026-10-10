package com.hrms.auth.dto.response;

import java.time.LocalDateTime;

/** One audit trail row as HR_ADMIN sees it. */
public record AuditEventResponse(
        Long id,
        LocalDateTime eventTime,
        String actor,
        String action,
        String targetType,
        String targetId,
        String detail) {}
