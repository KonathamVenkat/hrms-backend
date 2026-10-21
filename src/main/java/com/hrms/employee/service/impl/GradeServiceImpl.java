package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.GradeRequest;
import com.hrms.employee.dto.response.GradeResponse;
import com.hrms.employee.entity.Grade;
import com.hrms.employee.repository.GradeRepository;
import com.hrms.employee.service.GradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GradeServiceImpl implements GradeService {

    private static final String TARGET = "GRADE";

    private final GradeRepository repository;
    private final AuditTrail audit;
    private final EntityManager entityManager;

    @Override
    public List<GradeResponse> getAll() {
        return repository.findAllByOrderBySortOrderAscNameAsc().stream().map(this::toResponse).toList();
    }


    @Override
    public GradeResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public GradeResponse create(GradeRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Grade", "code", code);
        }
        boolean active = request.getIsActive() == null || request.getIsActive();

        Grade entity = Grade.builder().build();
        apply(entity, request, code);
        entity.setIsActive(active);

        Grade saved = repository.save(entity);
        // CREATED_AT / UPDATED_AT come from the column defaults; read them back so the response carries them.
        entityManager.flush();
        entityManager.refresh(saved);
        log.info("Grade created. ID: {}, code: {}", saved.getId(), saved.getCode());
        audit.record("GRADE_CREATED", TARGET, saved.getId(), summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public GradeResponse update(Long id, GradeRequest request) {
        Grade existing = findById(id);
        if (request.getVersion() == null) throw new BadRequestException("version is required to update a grade");
        Versions.requireCurrent(request.getVersion(), existing.getVersion());
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException("Grade", "code", code);
        }

        String before = summary(existing);
        apply(existing, request, code);
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive());
        existing.setUpdatedAt(LocalDateTime.now());
        Grade saved = repository.save(existing);
        entityManager.flush(); // so the response carries the incremented version
        audit.record("GRADE_UPDATED", TARGET, id, before + " -> " + summary(saved));
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
        Grade e = findById(id);
        repository.delete(e);
        try {
            // Flush now so a row that refers to this one (foreign key) is reported here, not at commit.
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw inUse(e);
        }
        log.info("Grade deleted. ID: {}, code: {}", id, e.getCode());
        audit.record("GRADE_DELETED", TARGET, id, summary(e));
    }

    private BusinessRuleException inUse(Grade e) {
        return new BusinessRuleException("MASTER_IN_USE",
            "Grade " + e.getCode() + " is in use. Deactivate it instead of deleting it.");
    }

    private void setActive(Long id, boolean active) {
        Grade e = findById(id);
        e.setIsActive(active);
        e.setUpdatedAt(LocalDateTime.now());
        repository.save(e);
        audit.record(active ? "GRADE_ACTIVATED" : "GRADE_DEACTIVATED", TARGET, id, e.getCode());
    }

    private static String summary(Grade e) {
        return "code=" + e.getCode() + ", name=" + e.getName() + ", nameAr=" + e.getNameAr() + ", sortOrder=" + e.getSortOrder()
            + ", active=" + e.getIsActive();
    }

    private Grade findById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Grade", "id", id));
    }

    private void apply(Grade e, GradeRequest r, String code) {
        e.setCode(code);
        e.setName(r.getName().trim());
        e.setNameAr(r.getNameAr().trim());
        e.setSortOrder(r.getSortOrder());
    }

    private GradeResponse toResponse(Grade e) {
        return GradeResponse.builder()
            .id(e.getId())
            .code(e.getCode())
            .name(e.getName())
            .nameAr(e.getNameAr())
            .sortOrder(e.getSortOrder())
            .isActive(Boolean.TRUE.equals(e.getIsActive()))
            .createdAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null)
            .updatedAt(e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : null)
            .version(e.getVersion())
            .build();
    }
}
