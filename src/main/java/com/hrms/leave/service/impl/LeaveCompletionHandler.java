package com.hrms.leave.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.audit.CurrentAuditor;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.leave.entity.LeaveRequest;
import com.hrms.leave.entity.LeaveStatus;
import com.hrms.leave.repository.LeaveRequestRepository;
import com.hrms.workflow.enums.RequestType;
import com.hrms.workflow.service.RequestCompletionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** What a finished leave workflow does to the request status and the leave balance (same transaction). */
@Component
@RequiredArgsConstructor
class LeaveCompletionHandler implements RequestCompletionHandler {

    private final LeaveRequestRepository requests;
    private final LeaveBalanceMovements movements;
    private final AuditTrail audit;

    @Override
    public RequestType type() {
        return RequestType.LEAVE;
    }

    @Override
    public void onApproved(long requestId) {
        LeaveRequest lr = load(requestId);
        movements.consume(lr);
        decide(lr, LeaveStatus.APPROVED);
    }

    @Override
    public void onRejected(long requestId, String reason) {
        LeaveRequest lr = load(requestId);
        movements.release(lr);
        lr.setRejectionReason(reason);
        decide(lr, LeaveStatus.REJECTED);
    }

    @Override
    public void onCancelled(long requestId) {
        LeaveRequest lr = load(requestId);
        movements.releaseIfPresent(lr);
        lr.setStatus(LeaveStatus.CANCELLED);
        lr.setUpdatedBy(CurrentAuditor.name());
        lr.setUpdatedAt(LocalDateTime.now());
        requests.save(lr);
    }

    private void decide(LeaveRequest lr, LeaveStatus status) {
        String actor = CurrentAuditor.name();
        lr.setStatus(status);
        lr.setApprovedBy(actor);
        lr.setApprovedAt(LocalDateTime.now());
        lr.setUpdatedBy(actor);
        lr.setUpdatedAt(LocalDateTime.now());
        requests.save(lr);
        audit.record("LEAVE_" + status.name(), "LEAVE_REQUEST", lr.getLeaveReqId(),
            "employee " + lr.getEmployeeId() + ", " + lr.getLeaveTypeCode() + ", " + lr.getTotalDays() + " days");
    }

    private LeaveRequest load(long requestId) {
        return requests.findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", "id", requestId));
    }
}
