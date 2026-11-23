package com.hrms.workflow.dto.response;

import com.hrms.workflow.enums.HistoryAction;

import java.time.LocalDateTime;

/**
 * One history row. {@code actorEmployeeId} / {@code actorRole} are null for system rows (AUTO_ROUTED);
 * {@code actorName} is the actor's full name (English), resolved from the employee module.
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
