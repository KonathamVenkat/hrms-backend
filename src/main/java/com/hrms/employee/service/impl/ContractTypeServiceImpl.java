package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.ContractTypeRequest;
import com.hrms.employee.dto.response.ContractTypeResponse;
import com.hrms.employee.entity.ContractType;
import com.hrms.employee.repository.ContractTypeRepository;
import com.hrms.employee.service.ContractTypeService;
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
public class ContractTypeServiceImpl implements ContractTypeService {

    private static final String TARGET = "CONTRACT_TYPE";

    private final ContractTypeRepository repository;
    private final AuditTrail audit;
    private final EntityManager entityManager;

    @Override
    public List<ContractTypeResponse> getAll() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }


    @Override
    public ContractTypeResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public ContractTypeResponse create(ContractTypeRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Contract type", "code", code);
        }
        boolean active = request.getIsActive() == null || request.getIsActive();

        ContractType entity = ContractType.builder().build();
        apply(entity, request, code);
        entity.setIsActive(active);

        ContractType saved = repository.save(entity);
        // CREATED_AT / UPDATED_AT come from the column defaults; read them back so the response carries them.
        entityManager.flush();
        entityManager.refresh(saved);
        log.info("Contract type created. ID: {}, code: {}", saved.getId(), saved.getCode());
        audit.record("CONTRACT_TYPE_CREATED", TARGET, saved.getId(), summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ContractTypeResponse update(Long id, ContractTypeRequest request) {
        ContractType existing = findById(id);
        if (request.getVersion() == null) throw new BadRequestException("version is required to update a contract type");
        Versions.requireCurrent(request.getVersion(), existing.getVersion());
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException("Contract type", "code", code);
        }

        String before = summary(existing);
        apply(existing, request, code);
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive());
        existing.setUpdatedAt(LocalDateTime.now());
        ContractType saved = repository.save(existing);
        entityManager.flush(); // so the response carries the incremented version
        audit.record("CONTRACT_TYPE_UPDATED", TARGET, id, before + " -> " + summary(saved));
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
        ContractType e = findById(id);
        repository.delete(e);
        try {
            // Flush now so a row that refers to this one (foreign key) is reported here, not at commit.
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw inUse(e);
        }
        log.info("Contract type deleted. ID: {}, code: {}", id, e.getCode());
        audit.record("CONTRACT_TYPE_DELETED", TARGET, id, summary(e));
    }

    private BusinessRuleException inUse(ContractType e) {
        return new BusinessRuleException("MASTER_IN_USE",
            "Contract type " + e.getCode() + " is in use. Deactivate it instead of deleting it.");
    }

    private void setActive(Long id, boolean active) {
        ContractType e = findById(id);
        e.setIsActive(active);
        e.setUpdatedAt(LocalDateTime.now());
        repository.save(e);
        audit.record(active ? "CONTRACT_TYPE_ACTIVATED" : "CONTRACT_TYPE_DEACTIVATED", TARGET, id, e.getCode());
    }

    private static String summary(ContractType e) {
        return "code=" + e.getCode() + ", name=" + e.getName() + ", nameAr=" + e.getNameAr()
            + ", active=" + e.getIsActive();
    }

    private ContractType findById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Contract type", "id", id));
    }

    private void apply(ContractType e, ContractTypeRequest r, String code) {
        e.setCode(code);
        e.setName(r.getName().trim());
        e.setNameAr(r.getNameAr().trim());
    }

    private ContractTypeResponse toResponse(ContractType e) {
        return ContractTypeResponse.builder()
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
