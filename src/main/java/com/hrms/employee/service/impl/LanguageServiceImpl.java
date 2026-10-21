package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.LanguageRequest;
import com.hrms.employee.dto.response.LanguageResponse;
import com.hrms.employee.dto.response.MasterLookupResponse;
import com.hrms.employee.entity.Language;
import com.hrms.employee.repository.LanguageRepository;
import com.hrms.employee.service.LanguageService;
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
public class LanguageServiceImpl implements LanguageService {

    private static final String TARGET = "LANGUAGE";

    private final LanguageRepository repository;
    private final AuditTrail audit;
    private final EntityManager entityManager;

    @Override
    public List<LanguageResponse> getAll() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }


    @Override
    public List<MasterLookupResponse> getActiveLookup() {
        return repository.findByIsActiveOrderByNameAsc(true).stream().map(e -> MasterLookupResponse.builder().id(e.getId()).code(e.getCode()).name(e.getName()).nameAr(e.getNameAr()).build()).toList();
    }

    @Override
    public LanguageResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public LanguageResponse create(LanguageRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Language", "code", code);
        }
        boolean active = request.getIsActive() == null || request.getIsActive();

        Language entity = Language.builder().build();
        apply(entity, request, code);
        entity.setIsActive(active);

        Language saved = repository.save(entity);
        // CREATED_AT / UPDATED_AT come from the column defaults; read them back so the response carries them.
        entityManager.flush();
        entityManager.refresh(saved);
        log.info("Language created. ID: {}, code: {}", saved.getId(), saved.getCode());
        audit.record("LANGUAGE_CREATED", TARGET, saved.getId(), summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public LanguageResponse update(Long id, LanguageRequest request) {
        Language existing = findById(id);
        if (request.getVersion() == null) throw new BadRequestException("version is required to update a language");
        Versions.requireCurrent(request.getVersion(), existing.getVersion());
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException("Language", "code", code);
        }

        String before = summary(existing);
        apply(existing, request, code);
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive());
        existing.setUpdatedAt(LocalDateTime.now());
        Language saved = repository.save(existing);
        entityManager.flush(); // so the response carries the incremented version
        audit.record("LANGUAGE_UPDATED", TARGET, id, before + " -> " + summary(saved));
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
        Language e = findById(id);
        repository.delete(e);
        try {
            // Flush now so a row that refers to this one (foreign key) is reported here, not at commit.
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw inUse(e);
        }
        log.info("Language deleted. ID: {}, code: {}", id, e.getCode());
        audit.record("LANGUAGE_DELETED", TARGET, id, summary(e));
    }

    private BusinessRuleException inUse(Language e) {
        return new BusinessRuleException("MASTER_IN_USE",
            "Language " + e.getCode() + " is in use. Deactivate it instead of deleting it.");
    }

    private void setActive(Long id, boolean active) {
        Language e = findById(id);
        e.setIsActive(active);
        e.setUpdatedAt(LocalDateTime.now());
        repository.save(e);
        audit.record(active ? "LANGUAGE_ACTIVATED" : "LANGUAGE_DEACTIVATED", TARGET, id, e.getCode());
    }

    private static String summary(Language e) {
        return "code=" + e.getCode() + ", name=" + e.getName() + ", nameAr=" + e.getNameAr()
            + ", active=" + e.getIsActive();
    }

    private Language findById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Language", "id", id));
    }

    private void apply(Language e, LanguageRequest r, String code) {
        e.setCode(code);
        e.setName(r.getName().trim());
        e.setNameAr(r.getNameAr().trim());
    }

    private LanguageResponse toResponse(Language e) {
        return LanguageResponse.builder()
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
