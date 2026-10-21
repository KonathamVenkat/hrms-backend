package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.dto.PagedResponse;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.CityRequest;
import com.hrms.employee.dto.response.CityResponse;
import com.hrms.employee.entity.City;
import com.hrms.employee.repository.CityRepository;
import com.hrms.employee.repository.ProvinceRepository;
import com.hrms.employee.entity.Province;
import com.hrms.employee.service.CityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CityServiceImpl implements CityService {

    private static final String TARGET = "CITY";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_SEARCH_LENGTH = 100;

    private final CityRepository repository;
    private final ProvinceRepository provinceRepository;
    private final AuditTrail audit;
    private final EntityManager entityManager;

    @Override
    public List<CityResponse> getAll() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Override
    public PagedResponse<CityResponse> search(String q, Long provinceId, Boolean active, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Page must be 0 or more and size between 1 and " + MAX_PAGE_SIZE);
        }
        Specification<City> spec = Specification.unrestricted();
        String text = q == null ? "" : q.trim();
        if (text.length() > MAX_SEARCH_LENGTH) {
            throw new BadRequestException("Search text must be at most " + MAX_SEARCH_LENGTH + " characters");
        }
        if (!text.isEmpty()) {
            String like = "%" + text.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), like, '\\'),
                cb.like(cb.lower(root.get("code")), like, '\\'),
                cb.like(cb.lower(root.get("nameAr")), like, '\\')));
        }
        if (provinceId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("provinceId"), provinceId));
        }
        if (active != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("isActive"), active));
        }
        var result = repository.findAll(spec, PageRequest.of(page, size, Sort.by("name", "id")));
        return PagedResponse.from(result.map(this::toResponse));
    }

    @Override
    public CityResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public CityResponse create(CityRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("City", "code", code);
        }
        boolean active = request.getIsActive() == null || request.getIsActive();
        checkParent(request.getProvinceId(), active);

        City entity = City.builder().build();
        apply(entity, request, code);
        entity.setIsActive(active);

        City saved = repository.save(entity);
        // CREATED_AT / UPDATED_AT come from the column defaults; read them back so the response carries them.
        entityManager.flush();
        entityManager.refresh(saved);
        log.info("City created. ID: {}, code: {}", saved.getId(), saved.getCode());
        audit.record("CITY_CREATED", TARGET, saved.getId(), summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public CityResponse update(Long id, CityRequest request) {
        City existing = findById(id);
        if (request.getVersion() == null) throw new BadRequestException("version is required to update a city");
        Versions.requireCurrent(request.getVersion(), existing.getVersion());
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException("City", "code", code);
        }
        boolean active = request.getIsActive() != null ? request.getIsActive() : Boolean.TRUE.equals(existing.getIsActive());
        checkParent(request.getProvinceId(), active);

        String before = summary(existing);
        apply(existing, request, code);
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive());
        existing.setUpdatedAt(LocalDateTime.now());
        City saved = repository.save(existing);
        entityManager.flush(); // so the response carries the incremented version
        audit.record("CITY_UPDATED", TARGET, id, before + " -> " + summary(saved));
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
        City e = findById(id);
        repository.delete(e);
        try {
            // Flush now so a row that refers to this one (foreign key) is reported here, not at commit.
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw inUse(e);
        }
        log.info("City deleted. ID: {}, code: {}", id, e.getCode());
        audit.record("CITY_DELETED", TARGET, id, summary(e));
    }

    private BusinessRuleException inUse(City e) {
        return new BusinessRuleException("MASTER_IN_USE",
            "City " + e.getCode() + " is in use. Deactivate it instead of deleting it.");
    }

    private void setActive(Long id, boolean active) {
        City e = findById(id);
        if (active) checkParent(e.getProvinceId(), true);
        e.setIsActive(active);
        e.setUpdatedAt(LocalDateTime.now());
        repository.save(e);
        audit.record(active ? "CITY_ACTIVATED" : "CITY_DEACTIVATED", TARGET, id, e.getCode());
    }

    /** The province must exist, and an active city needs an active province. */
    private void checkParent(Long provinceId, boolean childActive) {
        Province parent = provinceRepository.findById(provinceId)
            .orElseThrow(() -> new ResourceNotFoundException("Province", "id", provinceId));
        if (childActive && !Boolean.TRUE.equals(parent.getIsActive())) {
            throw new BusinessRuleException("PARENT_INACTIVE",
                "Province " + parent.getCode() + " is inactive, so it cannot hold an active city.");
        }
    }

    private static String summary(City e) {
        return "code=" + e.getCode() + ", name=" + e.getName() + ", nameAr=" + e.getNameAr() + ", provinceId=" + e.getProvinceId()
            + ", active=" + e.getIsActive();
    }

    private City findById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("City", "id", id));
    }

    private void apply(City e, CityRequest r, String code) {
        e.setProvinceId(r.getProvinceId());
        e.setCode(code);
        e.setName(r.getName().trim());
        e.setNameAr(r.getNameAr().trim());
    }

    private CityResponse toResponse(City e) {
        return CityResponse.builder()
            .id(e.getId())
            .provinceId(e.getProvinceId())
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
