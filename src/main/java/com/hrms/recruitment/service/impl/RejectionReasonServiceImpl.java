package com.hrms.recruitment.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.recruitment.dto.request.RejectionReasonRequest;
import com.hrms.recruitment.dto.response.RecruitmentLookupResponse;
import com.hrms.recruitment.dto.response.RejectionReasonResponse;
import com.hrms.recruitment.entity.RejectionReason;
import com.hrms.recruitment.enums.ReasonScope;
import com.hrms.recruitment.repository.RejectionReasonRepository;
import com.hrms.recruitment.service.RejectionReasonService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RejectionReasonServiceImpl implements RejectionReasonService {

    private static final String TARGET = "REJECTION_REASON";

    private final RejectionReasonRepository repository;
    private final AuditTrail audit;
    private final EntityManager entityManager;

    @Override
    public List<RejectionReasonResponse> getAll() {
        return repository.findAllByOrderBySortOrderAscNameAsc().stream().map(this::toResponse).toList();
    }

    @Override
    public List<RecruitmentLookupResponse> getActiveLookup(ReasonScope appliesTo) {
        return repository.findByIsActiveAndAppliesToOrderBySortOrderAscNameAsc(true, appliesTo).stream()
            .map(r -> RecruitmentLookupResponse.builder()
                .id(r.getId()).code(r.getCode()).name(r.getName()).nameAr(r.getNameAr())
                .kind(r.getAppliesTo().name()).build())
            .toList();
    }

    @Override
    public RejectionReasonResponse getById(Long id) {
        return toResponse(find(id));
    }

    @Override
    @Transactional
    public RejectionReasonResponse create(RejectionReasonRequest request) {
        String code = request.code().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Rejection reason", "code", code);
        }
        RejectionReason entity = RejectionReason.builder().build();
        apply(entity, request, code);
        entity.setIsActive(request.isActive() == null || request.isActive());

        RejectionReason saved = repository.save(entity);
        entityManager.flush();
        entityManager.refresh(saved); // CREATED_AT / UPDATED_AT come from the column defaults
        log.info("Rejection reason created. ID: {}, code: {}", saved.getId(), saved.getCode());
        audit.record("REJECTION_REASON_CREATED", TARGET, saved.getId(), summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public RejectionReasonResponse update(Long id, RejectionReasonRequest request) {
        RejectionReason existing = find(id);
        if (request.version() == null) throw new BadRequestException("version is required to update a rejection reason");
        if (!Objects.equals(request.version(), existing.getVersion())) {
            throw new OptimisticLockingFailureException("Version " + request.version() + " is stale, current is " + existing.getVersion());
        }
        String code = request.code().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException("Rejection reason", "code", code);
        }
        String before = summary(existing);
        apply(existing, request, code);
        if (request.isActive() != null) existing.setIsActive(request.isActive());
        existing.setUpdatedAt(LocalDateTime.now());
        RejectionReason saved = repository.save(existing);
        entityManager.flush(); // so the response carries the incremented version
        audit.record("REJECTION_REASON_UPDATED", TARGET, id, before + " -> " + summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        setActive(id, false);
    }

    @Override
    @Transactional
    public void activate(Long id) {
        setActive(id, true);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        RejectionReason r = find(id);
        repository.delete(r);
        try {
            // Flush now so an application or offer that uses this reason (foreign key) is reported here, not at commit.
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("MASTER_IN_USE",
                "Rejection reason " + r.getCode() + " is in use. Deactivate it instead of deleting it.");
        }
        log.info("Rejection reason deleted. ID: {}, code: {}", id, r.getCode());
        audit.record("REJECTION_REASON_DELETED", TARGET, id, summary(r));
    }

    private void setActive(Long id, boolean active) {
        RejectionReason r = find(id);
        r.setIsActive(active);
        r.setUpdatedAt(LocalDateTime.now());
        repository.save(r);
        audit.record(active ? "REJECTION_REASON_ACTIVATED" : "REJECTION_REASON_DEACTIVATED", TARGET, id, r.getCode());
    }

    private RejectionReason find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Rejection reason", "id", id));
    }

    private static void apply(RejectionReason r, RejectionReasonRequest in, String code) {
        r.setCode(code);
        r.setName(in.name().trim());
        r.setNameAr(in.nameAr().trim());
        r.setAppliesTo(in.appliesTo());
        r.setSortOrder(in.sortOrder());
    }

    private static String summary(RejectionReason r) {
        return "code=" + r.getCode() + ", name=" + r.getName() + ", appliesTo=" + r.getAppliesTo()
            + ", sort=" + r.getSortOrder() + ", active=" + r.getIsActive();
    }

    private RejectionReasonResponse toResponse(RejectionReason r) {
        return RejectionReasonResponse.builder()
            .id(r.getId()).code(r.getCode()).name(r.getName()).nameAr(r.getNameAr())
            .appliesTo(r.getAppliesTo()).sortOrder(r.getSortOrder()).isActive(Boolean.TRUE.equals(r.getIsActive()))
            .createdAt(r.getCreatedAt()).updatedAt(r.getUpdatedAt()).version(r.getVersion())
            .build();
    }
}
