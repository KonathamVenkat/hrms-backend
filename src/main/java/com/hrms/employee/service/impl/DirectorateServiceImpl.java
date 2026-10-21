package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.DirectorateRequest;
import com.hrms.employee.dto.response.DirectorateResponse;
import com.hrms.employee.entity.Directorate;
import com.hrms.employee.repository.DirectorateRepository;
import com.hrms.employee.repository.CompanyBranchRepository;
import com.hrms.employee.entity.CompanyBranch;
import com.hrms.employee.service.DirectorateService;
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
public class DirectorateServiceImpl implements DirectorateService {

    private static final String TARGET = "DIRECTORATE";

    private final DirectorateRepository repository;
    private final CompanyBranchRepository companyBranchRepository;
    private final AuditTrail audit;
    private final EntityManager entityManager;

    @Override
    public List<DirectorateResponse> getAll() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }


    @Override
    public DirectorateResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public DirectorateResponse create(DirectorateRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Directorate", "code", code);
        }
        boolean active = request.getIsActive() == null || request.getIsActive();
        checkParent(request.getCompanyBranchId(), active);

        Directorate entity = Directorate.builder().build();
        apply(entity, request, code);
        entity.setIsActive(active);

        Directorate saved = repository.save(entity);
        // CREATED_AT / UPDATED_AT come from the column defaults; read them back so the response carries them.
        entityManager.flush();
        entityManager.refresh(saved);
        log.info("Directorate created. ID: {}, code: {}", saved.getId(), saved.getCode());
        audit.record("DIRECTORATE_CREATED", TARGET, saved.getId(), summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public DirectorateResponse update(Long id, DirectorateRequest request) {
        Directorate existing = findById(id);
        if (request.getVersion() == null) throw new BadRequestException("version is required to update a directorate");
        Versions.requireCurrent(request.getVersion(), existing.getVersion());
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException("Directorate", "code", code);
        }
        boolean active = request.getIsActive() != null ? request.getIsActive() : Boolean.TRUE.equals(existing.getIsActive());
        checkParent(request.getCompanyBranchId(), active);

        String before = summary(existing);
        apply(existing, request, code);
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive());
        existing.setUpdatedAt(LocalDateTime.now());
        Directorate saved = repository.save(existing);
        entityManager.flush(); // so the response carries the incremented version
        audit.record("DIRECTORATE_UPDATED", TARGET, id, before + " -> " + summary(saved));
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
        Directorate e = findById(id);
        repository.delete(e);
        try {
            // Flush now so a row that refers to this one (foreign key) is reported here, not at commit.
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw inUse(e);
        }
        log.info("Directorate deleted. ID: {}, code: {}", id, e.getCode());
        audit.record("DIRECTORATE_DELETED", TARGET, id, summary(e));
    }

    private BusinessRuleException inUse(Directorate e) {
        return new BusinessRuleException("MASTER_IN_USE",
            "Directorate " + e.getCode() + " is in use. Deactivate it instead of deleting it.");
    }

    private void setActive(Long id, boolean active) {
        Directorate e = findById(id);
        if (active) checkParent(e.getCompanyBranchId(), true);
        e.setIsActive(active);
        e.setUpdatedAt(LocalDateTime.now());
        repository.save(e);
        audit.record(active ? "DIRECTORATE_ACTIVATED" : "DIRECTORATE_DEACTIVATED", TARGET, id, e.getCode());
    }

    /** The company branch must exist, and an active directorate needs an active company branch. */
    private void checkParent(Long companyBranchId, boolean childActive) {
        CompanyBranch parent = companyBranchRepository.findById(companyBranchId)
            .orElseThrow(() -> new ResourceNotFoundException("Company branch", "id", companyBranchId));
        if (childActive && !Boolean.TRUE.equals(parent.getIsActive())) {
            throw new BusinessRuleException("PARENT_INACTIVE",
                "Company branch " + parent.getCode() + " is inactive, so it cannot hold an active directorate.");
        }
    }

    private static String summary(Directorate e) {
        return "code=" + e.getCode() + ", name=" + e.getName() + ", nameAr=" + e.getNameAr() + ", companyBranchId=" + e.getCompanyBranchId()
            + ", active=" + e.getIsActive();
    }

    private Directorate findById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Directorate", "id", id));
    }

    private void apply(Directorate e, DirectorateRequest r, String code) {
        e.setCompanyBranchId(r.getCompanyBranchId());
        e.setCode(code);
        e.setName(r.getName().trim());
        e.setNameAr(r.getNameAr().trim());
    }

    private DirectorateResponse toResponse(Directorate e) {
        return DirectorateResponse.builder()
            .id(e.getId())
            .companyBranchId(e.getCompanyBranchId())
            .code(e.getCode())
            .name(e.getName())
            .nameAr(e.getNameAr())
            .isActive(Boolean.TRUE.equals(e.getIsActive()))
            .createdAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null)
            .updatedAt(e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : null)
            .version(e.getVersion())
            .build();
    }
}
