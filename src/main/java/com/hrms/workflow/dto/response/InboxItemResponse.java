package com.hrms.workflow.dto.response;

import com.hrms.workflow.enums.RequestType;

import java.time.LocalDateTime;

/** One request waiting for the caller. Labels are the waiting step's, from the flow version the request entered. */
public record InboxItemResponse(
    RequestType type,
    long requestId,
    String stepKey,
    String stepLabelEn,
    String stepLabelAr,
    long requesterEmployeeId,
    String requesterName,
    String summary,
    LocalDateTime submittedAt) {
}
