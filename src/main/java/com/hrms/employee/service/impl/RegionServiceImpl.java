package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.RegionRequest;
import com.hrms.employee.dto.response.RegionResponse;
import com.hrms.employee.entity.Region;
import com.hrms.employee.repository.RegionRepository;
import com.hrms.employee.repository.WilayaRepository;
import com.hrms.employee.service.RegionService;
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
public class RegionServiceImpl implements RegionService {

    private static final String TARGET = "REGION";

    private final RegionRepository repository;
    private final WilayaRepository wilayaRepository;
    private final AuditTrail audit;
    private final EntityManager entityManager;

    @Override
    public List<RegionResponse> getAll() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }


    @Override
    public RegionResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public RegionResponse create(RegionRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Region", "code", code);
        }
        boolean active = request.getIsActive() == null || request.getIsActive();

        Region entity = Region.builder().build();
        apply(entity, request, code);
        entity.setIsActive(active);

        Region saved = repository.save(entity);
        // CREATED_AT / UPDATED_AT come from the column defaults; read them back so the response carries them.
        entityManager.flush();
        entityManager.refresh(saved);
        log.info("Region created. ID: {}, code: {}", saved.getId(), saved.getCode());
        audit.record("REGION_CREATED", TARGET, saved.getId(), summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public RegionResponse update(Long id, RegionRequest request) {
        Region existing = findById(id);
        if (request.getVersion() == null) throw new BadRequestException("version is required to update a region");
        Versions.requireCurrent(request.getVersion(), existing.getVersion());
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException("Region", "code", code);
        }
        if (Boolean.FALSE.equals(request.getIsActive())) checkNoActiveChildren(existing);

        String before = summary(existing);
        apply(existing, request, code);
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive());
        existing.setUpdatedAt(LocalDateTime.now());
        Region saved = repository.save(existing);
        entityManager.flush(); // so the response carries the incremented version
        audit.record("REGION_UPDATED", TARGET, id, before + " -> " + summary(saved));
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
        Region e = findById(id);
        if (wilayaRepository.existsByRegionId(id)) throw inUse(e);
        repository.delete(e);
        try {
            // Flush now so a row that refers to this one (foreign key) is reported here, not at commit.
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw inUse(e);
        }
        log.info("Region deleted. ID: {}, code: {}", id, e.getCode());
        audit.record("REGION_DELETED", TARGET, id, summary(e));
    }

    private BusinessRuleException inUse(Region e) {
        return new BusinessRuleException("MASTER_IN_USE",
            "Region " + e.getCode() + " is in use. Deactivate it instead of deleting it.");
    }

    private void setActive(Long id, boolean active) {
        Region e = findById(id);
        if (!active) checkNoActiveChildren(e);
        e.setIsActive(active);
        e.setUpdatedAt(LocalDateTime.now());
        repository.save(e);
        audit.record(active ? "REGION_ACTIVATED" : "REGION_DEACTIVATED", TARGET, id, e.getCode());
    }

    private void checkNoActiveChildren(Region e) {
        if (wilayaRepository.existsByRegionIdAndIsActive(e.getId(), true)) {
            throw new BusinessRuleException("MASTER_HAS_ACTIVE_CHILDREN",
                "Region " + e.getCode() + " still has active wilayas. Deactivate them first.");
        }
    }

    private static String summary(Region e) {
        return "code=" + e.getCode() + ", name=" + e.getName() + ", nameAr=" + e.getNameAr()
            + ", active=" + e.getIsActive();
    }

    private Region findById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Region", "id", id));
    }

    private void apply(Region e, RegionRequest r, String code) {
        e.setCode(code);
        e.setName(r.getName().trim());
        e.setNameAr(r.getNameAr().trim());
    }

    private RegionResponse toResponse(Region e) {
        return RegionResponse.builder()
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
