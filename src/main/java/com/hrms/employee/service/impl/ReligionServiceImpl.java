package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.ReligionRequest;
import com.hrms.employee.dto.response.ReligionResponse;
import com.hrms.employee.entity.Religion;
import com.hrms.employee.repository.ReligionRepository;
import com.hrms.employee.service.ReligionService;
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
public class ReligionServiceImpl implements ReligionService {

    private static final String TARGET = "RELIGION";

    private final ReligionRepository repository;
    private final AuditTrail audit;
    private final EntityManager entityManager;

    @Override
    public List<ReligionResponse> getAll() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }


    @Override
    public ReligionResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public ReligionResponse create(ReligionRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Religion", "code", code);
        }
        boolean active = request.getIsActive() == null || request.getIsActive();

        Religion entity = Religion.builder().build();
        apply(entity, request, code);
        entity.setIsActive(active);

        Religion saved = repository.save(entity);
        // CREATED_AT / UPDATED_AT come from the column defaults; read them back so the response carries them.
        entityManager.flush();
        entityManager.refresh(saved);
        log.info("Religion created. ID: {}, code: {}", saved.getId(), saved.getCode());
        audit.record("RELIGION_CREATED", TARGET, saved.getId(), summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ReligionResponse update(Long id, ReligionRequest request) {
        Religion existing = findById(id);
        if (request.getVersion() == null) throw new BadRequestException("version is required to update a religion");
        Versions.requireCurrent(request.getVersion(), existing.getVersion());
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException("Religion", "code", code);
        }

        String before = summary(existing);
        apply(existing, request, code);
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive());
        existing.setUpdatedAt(LocalDateTime.now());
        Religion saved = repository.save(existing);
        entityManager.flush(); // so the response carries the incremented version
        audit.record("RELIGION_UPDATED", TARGET, id, before + " -> " + summary(saved));
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
        Religion e = findById(id);
        repository.delete(e);
        try {
            // Flush now so a row that refers to this one (foreign key) is reported here, not at commit.
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw inUse(e);
        }
        log.info("Religion deleted. ID: {}, code: {}", id, e.getCode());
        audit.record("RELIGION_DELETED", TARGET, id, summary(e));
    }

    private BusinessRuleException inUse(Religion e) {
        return new BusinessRuleException("MASTER_IN_USE",
            "Religion " + e.getCode() + " is in use. Deactivate it instead of deleting it.");
    }

    private void setActive(Long id, boolean active) {
        Religion e = findById(id);
        e.setIsActive(active);
        e.setUpdatedAt(LocalDateTime.now());
        repository.save(e);
        audit.record(active ? "RELIGION_ACTIVATED" : "RELIGION_DEACTIVATED", TARGET, id, e.getCode());
    }

    private static String summary(Religion e) {
        return "code=" + e.getCode() + ", name=" + e.getName() + ", nameAr=" + e.getNameAr()
            + ", active=" + e.getIsActive();
    }

    private Religion findById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Religion", "id", id));
    }

    private void apply(Religion e, ReligionRequest r, String code) {
        e.setCode(code);
        e.setName(r.getName().trim());
        e.setNameAr(r.getNameAr().trim());
    }

    private ReligionResponse toResponse(Religion e) {
        return ReligionResponse.builder()
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
