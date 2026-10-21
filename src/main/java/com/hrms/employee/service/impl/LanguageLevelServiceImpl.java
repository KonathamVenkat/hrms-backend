package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.LanguageLevelRequest;
import com.hrms.employee.dto.response.LanguageLevelResponse;
import com.hrms.employee.dto.response.MasterLookupResponse;
import com.hrms.employee.entity.LanguageLevel;
import com.hrms.employee.repository.LanguageLevelRepository;
import com.hrms.employee.service.LanguageLevelService;
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
public class LanguageLevelServiceImpl implements LanguageLevelService {

    private static final String TARGET = "LANGUAGE_LEVEL";

    private final LanguageLevelRepository repository;
    private final AuditTrail audit;
    private final EntityManager entityManager;

    @Override
    public List<LanguageLevelResponse> getAll() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }


    @Override
    public List<MasterLookupResponse> getActiveLookup() {
        return repository.findByIsActiveOrderByNameAsc(true).stream().map(e -> MasterLookupResponse.builder().id(e.getId()).code(e.getCode()).name(e.getName()).nameAr(e.getNameAr()).build()).toList();
    }

    @Override
    public LanguageLevelResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public LanguageLevelResponse create(LanguageLevelRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Language level", "code", code);
        }
        boolean active = request.getIsActive() == null || request.getIsActive();

        LanguageLevel entity = LanguageLevel.builder().build();
        apply(entity, request, code);
        entity.setIsActive(active);

        LanguageLevel saved = repository.save(entity);
        // CREATED_AT / UPDATED_AT come from the column defaults; read them back so the response carries them.
        entityManager.flush();
        entityManager.refresh(saved);
        log.info("Language level created. ID: {}, code: {}", saved.getId(), saved.getCode());
        audit.record("LANGUAGE_LEVEL_CREATED", TARGET, saved.getId(), summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public LanguageLevelResponse update(Long id, LanguageLevelRequest request) {
        LanguageLevel existing = findById(id);
        if (request.getVersion() == null) throw new BadRequestException("version is required to update a language level");
        Versions.requireCurrent(request.getVersion(), existing.getVersion());
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException("Language level", "code", code);
        }

        String before = summary(existing);
        apply(existing, request, code);
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive());
        existing.setUpdatedAt(LocalDateTime.now());
        LanguageLevel saved = repository.save(existing);
        entityManager.flush(); // so the response carries the incremented version
        audit.record("LANGUAGE_LEVEL_UPDATED", TARGET, id, before + " -> " + summary(saved));
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
        LanguageLevel e = findById(id);
        repository.delete(e);
        try {
            // Flush now so a row that refers to this one (foreign key) is reported here, not at commit.
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw inUse(e);
        }
        log.info("Language level deleted. ID: {}, code: {}", id, e.getCode());
        audit.record("LANGUAGE_LEVEL_DELETED", TARGET, id, summary(e));
    }

    private BusinessRuleException inUse(LanguageLevel e) {
        return new BusinessRuleException("MASTER_IN_USE",
            "Language level " + e.getCode() + " is in use. Deactivate it instead of deleting it.");
    }

    private void setActive(Long id, boolean active) {
        LanguageLevel e = findById(id);
        e.setIsActive(active);
        e.setUpdatedAt(LocalDateTime.now());
        repository.save(e);
        audit.record(active ? "LANGUAGE_LEVEL_ACTIVATED" : "LANGUAGE_LEVEL_DEACTIVATED", TARGET, id, e.getCode());
    }

    private static String summary(LanguageLevel e) {
        return "code=" + e.getCode() + ", name=" + e.getName() + ", nameAr=" + e.getNameAr()
            + ", active=" + e.getIsActive();
    }

    private LanguageLevel findById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Language level", "id", id));
    }

    private void apply(LanguageLevel e, LanguageLevelRequest r, String code) {
        e.setCode(code);
        e.setName(r.getName().trim());
        e.setNameAr(r.getNameAr().trim());
    }

    private LanguageLevelResponse toResponse(LanguageLevel e) {
        return LanguageLevelResponse.builder()
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
