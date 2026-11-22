package com.hrms.payroll.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.service.impl.Versions;
import com.hrms.payroll.dto.request.QualificationLevelRequest;
import com.hrms.payroll.dto.response.QualificationLevelResponse;
import com.hrms.payroll.entity.QualificationLevel;
import com.hrms.payroll.repository.QualificationLevelRepository;
import com.hrms.payroll.service.QualificationLevelService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** The qualification level master, maintained like Grade: unique code and name, optimistic lock, audit, in-use check. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QualificationLevelServiceImpl implements QualificationLevelService {

    private static final String TARGET = "QUALIFICATION_LEVEL";

    private final QualificationLevelRepository repository;
    private final AuditTrail audit;
    private final EntityManager entityManager;

    @Override
    public List<QualificationLevelResponse> getAll() {
        return repository.findAllByOrderBySortOrderAscNameAsc().stream().map(QualificationLevelServiceImpl::toResponse).toList();
    }

    @Override
    public List<QualificationLevelResponse> getActive() {
        return repository.findByIsActiveTrueOrderBySortOrderAscNameAsc().stream()
            .map(QualificationLevelServiceImpl::toResponse).toList();
    }

    @Override
    public QualificationLevelResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public QualificationLevelResponse create(QualificationLevelRequest request) {
        String code = request.code().trim().toUpperCase();
        String name = request.name().trim();
        if (repository.existsByCodeIgnoreCase(code)) throw new DuplicateResourceException("Qualification level", "code", code);
        if (repository.existsByNameIgnoreCase(name)) throw new DuplicateResourceException("Qualification level", "name", name);

        QualificationLevel entity = QualificationLevel.builder().build();
        apply(entity, request, code);
        entity.setIsActive(request.isActive() == null || request.isActive());
        QualificationLevel saved = repository.save(entity);
        // CREATED_AT / UPDATED_AT come from the column defaults; read them back so the response carries them.
        entityManager.flush();
        entityManager.refresh(saved);
        log.info("Qualification level created. ID: {}, code: {}", saved.getId(), saved.getCode());
        audit.record("QUALIFICATION_LEVEL_CREATED", TARGET, saved.getId(), summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public QualificationLevelResponse update(Long id, QualificationLevelRequest request) {
        QualificationLevel existing = findById(id);
        if (request.version() == null) throw new BadRequestException("version is required to update a qualification level");
        Versions.requireCurrent(request.version(), existing.getVersion());
        String code = request.code().trim().toUpperCase();
        String name = request.name().trim();
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) throw new DuplicateResourceException("Qualification level", "code", code);
        if (repository.existsByNameIgnoreCaseAndIdNot(name, id)) throw new DuplicateResourceException("Qualification level", "name", name);

        String before = summary(existing);
        apply(existing, request, code);
        if (request.isActive() != null) existing.setIsActive(request.isActive());
        existing.setUpdatedAt(LocalDateTime.now());
        QualificationLevel saved = repository.save(existing);
        entityManager.flush(); // so the response carries the incremented version
        // A new amount applies to salaries assigned from now on; saved salaries keep the amount they were given.
        audit.record("QUALIFICATION_LEVEL_UPDATED", TARGET, id, before + " -> " + summary(saved));
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
        QualificationLevel e = findById(id);
        repository.delete(e);
        try {
            // Flush now so a salary that refers to this level (foreign key) is reported here, not at commit.
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("MASTER_IN_USE",
                "Qualification level " + e.getCode() + " is in use. Deactivate it instead of deleting it.");
        }
        log.info("Qualification level deleted. ID: {}, code: {}", id, e.getCode());
        audit.record("QUALIFICATION_LEVEL_DELETED", TARGET, id, summary(e));
    }

    private void setActive(Long id, boolean active) {
        QualificationLevel e = findById(id);
        e.setIsActive(active);
        e.setUpdatedAt(LocalDateTime.now());
        repository.save(e);
        audit.record(active ? "QUALIFICATION_LEVEL_ACTIVATED" : "QUALIFICATION_LEVEL_DEACTIVATED", TARGET, id, e.getCode());
    }

    private QualificationLevel findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Qualification level", "id", id));
    }

    private static void apply(QualificationLevel e, QualificationLevelRequest r, String code) {
        e.setCode(code);
        e.setName(r.name().trim());
        e.setNameAr(r.nameAr().trim());
        e.setMonthlyAmount(r.monthlyAmount());
        e.setSortOrder(r.sortOrder());
    }

    /** Master data, not a person's pay: the level's amount may be in the audit detail. */
    private static String summary(QualificationLevel e) {
        return "code=" + e.getCode() + ", name=" + e.getName() + ", nameAr=" + e.getNameAr()
            + ", monthlyAmount=" + e.getMonthlyAmount() + ", sortOrder=" + e.getSortOrder() + ", active=" + e.getIsActive();
    }

    static QualificationLevelResponse toResponse(QualificationLevel e) {
        return new QualificationLevelResponse(e.getId(), e.getCode(), e.getName(), e.getNameAr(), e.getMonthlyAmount(),
            e.getSortOrder(), Boolean.TRUE.equals(e.getIsActive()),
            e.getCreatedAt() != null ? e.getCreatedAt().toString() : null,
            e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : null,
            e.getVersion());
    }
}
