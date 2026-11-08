package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.SpecializationRequest;
import com.hrms.employee.dto.response.SpecializationResponse;
import com.hrms.employee.entity.Specialization;
import com.hrms.employee.repository.SpecializationRepository;
import com.hrms.employee.dto.response.MasterLookupResponse;
import com.hrms.employee.service.SpecializationService;
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
public class SpecializationServiceImpl implements SpecializationService {

    private static final String TARGET = "SPECIALIZATION";

    private final SpecializationRepository repository;
    private final AuditTrail audit;
    private final EntityManager entityManager;

    @Override
    public List<MasterLookupResponse> getActiveLookup() {
        return repository.findByIsActiveOrderByNameAsc(true).stream()
            .map(e -> MasterLookupResponse.builder().id(e.getId()).code(e.getCode()).name(e.getName()).nameAr(e.getNameAr()).build())
            .toList();
    }

    @Override
    public List<SpecializationResponse> getAll() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }


    @Override
    public SpecializationResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public SpecializationResponse create(SpecializationRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Specialization", "code", code);
        }
        if (repository.existsByNameIgnoreCase(request.getName().trim())) {
            throw new DuplicateResourceException("Specialization", "name", request.getName().trim());
        }
        boolean active = request.getIsActive() == null || request.getIsActive();

        Specialization entity = Specialization.builder().build();
        apply(entity, request, code);
        entity.setIsActive(active);

        Specialization saved = repository.save(entity);
        // CREATED_AT / UPDATED_AT come from the column defaults; read them back so the response carries them.
        entityManager.flush();
        entityManager.refresh(saved);
        log.info("Specialization created. ID: {}, code: {}", saved.getId(), saved.getCode());
        audit.record("SPECIALIZATION_CREATED", TARGET, saved.getId(), summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public SpecializationResponse update(Long id, SpecializationRequest request) {
        Specialization existing = findById(id);
        if (request.getVersion() == null) throw new BadRequestException("version is required to update a specialization");
        Versions.requireCurrent(request.getVersion(), existing.getVersion());
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException("Specialization", "code", code);
        }
        if (repository.existsByNameIgnoreCaseAndIdNot(request.getName().trim(), id)) {
            throw new DuplicateResourceException("Specialization", "name", request.getName().trim());
        }

        String before = summary(existing);
        apply(existing, request, code);
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive());
        existing.setUpdatedAt(LocalDateTime.now());
        Specialization saved = repository.save(existing);
        entityManager.flush(); // so the response carries the incremented version
        audit.record("SPECIALIZATION_UPDATED", TARGET, id, before + " -> " + summary(saved));
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
        Specialization e = findById(id);
        repository.delete(e);
        try {
            // Flush now so a row that refers to this one (foreign key) is reported here, not at commit.
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw inUse(e);
        }
        log.info("Specialization deleted. ID: {}, code: {}", id, e.getCode());
        audit.record("SPECIALIZATION_DELETED", TARGET, id, summary(e));
    }

    private BusinessRuleException inUse(Specialization e) {
        return new BusinessRuleException("MASTER_IN_USE",
            "Specialization " + e.getCode() + " is in use. Deactivate it instead of deleting it.");
    }

    private void setActive(Long id, boolean active) {
        Specialization e = findById(id);
        e.setIsActive(active);
        e.setUpdatedAt(LocalDateTime.now());
        repository.save(e);
        audit.record(active ? "SPECIALIZATION_ACTIVATED" : "SPECIALIZATION_DEACTIVATED", TARGET, id, e.getCode());
    }

    private static String summary(Specialization e) {
        return "code=" + e.getCode() + ", name=" + e.getName() + ", nameAr=" + e.getNameAr()
            + ", active=" + e.getIsActive();
    }

    private Specialization findById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Specialization", "id", id));
    }

    private void apply(Specialization e, SpecializationRequest r, String code) {
        e.setCode(code);
        e.setName(r.getName().trim());
        e.setNameAr(r.getNameAr().trim());
    }

    private SpecializationResponse toResponse(Specialization e) {
        return SpecializationResponse.builder()
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
