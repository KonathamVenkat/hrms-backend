package com.hrms.workflow.service.impl;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.dto.PagedResponse;
import com.hrms.employee.config.PageLimits;
import com.hrms.workflow.dto.response.InboxItemResponse;
import com.hrms.workflow.entity.WorkflowStep;
import com.hrms.workflow.repository.WorkflowStepRepository;
import com.hrms.workflow.service.InboxService;
import com.hrms.workflow.service.InboxSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The approvals inbox: every request type's waiting requests for the caller, oldest first, in one paged list. Each
 * request module answers through its {@link InboxSource} (one paged query over its CURRENT_* columns, served by the
 * approver indexes), so the workflow module never reads request tables. The caller (person and roles) comes from the
 * security context, never from a parameter. The stored approver is a snapshot; authorization stays live in
 * {@code WorkflowAuthorization} on every action.
 * <p>
 * With one source the page is that source's page. With several, page {@code p} of size {@code s} reads the first
 * {@code (p + 1) * s} rows of each source, merges them by submission time (then type and id, so the order is stable)
 * and cuts the page out; the total is the sum of the sources' totals.
 */
@Service
public class InboxServiceImpl implements InboxService {

    private static final String ROLE_PREFIX = "ROLE_";
    private static final long NO_EMPLOYEE = -1L;
    /** A merged page reads (page + 1) * size rows per source: deep pages of a huge inbox are capped here. */
    static final int MAX_MERGE_ROWS = 1000;

    private static final Comparator<Tagged> OLDEST_FIRST = Comparator
        .comparing((Tagged t) -> t.row().submittedAt(), Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(t -> t.source().type().name())
        .thenComparingLong(t -> t.row().requestId());

    private final List<InboxSource> sources;
    private final EmployeeAccessGuard accessGuard;
    private final WorkflowStepRepository steps;

    public InboxServiceImpl(List<InboxSource> sources, EmployeeAccessGuard accessGuard, WorkflowStepRepository steps) {
        if (sources.isEmpty()) {
            throw new IllegalStateException("No InboxSource: at least one request type must feed the inbox.");
        }
        long types = sources.stream().map(InboxSource::type).distinct().count();
        if (types != sources.size()) {
            throw new IllegalStateException("Two InboxSource beans serve the same request type.");
        }
        this.sources = List.copyOf(sources);
        this.accessGuard = accessGuard;
        this.steps = steps;
    }

    private record Tagged(InboxSource source, InboxSource.Row row) {
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<InboxItemResponse> inbox(int page, int size) {
        Long caller = accessGuard.currentEmployeeId();
        long me = caller == null ? NO_EMPLOYEE : caller;
        Set<String> roles = callerRoles();
        Pageable pageable = PageLimits.of(page, size);
        Page<Tagged> rows = sources.size() == 1
            ? single(sources.get(0), me, roles, pageable)
            : merged(me, roles, pageable);
        Map<Long, Map<String, WorkflowStep>> flows = new HashMap<>();
        return PagedResponse.from(rows.map(t -> {
            InboxSource.Row r = t.row();
            WorkflowStep step = r.definitionId() == null ? null : flows.computeIfAbsent(r.definitionId(),
                id -> steps.findByDefinitionIdOrderByPositionNo(id).stream()
                    .collect(Collectors.toMap(WorkflowStep::getStepKey, s -> s))).get(r.stepKey());
            return new InboxItemResponse(t.source().type(), r.requestId(), r.stepKey(),
                step == null ? r.stepKey() : step.getLabelEn(), step == null ? r.stepKey() : step.getLabelAr(),
                r.requesterEmployeeId(), r.requesterName(), r.summary(), r.submittedAt(), r.details());
        }));
    }

    private static Page<Tagged> single(InboxSource source, long me, Set<String> roles, Pageable pageable) {
        return source.waitingFor(me, roles, pageable).map(r -> new Tagged(source, r));
    }

    private Page<Tagged> merged(long me, Set<String> roles, Pageable pageable) {
        long offset = pageable.getOffset();
        // ponytail: reads up to (page + 1) * size rows per source; a keyset cursor per source if inboxes grow huge
        int head = (int) Math.min(offset + pageable.getPageSize(), MAX_MERGE_ROWS);
        List<Tagged> all = new ArrayList<>();
        long total = 0;
        for (InboxSource source : sources) {
            Page<InboxSource.Row> first = source.waitingFor(me, roles, PageRequest.of(0, head));
            total += first.getTotalElements();
            first.forEach(r -> all.add(new Tagged(source, r)));
        }
        all.sort(OLDEST_FIRST);
        List<Tagged> pageRows = offset >= all.size() ? List.of()
            : all.subList((int) offset, (int) Math.min(offset + pageable.getPageSize(), all.size()));
        return new PageImpl<>(pageRows, pageable, total);
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
