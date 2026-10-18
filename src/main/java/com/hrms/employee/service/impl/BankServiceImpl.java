package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.BankRequest;
import com.hrms.employee.dto.response.BankResponse;
import com.hrms.employee.dto.response.MasterLookupResponse;
import com.hrms.employee.entity.Bank;
import com.hrms.employee.repository.BankRepository;
import com.hrms.employee.repository.BankBranchRepository;
import com.hrms.employee.service.BankService;
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
public class BankServiceImpl implements BankService {

    private static final String TARGET = "BANK";

    private final BankRepository repository;
    private final BankBranchRepository bankBranchRepository;
    private final AuditTrail audit;
    private final EntityManager entityManager;

    @Override
    public List<BankResponse> getAll() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }


    @Override
    public List<MasterLookupResponse> getActiveLookup() {
        return repository.findByIsActiveOrderByNameAsc(true).stream().map(e -> MasterLookupResponse.builder().id(e.getId()).code(e.getCode()).name(e.getName()).nameAr(e.getNameAr()).build()).toList();
    }

    @Override
    public BankResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public BankResponse create(BankRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Bank", "code", code);
        }
        boolean active = request.getIsActive() == null || request.getIsActive();

        Bank entity = Bank.builder().build();
        apply(entity, request, code);
        entity.setIsActive(active);

        Bank saved = repository.save(entity);
        // CREATED_AT / UPDATED_AT come from the column defaults; read them back so the response carries them.
        entityManager.flush();
        entityManager.refresh(saved);
        log.info("Bank created. ID: {}, code: {}", saved.getId(), saved.getCode());
        audit.record("BANK_CREATED", TARGET, saved.getId(), summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public BankResponse update(Long id, BankRequest request) {
        Bank existing = findById(id);
        if (request.getVersion() == null) throw new BadRequestException("version is required to update a bank");
        Versions.requireCurrent(request.getVersion(), existing.getVersion());
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException("Bank", "code", code);
        }
        if (Boolean.FALSE.equals(request.getIsActive())) checkNoActiveChildren(existing);

        String before = summary(existing);
        apply(existing, request, code);
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive());
        existing.setUpdatedAt(LocalDateTime.now());
        Bank saved = repository.save(existing);
        entityManager.flush(); // so the response carries the incremented version
        audit.record("BANK_UPDATED", TARGET, id, before + " -> " + summary(saved));
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
        Bank e = findById(id);
        if (bankBranchRepository.existsByBankId(id)) throw inUse(e);
        repository.delete(e);
        try {
            // Flush now so a row that refers to this one (foreign key) is reported here, not at commit.
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw inUse(e);
        }
        log.info("Bank deleted. ID: {}, code: {}", id, e.getCode());
        audit.record("BANK_DELETED", TARGET, id, summary(e));
    }

    private BusinessRuleException inUse(Bank e) {
        return new BusinessRuleException("MASTER_IN_USE",
            "Bank " + e.getCode() + " is in use. Deactivate it instead of deleting it.");
    }

    private void setActive(Long id, boolean active) {
        Bank e = findById(id);
        if (!active) checkNoActiveChildren(e);
        e.setIsActive(active);
        e.setUpdatedAt(LocalDateTime.now());
        repository.save(e);
        audit.record(active ? "BANK_ACTIVATED" : "BANK_DEACTIVATED", TARGET, id, e.getCode());
    }

    private void checkNoActiveChildren(Bank e) {
        if (bankBranchRepository.existsByBankIdAndIsActive(e.getId(), true)) {
            throw new BusinessRuleException("MASTER_HAS_ACTIVE_CHILDREN",
                "Bank " + e.getCode() + " still has active bank branches. Deactivate them first.");
        }
    }

    private static String summary(Bank e) {
        return "code=" + e.getCode() + ", name=" + e.getName() + ", nameAr=" + e.getNameAr()
            + ", active=" + e.getIsActive();
    }

    private Bank findById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Bank", "id", id));
    }

    private void apply(Bank e, BankRequest r, String code) {
        e.setCode(code);
        e.setName(r.getName().trim());
        e.setNameAr(r.getNameAr().trim());
    }

    private BankResponse toResponse(Bank e) {
        return BankResponse.builder()
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
