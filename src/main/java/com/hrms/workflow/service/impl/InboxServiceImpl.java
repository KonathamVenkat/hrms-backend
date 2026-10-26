package com.hrms.workflow.service.impl;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.dto.PagedResponse;
import com.hrms.employee.config.PageLimits;
import com.hrms.workflow.dto.response.InboxItemResponse;
import com.hrms.workflow.entity.WorkflowStep;
import com.hrms.workflow.repository.WorkflowStepRepository;
import com.hrms.workflow.service.InboxService;
import com.hrms.workflow.service.InboxSource;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The approvals inbox: one paged query on LEAVE_REQUESTS (through {@link InboxSource}) over the three CURRENT_*
 * columns written at each transition (CURRENT_STEP_KEY, CURRENT_APPROVER_EMPLOYEE_ID, CURRENT_APPROVER_ROLE), served
 * by the V10 indexes on CURRENT_APPROVER_EMPLOYEE_ID and CURRENT_APPROVER_ROLE. The caller (person and roles) comes
 * from the security context, never from a parameter. The stored approver is a snapshot; authorization stays live in
 * {@code WorkflowAuthorization} on every action. Only leave feeds the inbox today: a second request type means
 * merging sources, and a second {@link InboxSource} bean fails at startup rather than being silently ignored.
 */
@Service
@RequiredArgsConstructor
public class InboxServiceImpl implements InboxService {

    private static final String ROLE_PREFIX = "ROLE_";
    private static final long NO_EMPLOYEE = -1L;

    private final InboxSource source;
    private final EmployeeAccessGuard accessGuard;
    private final WorkflowStepRepository steps;

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<InboxItemResponse> inbox(int page, int size) {
        Long caller = accessGuard.currentEmployeeId();
        Page<InboxSource.Row> rows = source.waitingFor(caller == null ? NO_EMPLOYEE : caller, callerRoles(),
            PageLimits.of(page, size));
        Map<Long, Map<String, WorkflowStep>> flows = new HashMap<>();
        return PagedResponse.from(rows.map(r -> {
            WorkflowStep step = r.definitionId() == null ? null : flows.computeIfAbsent(r.definitionId(),
                id -> steps.findByDefinitionIdOrderByPositionNo(id).stream()
                    .collect(Collectors.toMap(WorkflowStep::getStepKey, s -> s))).get(r.stepKey());
            return new InboxItemResponse(source.type(), r.requestId(), r.stepKey(),
                step == null ? r.stepKey() : step.getLabelEn(), step == null ? r.stepKey() : step.getLabelAr(),
                r.requesterEmployeeId(), r.requesterName(), r.summary(), r.submittedAt());
        }));
    }

    private static Set<String> callerRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required.");
        }
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
            .map(a -> a.startsWith(ROLE_PREFIX) ? a.substring(ROLE_PREFIX.length()) : a).collect(Collectors.toSet());
    }
}
