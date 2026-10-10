package com.hrms.auth.service.impl;

import com.hrms.auth.dto.response.AuditEventResponse;
import com.hrms.auth.entity.AuditEvent;
import com.hrms.auth.repository.AuditEventRepository;
import com.hrms.auth.service.AuditEventService;
import com.hrms.common.dto.PagedResponse;
import com.hrms.common.exception.BusinessRuleException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditEventServiceImpl implements AuditEventService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditEventRepository events;

    @Override
    public PagedResponse<AuditEventResponse> search(String actor, String action, String targetType, String targetId,
                                                    LocalDate from, LocalDate to, int page, int size) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new BusinessRuleException("INVALID_DATES", "End date cannot be before start date.");
        }

        Specification<AuditEvent> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (hasText(actor))      p.add(cb.equal(cb.lower(root.get("actor")), actor.trim().toLowerCase()));
            if (hasText(action))     p.add(cb.equal(root.get("action"), action.trim().toUpperCase()));
            if (hasText(targetType)) p.add(cb.equal(root.get("targetType"), targetType.trim().toUpperCase()));
            if (hasText(targetId))   p.add(cb.equal(root.get("targetId"), targetId.trim()));
            if (from != null)        p.add(cb.greaterThanOrEqualTo(root.get("eventTime"), from.atStartOfDay()));
            if (to != null)          p.add(cb.lessThan(root.get("eventTime"), to.plusDays(1).atStartOfDay()));
            return cb.and(p.toArray(new Predicate[0]));
        };

        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("eventTime"), Sort.Order.desc("id")));
        return PagedResponse.from(events.findAll(spec, pageable).map(AuditEventServiceImpl::toResponse));
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static AuditEventResponse toResponse(AuditEvent e) {
        return new AuditEventResponse(e.getId(), e.getEventTime(), e.getActor(), e.getAction(),
                e.getTargetType(), e.getTargetId(), e.getDetail());
    }
}
