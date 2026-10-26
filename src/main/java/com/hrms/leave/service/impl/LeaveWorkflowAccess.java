package com.hrms.leave.service.impl;

import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.leave.entity.LeaveRequest;
import com.hrms.leave.repository.LeaveRequestRepository;
import com.hrms.workflow.enums.RequestType;
import com.hrms.workflow.service.WorkflowRequestAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import org.springframework.data.domain.PageRequest;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** The workflow's window onto LEAVE_REQUESTS. Writes join the workflow action's transaction. */
@Component
@RequiredArgsConstructor
class LeaveWorkflowAccess implements WorkflowRequestAccess {

    private final LeaveRequestRepository requests;
    private final EmployeeRepository employees;

    @Override
    public RequestType type() {
        return RequestType.LEAVE;
    }

    @Override
    public long requesterEmployeeId(long requestId) {
        return load(requestId).getEmployeeId();
    }

    @Override
    public Long targetDepartmentId(long requestId) {
        return load(requestId).getTargetDepartmentId();
    }

    @Override
    public Brief brief(long requestId) {
        LeaveRequest lr = load(requestId);
        return new Brief(lr.getEmployeeId(),
            LeaveRequestSummary.name(employees.findById(lr.getEmployeeId()).orElse(null)),
            LeaveRequestSummary.text(lr));
    }

    @Override
    public Optional<String> stage(long requestId) {
        return Optional.ofNullable(load(requestId).getStage());
    }

    @Override
    public Optional<String> processInstanceId(long requestId) {
        return Optional.ofNullable(load(requestId).getProcessInstanceId());
    }

    @Override
    public void markEntered(long requestId, Long definitionId, String processInstanceId, Long targetDepartmentId,
                            String stage, String stepKey, String approverType, Long approverEmployeeId,
                            String approverRole) {
        LeaveRequest lr = load(requestId);
        lr.setWorkflowDefinitionId(definitionId);
        lr.setProcessInstanceId(processInstanceId);
        lr.setTargetDepartmentId(targetDepartmentId);
        writeStage(lr, stage, stepKey, approverEmployeeId, approverRole);
    }

    @Override
    public void markStage(long requestId, String stage, String stepKey, String approverType,
                          Long approverEmployeeId, String approverRole) {
        writeStage(load(requestId), stage, stepKey, approverEmployeeId, approverRole);
    }

    @Override
    public void markTerminal(long requestId, String stage) {
        writeStage(load(requestId), stage, null, null, null);
    }

    @Override
    public List<OpenRequest> openRequests(Collection<String> stages, long afterRequestId, int limit) {
        return requests.findOpenAtStages(stages, afterRequestId, PageRequest.of(0, limit)).stream()
            .map(lr -> new OpenRequest(lr.getLeaveReqId(), lr.getEmployeeId(), lr.getTargetDepartmentId(),
                lr.getWorkflowDefinitionId(), lr.getCurrentStepKey(), lr.getStage(),
                lr.getCurrentApproverEmployeeId(), lr.getCurrentApproverRole()))
            .toList();
    }

    private void writeStage(LeaveRequest lr, String stage, String stepKey, Long approverEmployeeId,
                            String approverRole) {
        lr.setStage(stage);
        lr.setCurrentStepKey(stepKey);
        lr.setCurrentApproverEmployeeId(approverEmployeeId);
        lr.setCurrentApproverRole(approverRole);
        requests.save(lr);
    }

    private LeaveRequest load(long requestId) {
        return requests.findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", "id", requestId));
    }
}
