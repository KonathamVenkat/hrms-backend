package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.BankBranchRequest;
import com.hrms.employee.dto.response.BankBranchResponse;
import com.hrms.employee.dto.response.MasterLookupResponse;
import com.hrms.employee.entity.BankBranch;
import com.hrms.employee.repository.BankBranchRepository;
import com.hrms.employee.repository.BankRepository;
import com.hrms.employee.entity.Bank;
import com.hrms.employee.service.BankBranchService;
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
public class BankBranchServiceImpl implements BankBranchService {

    private static final String TARGET = "BANK_BRANCH";

    private final BankBranchRepository repository;
    private final BankRepository bankRepository;
    private final AuditTrail audit;
    private final EntityManager entityManager;

    @Override
    public List<BankBranchResponse> getAll() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }


    @Override
    public List<MasterLookupResponse> getActiveLookupByBankId(Long bankId) {
        return repository.findByBankIdAndIsActiveOrderByNameAsc(bankId, true).stream().map(e -> MasterLookupResponse.builder().id(e.getId()).code(e.getCode()).name(e.getName()).nameAr(e.getNameAr()).build()).toList();
    }

    @Override
    public BankBranchResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public BankBranchResponse create(BankBranchRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Bank branch", "code", code);
        }
        boolean active = request.getIsActive() == null || request.getIsActive();
        checkParent(request.getBankId(), active);

        BankBranch entity = BankBranch.builder().build();
        apply(entity, request, code);
        entity.setIsActive(active);

        BankBranch saved = repository.save(entity);
        // CREATED_AT / UPDATED_AT come from the column defaults; read them back so the response carries them.
        entityManager.flush();
        entityManager.refresh(saved);
        log.info("Bank branch created. ID: {}, code: {}", saved.getId(), saved.getCode());
        audit.record("BANK_BRANCH_CREATED", TARGET, saved.getId(), summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public BankBranchResponse update(Long id, BankBranchRequest request) {
        BankBranch existing = findById(id);
        if (request.getVersion() == null) throw new BadRequestException("version is required to update a bank branch");
        Versions.requireCurrent(request.getVersion(), existing.getVersion());
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException("Bank branch", "code", code);
        }
        boolean active = request.getIsActive() != null ? request.getIsActive() : Boolean.TRUE.equals(existing.getIsActive());
        checkParent(request.getBankId(), active);

        String before = summary(existing);
        apply(existing, request, code);
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive());
        existing.setUpdatedAt(LocalDateTime.now());
        BankBranch saved = repository.save(existing);
        entityManager.flush(); // so the response carries the incremented version
        audit.record("BANK_BRANCH_UPDATED", TARGET, id, before + " -> " + summary(saved));
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
        BankBranch e = findById(id);
        repository.delete(e);
        try {
            // Flush now so a row that refers to this one (foreign key) is reported here, not at commit.
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw inUse(e);
        }
        log.info("Bank branch deleted. ID: {}, code: {}", id, e.getCode());
        audit.record("BANK_BRANCH_DELETED", TARGET, id, summary(e));
    }

    private BusinessRuleException inUse(BankBranch e) {
        return new BusinessRuleException("MASTER_IN_USE",
            "Bank branch " + e.getCode() + " is in use. Deactivate it instead of deleting it.");
    }

    private void setActive(Long id, boolean active) {
        BankBranch e = findById(id);
        if (active) checkParent(e.getBankId(), true);
        e.setIsActive(active);
        e.setUpdatedAt(LocalDateTime.now());
        repository.save(e);
        audit.record(active ? "BANK_BRANCH_ACTIVATED" : "BANK_BRANCH_DEACTIVATED", TARGET, id, e.getCode());
    }

    /** The bank must exist, and an active bank branch needs an active bank. */
    private void checkParent(Long bankId, boolean childActive) {
        Bank parent = bankRepository.findById(bankId)
            .orElseThrow(() -> new ResourceNotFoundException("Bank", "id", bankId));
        if (childActive && !Boolean.TRUE.equals(parent.getIsActive())) {
            throw new BusinessRuleException("PARENT_INACTIVE",
                "Bank " + parent.getCode() + " is inactive, so it cannot hold an active bank branch.");
        }
    }

    private static String summary(BankBranch e) {
        return "code=" + e.getCode() + ", name=" + e.getName() + ", nameAr=" + e.getNameAr() + ", bankId=" + e.getBankId()
            + ", active=" + e.getIsActive();
    }

    private BankBranch findById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Bank branch", "id", id));
    }

    private void apply(BankBranch e, BankBranchRequest r, String code) {
        e.setBankId(r.getBankId());
        e.setCode(code);
        e.setName(r.getName().trim());
        e.setNameAr(r.getNameAr().trim());
    }

    private BankBranchResponse toResponse(BankBranch e) {
        return BankBranchResponse.builder()
            .id(e.getId())
            .bankId(e.getBankId())
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
