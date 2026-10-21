package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.WilayaRequest;
import com.hrms.employee.dto.response.WilayaResponse;
import com.hrms.employee.entity.Wilaya;
import com.hrms.employee.repository.WilayaRepository;
import com.hrms.employee.repository.RegionRepository;
import com.hrms.employee.entity.Region;
import com.hrms.employee.repository.ProvinceRepository;
import com.hrms.employee.service.WilayaService;
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
public class WilayaServiceImpl implements WilayaService {

    private static final String TARGET = "WILAYA";

    private final WilayaRepository repository;
    private final RegionRepository regionRepository;
    private final ProvinceRepository provinceRepository;
    private final AuditTrail audit;
    private final EntityManager entityManager;

    @Override
    public List<WilayaResponse> getAll() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }


    @Override
    public WilayaResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public WilayaResponse create(WilayaRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Wilaya", "code", code);
        }
        boolean active = request.getIsActive() == null || request.getIsActive();
        checkParent(request.getRegionId(), active);

        Wilaya entity = Wilaya.builder().build();
        apply(entity, request, code);
        entity.setIsActive(active);

        Wilaya saved = repository.save(entity);
        // CREATED_AT / UPDATED_AT come from the column defaults; read them back so the response carries them.
        entityManager.flush();
        entityManager.refresh(saved);
        log.info("Wilaya created. ID: {}, code: {}", saved.getId(), saved.getCode());
        audit.record("WILAYA_CREATED", TARGET, saved.getId(), summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public WilayaResponse update(Long id, WilayaRequest request) {
        Wilaya existing = findById(id);
        if (request.getVersion() == null) throw new BadRequestException("version is required to update a wilaya");
        Versions.requireCurrent(request.getVersion(), existing.getVersion());
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException("Wilaya", "code", code);
        }
        boolean active = request.getIsActive() != null ? request.getIsActive() : Boolean.TRUE.equals(existing.getIsActive());
        checkParent(request.getRegionId(), active);
        if (!existing.getRegionId().equals(request.getRegionId()) && provinceRepository.existsByWilayaId(id)) {
            throw new BusinessRuleException("MASTER_HAS_CHILDREN",
                "Wilaya " + existing.getCode() + " has provinces below it, so it cannot move to another region.");
        }
        if (Boolean.FALSE.equals(request.getIsActive())) checkNoActiveChildren(existing);

        String before = summary(existing);
        apply(existing, request, code);
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive());
        existing.setUpdatedAt(LocalDateTime.now());
        Wilaya saved = repository.save(existing);
        entityManager.flush(); // so the response carries the incremented version
        audit.record("WILAYA_UPDATED", TARGET, id, before + " -> " + summary(saved));
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
        Wilaya e = findById(id);
        if (provinceRepository.existsByWilayaId(id)) throw inUse(e);
        repository.delete(e);
        try {
            // Flush now so a row that refers to this one (foreign key) is reported here, not at commit.
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw inUse(e);
        }
        log.info("Wilaya deleted. ID: {}, code: {}", id, e.getCode());
        audit.record("WILAYA_DELETED", TARGET, id, summary(e));
    }

    private BusinessRuleException inUse(Wilaya e) {
        return new BusinessRuleException("MASTER_IN_USE",
            "Wilaya " + e.getCode() + " is in use. Deactivate it instead of deleting it.");
    }

    private void setActive(Long id, boolean active) {
        Wilaya e = findById(id);
        if (!active) checkNoActiveChildren(e);
        if (active) checkParent(e.getRegionId(), true);
        e.setIsActive(active);
        e.setUpdatedAt(LocalDateTime.now());
        repository.save(e);
        audit.record(active ? "WILAYA_ACTIVATED" : "WILAYA_DEACTIVATED", TARGET, id, e.getCode());
    }

    private void checkNoActiveChildren(Wilaya e) {
        if (provinceRepository.existsByWilayaIdAndIsActive(e.getId(), true)) {
            throw new BusinessRuleException("MASTER_HAS_ACTIVE_CHILDREN",
                "Wilaya " + e.getCode() + " still has active provinces. Deactivate them first.");
        }
    }

    /** The region must exist, and an active wilaya needs an active region. */
    private void checkParent(Long regionId, boolean childActive) {
        Region parent = regionRepository.findById(regionId)
            .orElseThrow(() -> new ResourceNotFoundException("Region", "id", regionId));
        if (childActive && !Boolean.TRUE.equals(parent.getIsActive())) {
            throw new BusinessRuleException("PARENT_INACTIVE",
                "Region " + parent.getCode() + " is inactive, so it cannot hold an active wilaya.");
        }
    }

    private static String summary(Wilaya e) {
        return "code=" + e.getCode() + ", name=" + e.getName() + ", nameAr=" + e.getNameAr() + ", regionId=" + e.getRegionId()
            + ", active=" + e.getIsActive();
    }

    private Wilaya findById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Wilaya", "id", id));
    }

    private void apply(Wilaya e, WilayaRequest r, String code) {
        e.setRegionId(r.getRegionId());
        e.setCode(code);
        e.setName(r.getName().trim());
        e.setNameAr(r.getNameAr().trim());
    }

    private WilayaResponse toResponse(Wilaya e) {
        return WilayaResponse.builder()
            .id(e.getId())
            .regionId(e.getRegionId())
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
