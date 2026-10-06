package com.hrms.auth.service;

import com.hrms.auth.entity.AuditEvent;
import com.hrms.auth.repository.AuditEventRepository;
import com.hrms.common.audit.CurrentAuditor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Writes the audit trail. It joins the caller's transaction, so an event exists exactly when the
 * change it describes was committed. Never put a password, token or identity number in {@code detail}.
 */
@Component
@RequiredArgsConstructor
public class AuditTrail {

    private static final int MAX_DETAIL = 1000;

    /** Clips to the column length: a login attempt can name any username, however long. */
    private static String clip(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }

    private final AuditEventRepository events;

    /** Records an action by the signed-in user (SYSTEM when there is none). */
    public void record(String action, String targetType, Object targetId, String detail) {
        recordAs(CurrentAuditor.name(), action, targetType, targetId, detail);
    }

    /** Records an action by a named actor, for when nobody is signed in yet (login). */
    public void recordAs(String actor, String action, String targetType, Object targetId, String detail) {
        events.save(AuditEvent.builder()
                .eventTime(LocalDateTime.now())
                .actor(actor == null || actor.isBlank() ? "SYSTEM" : clip(actor, 100))
                .action(action)
                .targetType(targetType)
                .targetId(targetId == null ? null : clip(String.valueOf(targetId), 64))
                .detail(clip(detail, MAX_DETAIL))
                .build());
    }
}
