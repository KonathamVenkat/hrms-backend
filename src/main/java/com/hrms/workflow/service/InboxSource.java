package com.hrms.workflow.service;

import com.hrms.workflow.enums.RequestType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/**
 * A request module's waiting requests for one approver, so the workflow module never imports request repositories
 * (like {@link WorkflowRequestAccess}). Matches the stored current approver: the person, or one of the role names
 * (without the ROLE_ prefix); never the approver's own requests; oldest first.
 */
public interface InboxSource {

    RequestType type();

    Page<Row> waitingFor(long employeeId, Collection<String> roles, Pageable pageable);

    /** {@code definitionId} is the flow version the request entered (for the step labels). */
    /**
     * {@code summary} is a plain English line (fallback); {@code details} the same facts, language-neutral, for the
     * screen to word in the user's language (keys per request type, e.g. leave: leaveTypeCode, leaveTypeNameEn,
     * leaveTypeNameAr, startDate, endDate, totalDays).
     */
    record Row(long requestId, Long definitionId, String stepKey, long requesterEmployeeId, String requesterName,
               String summary, LocalDateTime submittedAt, Map<String, Object> details) {

        public Row(long requestId, Long definitionId, String stepKey, long requesterEmployeeId, String requesterName,
                   String summary, LocalDateTime submittedAt) {
            this(requestId, definitionId, stepKey, requesterEmployeeId, requesterName, summary, submittedAt, Map.of());
        }
    }
}
