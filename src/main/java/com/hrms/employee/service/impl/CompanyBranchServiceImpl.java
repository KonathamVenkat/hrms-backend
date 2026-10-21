package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.CompanyBranchRequest;
import com.hrms.employee.dto.response.CompanyBranchResponse;
import com.hrms.employee.entity.CompanyBranch;
import com.hrms.employee.repository.CompanyBranchRepository;
import com.hrms.employee.repository.DirectorateRepository;
import com.hrms.employee.repository.CompanyRepository;
import com.hrms.employee.entity.Company;
import com.hrms.employee.service.CompanyBranchService;
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
public class CompanyBranchServiceImpl implements CompanyBranchService {

    private static final String TARGET = "COMPANY_BRANCH";

    private final CompanyBranchRepository repository;
    private final CompanyRepository companyRepository;
    private final DirectorateRepository directorateRepository;
    private final AuditTrail audit;
    private final EntityManager entityManager;

    @Override
    public List<CompanyBranchResponse> getAll() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }


    @Override
    public CompanyBranchResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public CompanyBranchResponse create(CompanyBranchRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Company branch", "code", code);
        }
        boolean active = request.getIsActive() == null || request.getIsActive();
        checkParent(request.getCompanyId(), active);

        CompanyBranch entity = CompanyBranch.builder().build();
        apply(entity, request, code);
        entity.setIsActive(active);

        CompanyBranch saved = repository.save(entity);
        // CREATED_AT / UPDATED_AT come from the column defaults; read them back so the response carries them.
        entityManager.flush();
        entityManager.refresh(saved);
        log.info("Company branch created. ID: {}, code: {}", saved.getId(), saved.getCode());
        audit.record("COMPANY_BRANCH_CREATED", TARGET, saved.getId(), summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public CompanyBranchResponse update(Long id, CompanyBranchRequest request) {
        CompanyBranch existing = findById(id);
        if (request.getVersion() == null) throw new BadRequestException("version is required to update a company branch");
        Versions.requireCurrent(request.getVersion(), existing.getVersion());
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException("Company branch", "code", code);
        }
        boolean active = request.getIsActive() != null ? request.getIsActive() : Boolean.TRUE.equals(existing.getIsActive());
        checkParent(request.getCompanyId(), active);
        if (Boolean.FALSE.equals(request.getIsActive())) checkNoActiveChildren(existing);

        String before = summary(existing);
        apply(existing, request, code);
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive());
        existing.setUpdatedAt(LocalDateTime.now());
        CompanyBranch saved = repository.save(existing);
        entityManager.flush(); // so the response carries the incremented version
        audit.record("COMPANY_BRANCH_UPDATED", TARGET, id, before + " -> " + summary(saved));
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
        CompanyBranch e = findById(id);
        repository.delete(e);
        try {
            // Flush now so a row that refers to this one (foreign key) is reported here, not at commit.
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw inUse(e);
        }
        log.info("Company branch deleted. ID: {}, code: {}", id, e.getCode());
        audit.record("COMPANY_BRANCH_DELETED", TARGET, id, summary(e));
    }

    private BusinessRuleException inUse(CompanyBranch e) {
        return new BusinessRuleException("MASTER_IN_USE",
            "Company branch " + e.getCode() + " is in use. Deactivate it instead of deleting it.");
    }

    private void setActive(Long id, boolean active) {
        CompanyBranch e = findById(id);
        if (active) checkParent(e.getCompanyId(), true);
        else checkNoActiveChildren(e);
        e.setIsActive(active);
        e.setUpdatedAt(LocalDateTime.now());
        repository.save(e);
        audit.record(active ? "COMPANY_BRANCH_ACTIVATED" : "COMPANY_BRANCH_DEACTIVATED", TARGET, id, e.getCode());
    }

    /** A branch cannot be switched off while it still has active directorates. */
    private void checkNoActiveChildren(CompanyBranch e) {
        if (directorateRepository.existsByCompanyBranchIdAndIsActive(e.getId(), true)) {
            throw new BusinessRuleException("MASTER_HAS_ACTIVE_CHILDREN",
                "Company branch " + e.getCode() + " still has active directorates. Deactivate them first.");
        }
    }

    /** The company must exist, and an active company branch needs an active company. */
    private void checkParent(Long companyId, boolean childActive) {
        Company parent = companyRepository.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId));
        if (childActive && !Boolean.TRUE.equals(parent.getIsActive())) {
            throw new BusinessRuleException("PARENT_INACTIVE",
                "Company " + parent.getCode() + " is inactive, so it cannot hold an active company branch.");
        }
    }

    private static String summary(CompanyBranch e) {
        return "code=" + e.getCode() + ", name=" + e.getName() + ", nameAr=" + e.getNameAr() + ", companyId=" + e.getCompanyId()
            + ", active=" + e.getIsActive();
    }

    private CompanyBranch findById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Company branch", "id", id));
    }

    private void apply(CompanyBranch e, CompanyBranchRequest r, String code) {
        e.setCompanyId(r.getCompanyId());
        e.setCode(code);
        e.setName(r.getName().trim());
        e.setNameAr(r.getNameAr().trim());
    }

    private CompanyBranchResponse toResponse(CompanyBranch e) {
        return CompanyBranchResponse.builder()
            .id(e.getId())
            .companyId(e.getCompanyId())
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
