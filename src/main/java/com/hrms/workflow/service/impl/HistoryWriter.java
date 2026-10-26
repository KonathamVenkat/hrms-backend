package com.hrms.workflow.service.impl;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.workflow.dto.response.RequestHistoryResponse;
import com.hrms.workflow.entity.RequestHistory;
import com.hrms.workflow.enums.HistoryAction;
import com.hrms.workflow.enums.RequestType;
import com.hrms.workflow.repository.RequestHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

/** Appends REQUEST_HISTORY rows. The actor always comes from the security context, never from the request. */
@Component
@RequiredArgsConstructor
class HistoryWriter {

    private static final String ROLE_PREFIX = "ROLE_";

    private final RequestHistoryRepository repository;
    private final EmployeeAccessGuard accessGuard;

    /** A row by the signed-in caller: their employee id and their role name without the ROLE_ prefix. */
    void byCaller(RequestType type, long requestId, String stepKey, HistoryAction action, String comment,
                  String reasonCode) {
        save(type, requestId, stepKey, action, accessGuard.currentEmployeeId(), callerRole(), comment, reasonCode);
    }

    /** A system row (AUTO_ROUTED): no actor. */
    void bySystem(RequestType type, long requestId, String stepKey, String reasonCode) {
        save(type, requestId, stepKey, HistoryAction.AUTO_ROUTED, null, null, null, reasonCode);
    }

    List<RequestHistoryResponse> list(RequestType type, long requestId) {
        return repository.findTop200ByRequestTypeAndRequestIdOrderByActedAtAscHistoryIdAsc(type, requestId).stream()
            .map(h -> new RequestHistoryResponse(h.getStepKey(), h.getAction(), h.getActorEmployeeId(), null,
                h.getActorRole(), h.getComment(), h.getActedAt()))
            .toList();
    }

    private void save(RequestType type, long requestId, String stepKey, HistoryAction action, Long actor,
                      String role, String comment, String reasonCode) {
        RequestHistory h = new RequestHistory();
        h.setRequestType(type);
        h.setRequestId(requestId);
        h.setStepKey(stepKey);
        h.setAction(action);
        h.setActorEmployeeId(actor);
        h.setActorRole(role);
        h.setComment(comment);
        h.setReasonCode(reasonCode);
        repository.save(h);
    }

    /** Users hold one role; should there be several, the first by name keeps the value deterministic. */
    private static String callerRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
            .filter(a -> a != null && a.startsWith(ROLE_PREFIX)).sorted().findFirst()
            .map(a -> a.substring(ROLE_PREFIX.length())).orElse(null);
    }
}
