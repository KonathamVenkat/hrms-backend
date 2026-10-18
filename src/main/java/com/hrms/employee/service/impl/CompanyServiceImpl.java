package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.CompanyRequest;
import com.hrms.employee.dto.response.CompanyResponse;
import com.hrms.employee.entity.Company;
import com.hrms.employee.repository.CompanyRepository;
import com.hrms.employee.repository.CompanyBranchRepository;
import com.hrms.employee.service.CompanyService;
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
public class CompanyServiceImpl implements CompanyService {

    private static final String TARGET = "COMPANY";

    private final CompanyRepository repository;
    private final CompanyBranchRepository companyBranchRepository;
    private final AuditTrail audit;
    private final EntityManager entityManager;

    @Override
    public List<CompanyResponse> getAll() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }


    @Override
    public CompanyResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public CompanyResponse create(CompanyRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Company", "code", code);
        }
        boolean active = request.getIsActive() == null || request.getIsActive();

        Company entity = Company.builder().build();
        apply(entity, request, code);
        entity.setIsActive(active);

        Company saved = repository.save(entity);
        // CREATED_AT / UPDATED_AT come from the column defaults; read them back so the response carries them.
        entityManager.flush();
        entityManager.refresh(saved);
        log.info("Company created. ID: {}, code: {}", saved.getId(), saved.getCode());
        audit.record("COMPANY_CREATED", TARGET, saved.getId(), summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public CompanyResponse update(Long id, CompanyRequest request) {
        Company existing = findById(id);
        if (request.getVersion() == null) throw new BadRequestException("version is required to update a company");
        Versions.requireCurrent(request.getVersion(), existing.getVersion());
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException("Company", "code", code);
        }
        if (Boolean.FALSE.equals(request.getIsActive())) checkNoActiveChildren(existing);

        String before = summary(existing);
        apply(existing, request, code);
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive());
        existing.setUpdatedAt(LocalDateTime.now());
        Company saved = repository.save(existing);
        entityManager.flush(); // so the response carries the incremented version
        audit.record("COMPANY_UPDATED", TARGET, id, before + " -> " + summary(saved));
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
        Company e = findById(id);
        if (companyBranchRepository.existsByCompanyId(id)) throw inUse(e);
        repository.delete(e);
        try {
            // Flush now so a row that refers to this one (foreign key) is reported here, not at commit.
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw inUse(e);
        }
        log.info("Company deleted. ID: {}, code: {}", id, e.getCode());
        audit.record("COMPANY_DELETED", TARGET, id, summary(e));
    }

    private BusinessRuleException inUse(Company e) {
        return new BusinessRuleException("MASTER_IN_USE",
            "Company " + e.getCode() + " is in use. Deactivate it instead of deleting it.");
    }

    private void setActive(Long id, boolean active) {
        Company e = findById(id);
        if (!active) checkNoActiveChildren(e);
        e.setIsActive(active);
        e.setUpdatedAt(LocalDateTime.now());
        repository.save(e);
        audit.record(active ? "COMPANY_ACTIVATED" : "COMPANY_DEACTIVATED", TARGET, id, e.getCode());
    }

    private void checkNoActiveChildren(Company e) {
        if (companyBranchRepository.existsByCompanyIdAndIsActive(e.getId(), true)) {
            throw new BusinessRuleException("MASTER_HAS_ACTIVE_CHILDREN",
                "Company " + e.getCode() + " still has active company branches. Deactivate them first.");
        }
    }

    private static String summary(Company e) {
        return "code=" + e.getCode() + ", name=" + e.getName() + ", nameAr=" + e.getNameAr()
            + ", active=" + e.getIsActive();
    }

    private Company findById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Company", "id", id));
    }

    private void apply(Company e, CompanyRequest r, String code) {
        e.setCode(code);
        e.setName(r.getName().trim());
        e.setNameAr(r.getNameAr().trim());
    }

    private CompanyResponse toResponse(Company e) {
        return CompanyResponse.builder()
            .id(e.getId())
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
