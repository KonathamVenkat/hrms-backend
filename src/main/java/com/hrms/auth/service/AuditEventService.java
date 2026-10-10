package com.hrms.auth.service;

import com.hrms.auth.dto.response.AuditEventResponse;
import com.hrms.common.dto.PagedResponse;

import java.time.LocalDate;

public interface AuditEventService {

    /** Newest first. Every filter is optional; {@code from} and {@code to} are inclusive days. */
    PagedResponse<AuditEventResponse> search(String actor, String action, String targetType, String targetId,
                                             LocalDate from, LocalDate to, int page, int size);
}
