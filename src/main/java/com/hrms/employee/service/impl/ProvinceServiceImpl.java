package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.ProvinceRequest;
import com.hrms.employee.dto.response.ProvinceResponse;
import com.hrms.employee.entity.Province;
import com.hrms.employee.repository.ProvinceRepository;
import com.hrms.employee.repository.WilayaRepository;
import com.hrms.employee.entity.Wilaya;
import com.hrms.employee.repository.CityRepository;
import com.hrms.employee.service.ProvinceService;
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
public class ProvinceServiceImpl implements ProvinceService {

    private static final String TARGET = "PROVINCE";

    private final ProvinceRepository repository;
    private final WilayaRepository wilayaRepository;
    private final CityRepository cityRepository;
    private final AuditTrail audit;
    private final EntityManager entityManager;

    @Override
    public List<ProvinceResponse> getAll() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }


    @Override
    public ProvinceResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public ProvinceResponse create(ProvinceRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Province", "code", code);
        }
        boolean active = request.getIsActive() == null || request.getIsActive();
        checkParent(request.getWilayaId(), active);

        Province entity = Province.builder().build();
        apply(entity, request, code);
        entity.setIsActive(active);

        Province saved = repository.save(entity);
        // CREATED_AT / UPDATED_AT come from the column defaults; read them back so the response carries them.
        entityManager.flush();
        entityManager.refresh(saved);
        log.info("Province created. ID: {}, code: {}", saved.getId(), saved.getCode());
        audit.record("PROVINCE_CREATED", TARGET, saved.getId(), summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ProvinceResponse update(Long id, ProvinceRequest request) {
        Province existing = findById(id);
        if (request.getVersion() == null) throw new BadRequestException("version is required to update a province");
        Versions.requireCurrent(request.getVersion(), existing.getVersion());
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException("Province", "code", code);
        }
        boolean active = request.getIsActive() != null ? request.getIsActive() : Boolean.TRUE.equals(existing.getIsActive());
        checkParent(request.getWilayaId(), active);
        if (!existing.getWilayaId().equals(request.getWilayaId()) && cityRepository.existsByProvinceId(id)) {
            throw new BusinessRuleException("MASTER_HAS_CHILDREN",
                "Province " + existing.getCode() + " has cities below it, so it cannot move to another wilaya.");
        }
        if (Boolean.FALSE.equals(request.getIsActive())) checkNoActiveChildren(existing);

        String before = summary(existing);
        apply(existing, request, code);
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive());
        existing.setUpdatedAt(LocalDateTime.now());
        Province saved = repository.save(existing);
        entityManager.flush(); // so the response carries the incremented version
        audit.record("PROVINCE_UPDATED", TARGET, id, before + " -> " + summary(saved));
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
        Province e = findById(id);
        if (cityRepository.existsByProvinceId(id)) throw inUse(e);
        repository.delete(e);
        try {
            // Flush now so a row that refers to this one (foreign key) is reported here, not at commit.
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw inUse(e);
        }
        log.info("Province deleted. ID: {}, code: {}", id, e.getCode());
        audit.record("PROVINCE_DELETED", TARGET, id, summary(e));
    }

    private BusinessRuleException inUse(Province e) {
        return new BusinessRuleException("MASTER_IN_USE",
            "Province " + e.getCode() + " is in use. Deactivate it instead of deleting it.");
    }

    private void setActive(Long id, boolean active) {
        Province e = findById(id);
        if (!active) checkNoActiveChildren(e);
        if (active) checkParent(e.getWilayaId(), true);
        e.setIsActive(active);
        e.setUpdatedAt(LocalDateTime.now());
        repository.save(e);
        audit.record(active ? "PROVINCE_ACTIVATED" : "PROVINCE_DEACTIVATED", TARGET, id, e.getCode());
    }

    private void checkNoActiveChildren(Province e) {
        if (cityRepository.existsByProvinceIdAndIsActive(e.getId(), true)) {
            throw new BusinessRuleException("MASTER_HAS_ACTIVE_CHILDREN",
                "Province " + e.getCode() + " still has active cities. Deactivate them first.");
        }
    }

    /** The wilaya must exist, and an active province needs an active wilaya. */
    private void checkParent(Long wilayaId, boolean childActive) {
        Wilaya parent = wilayaRepository.findById(wilayaId)
            .orElseThrow(() -> new ResourceNotFoundException("Wilaya", "id", wilayaId));
        if (childActive && !Boolean.TRUE.equals(parent.getIsActive())) {
            throw new BusinessRuleException("PARENT_INACTIVE",
                "Wilaya " + parent.getCode() + " is inactive, so it cannot hold an active province.");
        }
    }

    private static String summary(Province e) {
        return "code=" + e.getCode() + ", name=" + e.getName() + ", nameAr=" + e.getNameAr() + ", wilayaId=" + e.getWilayaId()
            + ", active=" + e.getIsActive();
    }

    private Province findById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Province", "id", id));
    }

    private void apply(Province e, ProvinceRequest r, String code) {
        e.setWilayaId(r.getWilayaId());
        e.setCode(code);
        e.setName(r.getName().trim());
        e.setNameAr(r.getNameAr().trim());
    }

    private ProvinceResponse toResponse(Province e) {
        return ProvinceResponse.builder()
            .id(e.getId())
            .wilayaId(e.getWilayaId())
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
