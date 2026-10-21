package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.HouseTypeRequest;
import com.hrms.employee.dto.response.HouseTypeResponse;
import com.hrms.employee.entity.HouseType;
import com.hrms.employee.repository.HouseTypeRepository;
import com.hrms.employee.service.HouseTypeService;
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
public class HouseTypeServiceImpl implements HouseTypeService {

    private static final String TARGET = "HOUSE_TYPE";

    private final HouseTypeRepository repository;
    private final AuditTrail audit;
    private final EntityManager entityManager;

    @Override
    public List<HouseTypeResponse> getAll() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }


    @Override
    public HouseTypeResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public HouseTypeResponse create(HouseTypeRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("House type", "code", code);
        }
        boolean active = request.getIsActive() == null || request.getIsActive();

        HouseType entity = HouseType.builder().build();
        apply(entity, request, code);
        entity.setIsActive(active);

        HouseType saved = repository.save(entity);
        // CREATED_AT / UPDATED_AT come from the column defaults; read them back so the response carries them.
        entityManager.flush();
        entityManager.refresh(saved);
        log.info("House type created. ID: {}, code: {}", saved.getId(), saved.getCode());
        audit.record("HOUSE_TYPE_CREATED", TARGET, saved.getId(), summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public HouseTypeResponse update(Long id, HouseTypeRequest request) {
        HouseType existing = findById(id);
        if (request.getVersion() == null) throw new BadRequestException("version is required to update a house type");
        Versions.requireCurrent(request.getVersion(), existing.getVersion());
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException("House type", "code", code);
        }

        String before = summary(existing);
        apply(existing, request, code);
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive());
        existing.setUpdatedAt(LocalDateTime.now());
        HouseType saved = repository.save(existing);
        entityManager.flush(); // so the response carries the incremented version
        audit.record("HOUSE_TYPE_UPDATED", TARGET, id, before + " -> " + summary(saved));
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
        HouseType e = findById(id);
        repository.delete(e);
        try {
            // Flush now so a row that refers to this one (foreign key) is reported here, not at commit.
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw inUse(e);
        }
        log.info("House type deleted. ID: {}, code: {}", id, e.getCode());
        audit.record("HOUSE_TYPE_DELETED", TARGET, id, summary(e));
    }

    private BusinessRuleException inUse(HouseType e) {
        return new BusinessRuleException("MASTER_IN_USE",
            "House type " + e.getCode() + " is in use. Deactivate it instead of deleting it.");
    }

    private void setActive(Long id, boolean active) {
        HouseType e = findById(id);
        e.setIsActive(active);
        e.setUpdatedAt(LocalDateTime.now());
        repository.save(e);
        audit.record(active ? "HOUSE_TYPE_ACTIVATED" : "HOUSE_TYPE_DEACTIVATED", TARGET, id, e.getCode());
    }

    private static String summary(HouseType e) {
        return "code=" + e.getCode() + ", name=" + e.getName() + ", nameAr=" + e.getNameAr()
            + ", active=" + e.getIsActive();
    }

    private HouseType findById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("House type", "id", id));
    }

    private void apply(HouseType e, HouseTypeRequest r, String code) {
        e.setCode(code);
        e.setName(r.getName().trim());
        e.setNameAr(r.getNameAr().trim());
    }

    private HouseTypeResponse toResponse(HouseType e) {
        return HouseTypeResponse.builder()
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
