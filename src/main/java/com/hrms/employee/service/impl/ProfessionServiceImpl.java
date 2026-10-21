package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.ProfessionRequest;
import com.hrms.employee.dto.response.ProfessionResponse;
import com.hrms.employee.entity.Profession;
import com.hrms.employee.repository.ProfessionRepository;
import com.hrms.employee.service.ProfessionService;
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
public class ProfessionServiceImpl implements ProfessionService {

    private static final String TARGET = "PROFESSION";

    private final ProfessionRepository repository;
    private final AuditTrail audit;
    private final EntityManager entityManager;

    @Override
    public List<ProfessionResponse> getAll() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }


    @Override
    public ProfessionResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public ProfessionResponse create(ProfessionRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Profession", "code", code);
        }
        boolean active = request.getIsActive() == null || request.getIsActive();

        Profession entity = Profession.builder().build();
        apply(entity, request, code);
        entity.setIsActive(active);

        Profession saved = repository.save(entity);
        // CREATED_AT / UPDATED_AT come from the column defaults; read them back so the response carries them.
        entityManager.flush();
        entityManager.refresh(saved);
        log.info("Profession created. ID: {}, code: {}", saved.getId(), saved.getCode());
        audit.record("PROFESSION_CREATED", TARGET, saved.getId(), summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ProfessionResponse update(Long id, ProfessionRequest request) {
        Profession existing = findById(id);
        if (request.getVersion() == null) throw new BadRequestException("version is required to update a profession");
        Versions.requireCurrent(request.getVersion(), existing.getVersion());
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException("Profession", "code", code);
        }

        String before = summary(existing);
        apply(existing, request, code);
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive());
        existing.setUpdatedAt(LocalDateTime.now());
        Profession saved = repository.save(existing);
        entityManager.flush(); // so the response carries the incremented version
        audit.record("PROFESSION_UPDATED", TARGET, id, before + " -> " + summary(saved));
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
        Profession e = findById(id);
        repository.delete(e);
        try {
            // Flush now so a row that refers to this one (foreign key) is reported here, not at commit.
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw inUse(e);
        }
        log.info("Profession deleted. ID: {}, code: {}", id, e.getCode());
        audit.record("PROFESSION_DELETED", TARGET, id, summary(e));
    }

    private BusinessRuleException inUse(Profession e) {
        return new BusinessRuleException("MASTER_IN_USE",
            "Profession " + e.getCode() + " is in use. Deactivate it instead of deleting it.");
    }

    private void setActive(Long id, boolean active) {
        Profession e = findById(id);
        e.setIsActive(active);
        e.setUpdatedAt(LocalDateTime.now());
        repository.save(e);
        audit.record(active ? "PROFESSION_ACTIVATED" : "PROFESSION_DEACTIVATED", TARGET, id, e.getCode());
    }

    private static String summary(Profession e) {
        return "code=" + e.getCode() + ", name=" + e.getName() + ", nameAr=" + e.getNameAr()
            + ", active=" + e.getIsActive();
    }

    private Profession findById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Profession", "id", id));
    }

    private void apply(Profession e, ProfessionRequest r, String code) {
        e.setCode(code);
        e.setName(r.getName().trim());
        e.setNameAr(r.getNameAr().trim());
    }

    private ProfessionResponse toResponse(Profession e) {
        return ProfessionResponse.builder()
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
