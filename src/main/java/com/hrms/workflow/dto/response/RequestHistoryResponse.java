package com.hrms.workflow.dto.response;

import com.hrms.workflow.enums.HistoryAction;

import java.time.LocalDateTime;

/**
 * One history row. {@code actorEmployeeId} / {@code actorRole} are null for system rows (AUTO_ROUTED);
 * {@code actorName} is not filled by the workflow module (it has no access to employee names).
 */
public record RequestHistoryResponse(
    String stepKey,
    HistoryAction action,
    Long actorEmployeeId,
    String actorName,
    String actorRole,
    String comment,
    LocalDateTime actedAt) {
}
