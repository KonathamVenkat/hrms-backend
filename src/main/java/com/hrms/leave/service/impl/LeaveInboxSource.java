package com.hrms.leave.service.impl;

import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.leave.entity.LeaveRequest;
import com.hrms.leave.repository.LeaveRequestRepository;
import com.hrms.workflow.enums.RequestType;
import com.hrms.workflow.service.InboxSource;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The workflow inbox's window onto LEAVE_REQUESTS: oldest submitted first (fixed sort, no client sort field). */
@Component
@RequiredArgsConstructor
class LeaveInboxSource implements InboxSource {

    private static final Sort OLDEST_FIRST = Sort.by("createdAt", "leaveReqId");

    private final LeaveRequestRepository requests;
    private final EmployeeRepository employees;

    @Override
    public RequestType type() {
        return RequestType.LEAVE;
    }

    @Override
    public Page<Row> waitingFor(long employeeId, Collection<String> roles, Pageable pageable) {
        // an empty IN list is invalid SQL on Oracle
        List<String> roleList = roles.isEmpty() ? List.of("") : List.copyOf(roles);
        Page<LeaveRequest> page = requests.findWaitingFor(employeeId, roleList,
            PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), OLDEST_FIRST));
        Map<Long, Employee> names = employees.findAllById(
            page.stream().map(LeaveRequest::getEmployeeId).distinct().toList()).stream()
            .collect(Collectors.toMap(Employee::getId, Function.identity()));
        return page.map(lr -> {
            Employee e = names.get(lr.getEmployeeId());
            return new Row(lr.getLeaveReqId(), lr.getWorkflowDefinitionId(), lr.getCurrentStepKey(),
                lr.getEmployeeId(), LeaveRequestSummary.name(e),
                LeaveRequestSummary.text(lr), lr.getCreatedAt());
        });
    }
}
